package club.muimi.kimusic.service.playback;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import org.jflac.sound.spi.Flac2PcmAudioInputStream;
import java.nio.file.Path;
import java.util.Map;

final class JavaSoundPlaybackEngine implements PlaybackEngine {
    private final Object pauseLock = new Object();
    private volatile boolean closed;
    private volatile boolean paused;
    private volatile double volume;
    private volatile double requestedSeek = -1;
    private volatile SourceDataLine line;
    private volatile AudioInputStream stream;

    @Override
    public void open(Path track, PlaybackListener listener, double initialVolume, boolean autoPlay) {
        volume = initialVolume;
        paused = !autoPlay;
        Thread.ofPlatform().daemon().name("kimusic-audio-decoder").start(() -> decode(track, listener));
    }

    @Override
    public void play() {
        paused = false;
        SourceDataLine activeLine = line;
        if (activeLine != null) {
            activeLine.start();
        }
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    @Override
    public void pause() {
        paused = true;
        SourceDataLine activeLine = line;
        if (activeLine != null) {
            activeLine.stop();
        }
    }

    @Override
    public void seek(double seconds) {
        requestedSeek = Math.max(0, seconds);
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    @Override
    public void setVolume(double value) {
        volume = Math.max(0, Math.min(1, value));
    }

    @Override
    public void close() {
        closed = true;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        closeCurrentResources();
    }

    private void decode(Path track, PlaybackListener listener) {
        double startSeconds = 0;
        try {
            double duration = readDuration(track);
            listener.onReady(duration);
            listener.onPlayingChanged(!paused);
            while (!closed) {
                requestedSeek = -1;
                boolean reachedEnd = playFrom(track, listener, startSeconds);
                if (closed) {
                    return;
                }
                if (requestedSeek >= 0) {
                    startSeconds = Math.min(duration, requestedSeek);
                    continue;
                }
                if (reachedEnd) {
                    listener.onEnd();
                }
                return;
            }
        } catch (Throwable error) {
            if (!closed) {
                listener.onError(error);
            }
        } finally {
            closeCurrentResources();
        }
    }

    private boolean playFrom(Path track, PlaybackListener listener, double startSeconds) throws Exception {
        try (AudioInputStream encoded = AudioSystem.getAudioInputStream(track.toFile())) {
            AudioFormat sourceFormat = encoded.getFormat();
            AudioFormat decoderFormat = selectDecoderFormat(sourceFormat);
            try (AudioInputStream decoded = createDecodedStream(encoded, sourceFormat, decoderFormat)) {
                boolean downmix = requiresDownmix(decoderFormat);
                AudioFormat outputFormat = selectOutputFormat(decoderFormat, downmix);
                try (AudioInputStream playable = !downmix && outputFormat.matches(decoderFormat)
                        ? decoded : downmix ? decoded : AudioSystem.getAudioInputStream(outputFormat, decoded)) {
                    stream = playable;
                    skipTo(playable, downmix ? decoderFormat : outputFormat, startSeconds);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, outputFormat);
                    SourceDataLine output = (SourceDataLine) AudioSystem.getLine(info);
                    line = output;
                    output.open(outputFormat, 16_384);
                    if (!paused) {
                        output.start();
                    }
                    byte[] buffer = new byte[downmix ? 8_192 + decoderFormat.getFrameSize() : 8_192];
                    byte[] outputBuffer = downmix ? new byte[8_192] : buffer;
                    long frames = 0;
                    while (!closed && requestedSeek < 0) {
                        awaitIfPaused(listener);
                        if (closed || requestedSeek >= 0) {
                            break;
                        }
                        int read = playable.read(buffer, 0, buffer.length);
                        if (read < 0) {
                            output.drain();
                            return true;
                        }
                        int outputBytes = downmix
                                ? downmixPcm16(buffer, read, decoderFormat.getChannels(), outputBuffer)
                                : read;
                        VisualizationFrame visualization = analyzeAndApplyVolume(
                                outputBuffer, outputBytes, outputFormat.getSampleRate() / 2.0);
                        output.write(outputBuffer, 0, outputBytes);
                        frames += outputBytes / outputFormat.getFrameSize();
                        listener.onProgress(startSeconds + frames / outputFormat.getFrameRate(), visualization);
                    }
                    output.flush();
                    return false;
                }
            }
        } finally {
            closeCurrentResources();
        }
    }

    private AudioFormat selectDecoderFormat(AudioFormat sourceFormat) {
        AudioFormat[] targets = AudioSystem.getTargetFormats(AudioFormat.Encoding.PCM_SIGNED, sourceFormat);
        if (targets.length == 0) {
            if ("FLAC".equalsIgnoreCase(sourceFormat.getEncoding().toString())
                    && sourceFormat.getChannels() > 2
                    && sourceFormat.getSampleSizeInBits() > 0) {
                int bytesPerSample = (sourceFormat.getSampleSizeInBits() + 7) / 8;
                return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        sourceFormat.getSampleRate(), sourceFormat.getSampleSizeInBits(),
                        sourceFormat.getChannels(), sourceFormat.getChannels() * bytesPerSample,
                        sourceFormat.getSampleRate(), false);
            }
            throw new IllegalArgumentException("No PCM decoder for " + sourceFormat);
        }
        for (AudioFormat target : targets) {
            if (target.getSampleRate() == sourceFormat.getSampleRate()
                    && target.getChannels() == sourceFormat.getChannels()) {
                return target;
            }
        }
        return targets[0];
    }

    private AudioInputStream createDecodedStream(AudioInputStream encoded,
                                                 AudioFormat sourceFormat,
                                                 AudioFormat decoderFormat) {
        if ("FLAC".equalsIgnoreCase(sourceFormat.getEncoding().toString())
                && sourceFormat.getChannels() > 2) {
            return new Flac2PcmAudioInputStream(encoded, decoderFormat, -1);
        }
        return AudioSystem.getAudioInputStream(decoderFormat, encoded);
    }

    private AudioFormat selectOutputFormat(AudioFormat decoderFormat, boolean downmix) {
        int outputChannels = downmix ? 2 : decoderFormat.getChannels();
        float[] sampleRates = {
                Math.min(decoderFormat.getSampleRate(), 48_000),
                decoderFormat.getSampleRate(),
                48_000,
                44_100,
                96_000
        };
        for (float sampleRate : sampleRates) {
            AudioFormat candidate = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate, 16, outputChannels,
                    outputChannels * 2, sampleRate, false);
            DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, candidate);
            if ((downmix || AudioSystem.isConversionSupported(candidate, decoderFormat))
                    && AudioSystem.isLineSupported(lineInfo)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("No audio output line for " + decoderFormat);
    }

    private boolean requiresDownmix(AudioFormat decoderFormat) {
        return decoderFormat.getChannels() > 2
                && decoderFormat.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                && decoderFormat.getSampleSizeInBits() == 16
                && decoderFormat.isBigEndian() == false;
    }

    private int downmixPcm16(byte[] input, int length, int channels, byte[] output) {
        int frameSize = channels * 2;
        int frames = length / frameSize;
        for (int frame = 0; frame < frames; frame++) {
            int inputOffset = frame * frameSize;
            double left = 0;
            double right = 0;
            int leftCount = 0;
            int rightCount = 0;
            for (int channel = 0; channel < channels; channel++) {
                int offset = inputOffset + channel * 2;
                int sample = (short) ((input[offset] & 0xff) | (input[offset + 1] << 8));
                if ((channel & 1) == 0) {
                    left += sample;
                    leftCount++;
                } else {
                    right += sample;
                    rightCount++;
                }
            }
            int mixedLeft = (int) Math.max(Short.MIN_VALUE,
                    Math.min(Short.MAX_VALUE, left / Math.max(1, leftCount)));
            int mixedRight = (int) Math.max(Short.MIN_VALUE,
                    Math.min(Short.MAX_VALUE, right / Math.max(1, rightCount)));
            int outputOffset = frame * 4;
            output[outputOffset] = (byte) mixedLeft;
            output[outputOffset + 1] = (byte) (mixedLeft >>> 8);
            output[outputOffset + 2] = (byte) mixedRight;
            output[outputOffset + 3] = (byte) (mixedRight >>> 8);
        }
        return frames * 4;
    }

    private void awaitIfPaused(PlaybackListener listener) throws InterruptedException {
        if (!paused) {
            return;
        }
        listener.onPlayingChanged(false);
        synchronized (pauseLock) {
            while (paused && !closed && requestedSeek < 0) {
                pauseLock.wait();
            }
        }
        if (!closed && requestedSeek < 0) {
            listener.onPlayingChanged(true);
        }
    }

    private double readDuration(Path track) {
        try {
            AudioFileFormat format = AudioSystem.getAudioFileFormat(track.toFile());
            Map<String, Object> properties = format.properties();
            Object duration = properties.get("duration");
            if (duration instanceof Number microseconds) {
                return microseconds.doubleValue() / 1_000_000;
            }
            AudioFormat audioFormat = format.getFormat();
            if (format.getFrameLength() > 0 && audioFormat.getFrameRate() > 0) {
                return format.getFrameLength() / audioFormat.getFrameRate();
            }
        } catch (Exception ignored) {
            // Some decoders expose duration only after opening the stream.
        }
        return 0;
    }

    private void skipTo(AudioInputStream decoded, AudioFormat format, double seconds) throws Exception {
        long bytes = (long) (seconds * format.getFrameRate() * format.getFrameSize());
        long skipped = 0;
        while (skipped < bytes) {
            long amount = decoded.skip(bytes - skipped);
            if (amount <= 0) {
                break;
            }
            skipped += amount;
        }
    }

    private VisualizationFrame analyzeAndApplyVolume(byte[] buffer, int length, double maxFrequencyHz) {
        float[] waveform = new float[64];
        int sampleCount = Math.max(1, length / 2);
        int samplesPerBand = Math.max(1, sampleCount / waveform.length);
        double gain = volume;
        for (int sample = 0; sample < sampleCount; sample++) {
            int offset = sample * 2;
            int value = (short) ((buffer[offset] & 0xff) | (buffer[offset + 1] << 8));
            int scaled = (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value * gain));
            buffer[offset] = (byte) (scaled & 0xff);
            buffer[offset + 1] = (byte) ((scaled >>> 8) & 0xff);
            int band = Math.min(waveform.length - 1, sample / samplesPerBand);
            waveform[band] = Math.max(waveform[band], Math.abs(scaled) / 32768f);
        }
        return new VisualizationFrame(waveform, spectrumOf(buffer, length), maxFrequencyHz);
    }

    private float[] spectrumOf(byte[] buffer, int length) {
        int availableSamples = length / 2;
        int fftSize = Integer.highestOneBit(Math.min(1_024, availableSamples));
        if (fftSize < 2) {
            return new float[64];
        }
        double[] real = new double[fftSize];
        double[] imaginary = new double[fftSize];
        double windowSum = 0;
        for (int index = 0; index < fftSize; index++) {
            int offset = index * 2;
            short sample = (short) ((buffer[offset] & 0xff) | (buffer[offset + 1] << 8));
            double window = 0.5 - 0.5 * Math.cos(2 * Math.PI * index / (fftSize - 1));
            real[index] = sample / 32768.0 * window;
            windowSum += window;
        }
        fft(real, imaginary);

        // Return calibrated linear amplitudes. WaveformView performs the only
        // logarithmic frequency and dB projection.
        int usableBins = fftSize / 2;
        float[] spectrum = new float[usableBins];
        for (int bin = 1; bin < usableBins; bin++) {
            double magnitude = Math.hypot(real[bin], imaginary[bin]);
            double amplitude = windowSum > 0 ? magnitude * 2.0 / windowSum : 0;
            spectrum[bin] = (float) Math.max(0, Math.min(20, amplitude));
        }
        return spectrum;
    }

    private void fft(double[] real, double[] imaginary) {
        int size = real.length;
        for (int index = 1, reversed = 0; index < size; index++) {
            int bit = size >> 1;
            while ((reversed & bit) != 0) {
                reversed ^= bit;
                bit >>= 1;
            }
            reversed ^= bit;
            if (index < reversed) {
                double swap = real[index];
                real[index] = real[reversed];
                real[reversed] = swap;
            }
        }
        for (int length = 2; length <= size; length <<= 1) {
            double angle = -2 * Math.PI / length;
            double baseReal = Math.cos(angle);
            double baseImaginary = Math.sin(angle);
            for (int offset = 0; offset < size; offset += length) {
                double factorReal = 1;
                double factorImaginary = 0;
                for (int index = 0; index < length / 2; index++) {
                    int even = offset + index;
                    int odd = even + length / 2;
                    double oddReal = real[odd] * factorReal - imaginary[odd] * factorImaginary;
                    double oddImaginary = real[odd] * factorImaginary + imaginary[odd] * factorReal;
                    real[odd] = real[even] - oddReal;
                    imaginary[odd] = imaginary[even] - oddImaginary;
                    real[even] += oddReal;
                    imaginary[even] += oddImaginary;
                    double nextFactorReal = factorReal * baseReal - factorImaginary * baseImaginary;
                    factorImaginary = factorReal * baseImaginary + factorImaginary * baseReal;
                    factorReal = nextFactorReal;
                }
            }
        }
    }

    private void closeCurrentResources() {
        SourceDataLine activeLine = line;
        line = null;
        if (activeLine != null) {
            activeLine.stop();
            activeLine.close();
        }
        AudioInputStream activeStream = stream;
        stream = null;
        if (activeStream != null) {
            try {
                activeStream.close();
            } catch (Exception ignored) {
                // Closing is best effort during a track switch.
            }
        }
    }
}
