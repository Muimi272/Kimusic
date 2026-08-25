package club.muimi.kimusic.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void playlistNamesAndTracksAreUnique() throws Exception {
        Path track = Files.writeString(temporaryDirectory.resolve("track.mp3"), "");
        PlaylistService service = new PlaylistService();

        assertTrue(service.create("  Focus  "));
        assertFalse(service.create("Focus"));
        assertTrue(service.addTrack("Focus", track));
        assertFalse(service.addTrack("Focus", track));
        assertEquals(1, service.tracks("Focus").size());
    }
}
