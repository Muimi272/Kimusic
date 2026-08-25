package club.muimi.kimusic.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTreeServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parentRootReplacesNestedRoot() throws Exception {
        Path album = Files.createDirectories(temporaryDirectory.resolve("artist/album"));
        FileTreeService service = FileTreeService.init();

        assertTrue(service.addRootFile(album.toFile()));
        assertTrue(service.addRootFile(temporaryDirectory.toFile()));
        assertEquals(1, service.getRootFiles().size());
        assertEquals(temporaryDirectory.toAbsolutePath().normalize(),
                service.getRootFiles().getFirst().toPath());
        assertFalse(service.addRootFile(album.toFile()));
    }

    @Test
    void scanReturnsOnlyKnownAudioFiles() throws Exception {
        Files.writeString(temporaryDirectory.resolve("song.mp3"), "");
        Files.writeString(temporaryDirectory.resolve("encrypted.ncm"), "");
        Files.writeString(temporaryDirectory.resolve("cover.jpg"), "");
        Path nested = Files.createDirectories(temporaryDirectory.resolve("nested"));
        Files.writeString(nested.resolve("track.FLAC"), "");
        FileTreeService service = FileTreeService.init();
        service.addRootFile(temporaryDirectory.toFile());

        assertEquals(3, service.scanAudioFiles().size());
    }
}
