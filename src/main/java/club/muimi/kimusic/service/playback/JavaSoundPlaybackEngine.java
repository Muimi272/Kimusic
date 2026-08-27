package club.muimi.kimusic.service.playback;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import org.jflac.sound.spi.Flac2PcmAudioInputStream;
import org.jflac.FLACDecoder;
import org.jflac.frame.Frame;
import org.jflac.io.RandomFileInputStream;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

final class JavaSoundPlaybackEngine implements PlaybackEngine {
    private static final double ACTIVE_SPECTRUM_RELATIVE_FLOOR = 0.003;
    private final Object pauseLock = new Object();
    private volatile boolean closed;
    private volatile boolean paused;
    private volatile double volume;
    private volatile double requestedSeek = -1;
    private volatile SourceDataLine line;
    private volatile AudioInputStream stream;
    private volatile Closeable randomInput;

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
        synchronized (pauseLock) {
            requestedSeek = Math.max(0, seconds);
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
            while (!closed) {
                double pending = takeRequestedSeek();
                if (pending >= 0) {
                    startSeconds = duration > 0 ? Math.min(duration, pending) : pending;
                }
                boolean reachedEnd = isFlac(track)
                        ? playFlac(track, listener, startSeconds)
                        : playFrom(track, listener, startSeconds);
                if (closed) {
                    return;
                }
                pending = takeRequestedSeek();
                if (pending >= 0) {
                    startSeconds = duration > 0 ? Math.min(duration, pending) : pending;
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

    private boolean playFlac(Path track, PlaybackListener listener, double startSeconds) throws Exception {
        try (RandomFileInputStream input = new RandomFileInputStream(track.toFile())) {
            randomInput = input;
            FLACDecoder decoder = new FLACDecoder(input);
            decoder.readMetadata();
            StreamInfo info = decoder.getStreamInfo();
            if (info == null || info.getSampleRate() <= 0 || info.getChannels() <= 0) {
                throw new IllegalArgumentException("FLAC stream metadata is incomplete");
            }
            int outputChannels = Math.min(2, info.getChannels());
            AudioFormat outputFormat = selectDirectOutputFormat(info.getSampleRate(), outputChannels);
            SourceDataLine output = (SourceDataLine) AudioSystem.getLine(
                    new DataLine.Info(SourceDataLine.class, outputFormat));
            line = output;
            output.open(outputFormat, 16_384);
            if (!paused) {
                output.start();
            }
            listener.onPlayingChanged(!paused);

            long totalSamples = info.getTotalSamples();
            long currentSample = 0;
            if (startSeconds > 0 && totalSamples > 0) {
                long desired = Math.min(totalSamples - 1,
                        Math.max(0, Math.round(startSeconds * info.getSampleRate())));
                currentSample = decoder.seek(desired);
            }
            ByteData pcm = null;
            byte[] converted = new byte[Math.max(16_384,
                    info.getMaxBlockSize() * outputFormat.getFrameSize())];
            while (!closed) {
                awaitIfPaused(listener);
                if (closed) {
                    return false;
                }
                double pending = takeRequestedSeek();
                if (pending >= 0) {
                    output.flush();
                    long desired = totalSamples > 0
                            ? Math.min(totalSamples - 1,
                            Math.max(0, Math.round(pending * info.getSampleRate())))
                            : Math.max(0, Math.round(pending * info.getSampleRate()));
                    currentSample = decoder.seek(desired);
                    continue;
                }
                Frame frame = decoder.readNextFrame();
                if (frame == null) {
                    output.drain();
                    return true;
                }
                pcm = decoder.decodeFrame(frame, pcm);
                int outputBytes = convertFlacToPcm16(pcm.getData(), pcm.getLen(),
                        info.getBitsPerSample(), info.getChannels(), outputChannels, converted);
                VisualizationFrame visualization = analyzeAndApplyVolume(
                        converted, outputBytes, outputChannels, info.getSampleRate() / 2.0);
                output.write(converted, 0, outputBytes);
                currentSample += outputBytes / outputFormat.getFrameSize();
                listener.onProgress(currentSample / (double) info.getSampleRate(), visualization);
            }
            return false;
        } finally {
            randomInput = null;
            closeCurrentResources();
        }
    }

    private AudioFormat selectDirectOutputFormat(float sampleRate, int channels) {
        float[] rates = {sampleRate, Math.min(sampleRate, 48_000), 48_000, 44_100, 96_000};
        for (float rate : rates) {
            if (rate != sampleRate) {
                continue;
            }
            AudioFormat candidate = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    rate, 16, channels, channels * 2, rate, false);
            if (AudioSystem.isLineSupported(new DataLine.Info(SourceDataLine.class, candidate))) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("No direct audio output line for FLAC at " + sampleRate + " Hz");
    }

    int convertFlacToPcm16(byte[] input, int length, int bitsPerSample,
                           int sourceChannels, int outputChannels, byte[] output) {
        int bytesPerSample = Math.max(1, (bitsPerSample + 7) / 8);
        int frameSize = bytesPerSample * sourceChannels;
        int frames = length / frameSize;
        int required = frames * outputChannels * 2;
        if (required > output.length) {
            throw new IllegalArgumentException("FLAC frame exceeds the playback buffer");
        }
        for (int frame = 0; frame < frames; frame++) {
            long left = 0;
            long right = 0;
            int leftCount = 0;
            int rightCount = 0;
            for (int channel = 0; channel < sourceChannels; channel++) {
                int sample = readFlacSample(input, frame * frameSize + channel * bytesPerSample,
                        bitsPerSample);
                if (outputChannels == 1 || (channel & 1) == 0) {
                    left += sample;
                    leftCount++;
                } else {
                    right += sample;
                    rightCount++;
                }
            }
            int mixedLeft = (int) (left / Math.max(1, leftCount));
            int offset = frame * outputChannels * 2;
            writePcm16(output, offset, mixedLeft);
            if (outputChannels == 2) {
                int mixedRight = rightCount == 0 ? mixedLeft : (int) (right / rightCount);
                writePcm16(output, offset + 2, mixedRight);
            }
        }
        return required;
    }

    private int readFlacSample(byte[] data, int offset, int bitsPerSample) {
        return switch (bitsPerSample) {
            case 8 -> ((data[offset] & 0xff) - 128) << 8;
            case 16 -> (short) ((data[offset] & 0xff) | (data[offset + 1] << 8));
            case 24 -> {
                int value = (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8)
                        | (data[offset + 2] << 16);
                yield value >> 8;
            }
            default -> throw new IllegalArgumentException("Unsupported FLAC bit depth: " + bitsPerSample);
        };
    }

    private void writePcm16(byte[] output, int offset, int value) {
        int bounded = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
        output[offset] = (byte) bounded;
        output[offset + 1] = (byte) (bounded >>> 8);
    }

    double takeRequestedSeek() {
        synchronized (pauseLock) {
            double pending = requestedSeek;
            requestedSeek = -1;
            return pending;
        }
    }

    private void restoreRequestedSeek(double seconds) {
        synchronized (pauseLock) {
            if (requestedSeek < 0) {
                requestedSeek = seconds;
            }
            pauseLock.notifyAll();
        }
    }

    private boolean isFlac(Path track) {
        return track.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".flac");
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
                    listener.onPlayingChanged(!paused);
                    byte[] buffer = new byte[downmix ? 8_192 + decoderFormat.getFrameSize() : 8_192];
                    byte[] outputBuffer = downmix ? new byte[8_192] : buffer;
                    long frames = 0;
                    while (!closed) {
                        awaitIfPaused(listener);
                        if (closed) {
                            break;
                        }
                        double pending = takeRequestedSeek();
                        if (pending >= 0) {
                            double current = startSeconds + frames / outputFormat.getFrameRate();
                            if (pending >= current) {
                                output.flush();
                                skipTo(playable, downmix ? decoderFormat : outputFormat,
                                        pending - current);
                                startSeconds = pending;
                                frames = 0;
                                listener.onProgress(pending, VisualizationFrame.empty());
                                continue;
                            }
                            restoreRequestedSeek(pending);
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
                                outputBuffer, outputBytes, outputFormat.getChannels(),
                                outputFormat.getSampleRate() / 2.0);
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

    private VisualizationFrame analyzeAndApplyVolume(byte[] buffer, int length, int channels,
                                                      double maxFrequencyHz) {
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
        float[] spectrum = spectrumOf(buffer, length, channels);
        double minFrequencyHz = lowestActiveFrequency(spectrum, maxFrequencyHz, false);
        return new VisualizationFrame(waveform, spectrum,
                minFrequencyHz, maxFrequencyHz, false);
    }

    private float[] spectrumOf(byte[] buffer, int length, int channels) {
        int channelCount = Math.max(1, channels);
        int availableFrames = length / (channelCount * 2);
        int fftSize = availableFrames >= 1_024 ? 4_096
                : Integer.highestOneBit(availableFrames);
        if (fftSize < 2) {
            return new float[64];
        }
        double[] real = new double[fftSize];
        double[] imaginary = new double[fftSize];
        double windowSum = 0;
        int windowSamples = Math.min(availableFrames, fftSize);
        for (int index = 0; index < windowSamples; index++) {
            int frameOffset = index * channelCount * 2;
            double sampleSum = 0;
            for (int channel = 0; channel < channelCount; channel++) {
                int offset = frameOffset + channel * 2;
                sampleSum += (short) ((buffer[offset] & 0xff) | (buffer[offset + 1] << 8));
            }
            double sample = sampleSum / channelCount;
            double window = 0.5 - 0.5 * Math.cos(2 * Math.PI * index / Math.max(1, windowSamples - 1));
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

    static double lowestActiveFrequency(float[] spectrum, double maxFrequencyHz,
                                        boolean bandAggregated) {
        if (spectrum == null || spectrum.length == 0
                || !Double.isFinite(maxFrequencyHz) || maxFrequencyHz <= 0) {
            return Double.NaN;
        }
        float peak = 0;
        for (float value : spectrum) {
            if (Float.isFinite(value)) {
                peak = Math.max(peak, Math.max(0, value));
            }
        }
        double binWidthHz = maxFrequencyHz / spectrum.length;
        int firstBin = bandAggregated ? 0 : Math.min(1, spectrum.length - 1);
        double floor = bandAggregated ? Math.pow(10.0, -59.0 / 20.0) : 1e-5;
        double threshold = Math.max(floor, peak * ACTIVE_SPECTRUM_RELATIVE_FLOOR);
        for (int bin = firstBin; bin < spectrum.length; bin++) {
            if (Float.isFinite(spectrum[bin]) && spectrum[bin] > threshold) {
                return bandAggregated && bin == 0
                        ? Math.min(20.0, binWidthHz) : Math.max(binWidthHz, bin * binWidthHz);
            }
        }
        return Double.NaN;
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
        Closeable activeInput = randomInput;
        randomInput = null;
        if (activeInput != null) {
            try {
                activeInput.close();
            } catch (Exception ignored) {
                // Closing is best effort during a FLAC seek or track switch.
            }
        }
    }
}
