package club.muimi.kimusic.service.playback;

import org.jaudiotagger.audio.AudioFileIO;

import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class AudioSampleRateProbe {
    private static final double DEFAULT_SAMPLE_RATE_HZ = 44_100.0;
    private static final int[] AAC_SAMPLE_RATES = {
            96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000,
            22_050, 16_000, 12_000, 11_025, 8_000, 7_350
    };

    private AudioSampleRateProbe() {
    }

    static double nyquistHz(Path track) {
        double sampleRate = sampleRateHz(track);
        return sampleRate > 0 && Double.isFinite(sampleRate)
                ? sampleRate / 2.0 : DEFAULT_SAMPLE_RATE_HZ / 2.0;
    }

    static double sampleRateHz(Path track) {
        if (track == null || !Files.isRegularFile(track)) {
            return DEFAULT_SAMPLE_RATE_HZ;
        }
        if (extension(track).equals("aac")) {
            double adtsRate = readAdtsSampleRate(track);
            if (adtsRate > 0) {
                return adtsRate;
            }
        }
        try {
            float sampleRate = AudioSystem.getAudioFileFormat(track.toFile())
                    .getFormat().getSampleRate();
            if (sampleRate > 0 && Float.isFinite(sampleRate)) {
                return sampleRate;
            }
        } catch (Exception ignored) {
            // The JavaFX backend handles containers that Java Sound may not recognize.
        }
        try {
            int sampleRate = AudioFileIO.read(track.toFile()).getAudioHeader().getSampleRateAsNumber();
            if (sampleRate > 0) {
                return sampleRate;
            }
        } catch (Exception ignored) {
            // Fall through to the common 44.1 kHz default for unknown containers.
        }
        return DEFAULT_SAMPLE_RATE_HZ;
    }

    private static double readAdtsSampleRate(Path track) {
        try (InputStream input = Files.newInputStream(track)) {
            return sampleRateFromAdtsHeader(input.readNBytes(7));
        } catch (IOException exception) {
            return 0;
        }
    }

    static double sampleRateFromAdtsHeader(byte[] header) {
        if (header == null || header.length < 4
                || (header[0] & 0xff) != 0xff || (header[1] & 0xf6) != 0xf0) {
            return 0;
        }
        int sampleRateIndex = (header[2] >>> 2) & 0x0f;
        return sampleRateIndex < AAC_SAMPLE_RATES.length ? AAC_SAMPLE_RATES[sampleRateIndex] : 0;
    }

    private static String extension(Path track) {
        String name = track.getFileName().toString().toLowerCase(Locale.ROOT);
        int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(separator + 1);
    }
}
