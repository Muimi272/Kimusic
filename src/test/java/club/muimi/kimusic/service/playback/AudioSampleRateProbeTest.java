package club.muimi.kimusic.service.playback;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioSampleRateProbeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsTheSampleRateFromAnAdtsAacHeader() throws Exception {
        Path aac = temporaryDirectory.resolve("sample.aac");
        Files.write(aac, new byte[]{
                (byte) 0xff, (byte) 0xf1, 0x4c, (byte) 0x80, 0x00, 0x1f, (byte) 0xfc
        });

        assertEquals(48_000.0, AudioSampleRateProbe.sampleRateHz(aac));
        assertEquals(24_000.0, AudioSampleRateProbe.nyquistHz(aac));
    }

    @Test
    void rejectsInvalidAdtsHeaders() {
        assertEquals(0, AudioSampleRateProbe.sampleRateFromAdtsHeader(
                new byte[]{0x00, 0x01, 0x02, 0x03}));
    }
}
