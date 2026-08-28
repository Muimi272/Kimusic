package club.muimi.kimusic.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioScanServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void findsAudioAndSkipsSystemNamedDirectories() throws Exception {
        Path music = Files.write(temporaryDirectory.resolve("song.mp3"), new byte[]{1});
        Path system = Files.createDirectories(temporaryDirectory.resolve("Windows"));
        Files.write(system.resolve("system.wav"), new byte[]{1});
        Path cache = Files.createDirectories(temporaryDirectory.resolve(".cache"));
        Files.write(cache.resolve("cached.ogg"), new byte[]{1});
        Files.write(temporaryDirectory.resolve("notes.txt"), new byte[]{1});

        List<Path> found = AudioScanService.scan(List.of(temporaryDirectory), (v, t, f) -> { }, () -> false);

        assertEquals(List.of(music.toAbsolutePath().normalize()), found);
        assertEquals(List.of(cache.resolve("cached.ogg").toAbsolutePath().normalize()),
                AudioScanService.scan(List.of(cache), (v, t, f) -> { }, () -> false));
    }

    @Test
    void reportsProgressAndHonorsCancellation() throws Exception {
        Files.write(temporaryDirectory.resolve("song.wav"), new byte[]{1});
        AtomicInteger callbacks = new AtomicInteger();

        assertThrows(CancellationException.class, () -> AudioScanService.scan(
                List.of(temporaryDirectory), (v, t, f) -> callbacks.incrementAndGet(),
                () -> callbacks.get() > 0));
        assertTrue(callbacks.get() > 0);
    }
}
