package club.muimi.kimusic.util;

import club.muimi.kimusic.model.AppState;
import club.muimi.kimusic.service.NoticeService;
import club.muimi.kimusic.status.VisualizationMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSerializerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void stateRoundTripsThroughAtomicFile() {
        DataSerializer serializer = DataSerializer.init(NoticeService.init(),
                temporaryDirectory.resolve("state.bin"));
        AppState expected = new AppState(List.of("C:/Music"),
                Map.of("Focus", List.of("C:/Music/a.mp3")),
                List.of("C:/Music/a.mp3"), true, 0.42, VisualizationMode.SPECTRUM);

        assertTrue(serializer.save(expected));
        AppState actual = serializer.load();

        assertEquals(expected.getLibraryRoots(), actual.getLibraryRoots());
        assertEquals(expected.getPlaylists(), actual.getPlaylists());
        assertEquals(expected.getQueue(), actual.getQueue());
        assertTrue(actual.isDarkTheme());
        assertEquals(0.42, actual.getVolume());
        assertEquals(VisualizationMode.SPECTRUM, actual.getVisualizationMode());
    }
}
