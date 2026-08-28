package club.muimi.kimusic.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EqualizerSettingsTest {
    @Test
    void clampsGainsAndCopiesMutableValues() {
        double[] gains = {20, -20, 3};
        EqualizerSettings settings = new EqualizerSettings(true, "Custom", gains, Map.of());
        gains[0] = 0;

        assertEquals(12, settings.getGains()[0]);
        assertEquals(-12, settings.getGains()[1]);
        double[] returned = settings.getGains();
        returned[2] = 99;
        assertEquals(3, settings.getGains()[2]);
    }

    @Test
    void exposesBuiltInPresetsAndKeepsCustomPresetsSeparate() {
        EqualizerSettings settings = EqualizerSettings.defaults();
        settings.setCustomPreset("My mix", new double[]{1, 2, 3});

        assertTrue(settings.allPresets().containsKey("Bass Boost"));
        assertArrayEquals(new double[]{1, 2, 3, 0, 0, 0, 0, 0, 0, 0},
                settings.allPresets().get("My mix"));
    }
}
