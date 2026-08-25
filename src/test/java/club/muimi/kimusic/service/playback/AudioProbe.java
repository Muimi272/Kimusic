package club.muimi.kimusic.service.playback;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import org.jflac.sound.spi.Flac2PcmAudioInputStream;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.image.Image;

public final class AudioProbe {
    private AudioProbe() {
    }

    public static void main(String[] args) throws Exception {
        File file = new File(String.join(" ", args));
        try (AudioInputStream encoded = AudioSystem.getAudioInputStream(file)) {
            AudioFormat source = encoded.getFormat();
            System.out.println("source=" + source);
            for (AudioFormat target : AudioSystem.getTargetFormats(AudioFormat.Encoding.PCM_SIGNED, source)) {
                System.out.println("target=" + target);
            }
            AudioFormat[] targets = AudioSystem.getTargetFormats(AudioFormat.Encoding.PCM_SIGNED, source);
            AudioFormat pcm = targets.length == 0 ? source : targets[0];
            System.out.println("pcm=" + pcm);
            System.out.println("conversion=" + AudioSystem.isConversionSupported(pcm, source));
            AudioFormat explicit = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    source.getSampleRate(), 16, source.getChannels(), source.getChannels() * 2,
                    source.getSampleRate(), false);
            System.out.println("explicit=" + explicit);
            System.out.println("explicitConversion=" + AudioSystem.isConversionSupported(explicit, source));
            try (AudioInputStream explicitDecoded = AudioSystem.getAudioInputStream(explicit, encoded)) {
                System.out.println("explicitBytes=" + explicitDecoded.read(new byte[8192]));
            } catch (Exception explicitFailure) {
                System.out.println("explicitError=" + explicitFailure.getMessage());
            }
            try (AudioInputStream freshEncoded = AudioSystem.getAudioInputStream(file);
                 AudioInputStream directJflac = new Flac2PcmAudioInputStream(freshEncoded, explicit, -1)) {
                System.out.println("directJflacBytes=" + directJflac.read(new byte[8192]));
            } catch (Exception directFailure) {
                System.out.println("directJflacError=" + directFailure.getMessage());
            }
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm, encoded)) {
                AudioFormat output = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 16,
                        pcm.getChannels(), pcm.getChannels() * 2, 48000, false);
                System.out.println("output=" + output);
                System.out.println("outputConversion=" + AudioSystem.isConversionSupported(output, pcm));
                AudioInputStream playable = null;
                try {
                    playable = AudioSystem.getAudioInputStream(output, decoded);
                    byte[] bytes = new byte[8192];
                    System.out.println("decodedBytes=" + playable.read(bytes));
                } catch (IllegalArgumentException unsupported) {
                    System.out.println("directOutputUnsupported=true");
                } finally {
                    if (playable != null) {
                        playable.close();
                    }
                }
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, output);
                System.out.println("lineSupported=" + AudioSystem.isLineSupported(info));
                if (AudioSystem.isLineSupported(info)) {
                    SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                    line.open(output, 16384);
                    line.close();
                }
            }
        }

        JavaSoundPlaybackEngine engine = new JavaSoundPlaybackEngine();
        CountDownLatch progressed = new CountDownLatch(1);
        AtomicReference<Throwable> playbackError = new AtomicReference<>();
        engine.open(file.toPath(), new PlaybackListener() {
            @Override public void onReady(double durationSeconds) {
                System.out.println("engineDuration=" + durationSeconds);
            }

            @Override public void onProgress(double currentSeconds, VisualizationFrame visualization) {
                if (currentSeconds >= 0.25 && progressed.getCount() > 0) {
                    System.out.println("engineProgress=" + currentSeconds);
                    progressed.countDown();
                    engine.close();
                }
            }

            @Override public void onPlayingChanged(boolean playing) {
            }

            @Override public void onArtwork(Image image) {
            }

            @Override public void onEnd() {
                progressed.countDown();
            }

            @Override public void onError(Throwable error) {
                playbackError.set(error);
                progressed.countDown();
            }
        }, 0.05, true);

        boolean completed = progressed.await(10, TimeUnit.SECONDS);
        engine.close();
        if (!completed) {
            throw new IllegalStateException("Playback engine did not produce progress within 10 seconds");
        }
        if (playbackError.get() != null) {
            throw new IllegalStateException("Playback engine failed", playbackError.get());
        }
        System.out.println("enginePlayback=true");
    }
}
