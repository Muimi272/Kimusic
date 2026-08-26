package club.muimi.kimusic.util;

import club.muimi.kimusic.status.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class I18nTest {
    @Test
    void translatesStaticLabelsInBothDirections() {
        assertEquals("Music Library", I18n.text(Language.ENGLISH, "音乐资料库"));
        assertEquals("音乐资料库", I18n.text(Language.CHINESE, "音乐资料库"));
        assertEquals("音乐资料库", I18n.keyFor("Music Library"));
    }
}
