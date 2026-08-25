package club.muimi.kimusic.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LyricsServiceTest {
    private final LyricsService service = new LyricsService();

    @Test
    void parsesAndSortsMultipleLrcTimestamps() {
        List<LyricsService.LyricLine> lines = service.parse(List.of(
                "[00:12.50][00:20.00]Chorus",
                "[00:03.1]Verse"
        ));

        assertEquals(3, lines.size());
        assertEquals("Verse", lines.get(0).text());
        assertEquals(12.5, lines.get(1).seconds());
        assertEquals(1, service.activeLineIndex(lines, 13));
    }
}
