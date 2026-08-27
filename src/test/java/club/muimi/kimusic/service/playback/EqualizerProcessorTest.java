package club.muimi.kimusic.service.playback;

import club.muimi.kimusic.model.EqualizerSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EqualizerProcessorTest {
    @Test
    void enabledPresetChangesPcmSamples() {
        byte[] input = new byte[4_000];
        input[0] = 0x00;
        input[1] = 0x40;
        byte[] original = input.clone();
        EqualizerSettings settings = EqualizerSettings.defaults();
        settings.setEnabled(true);
        settings.setGains(EqualizerSettings.builtInPresets().get("Bass Boost"));
        EqualizerProcessor processor = new EqualizerProcessor();

        processor.process(input, input.length, 2, 44_100, settings);

        boolean changed = false;
        for (int index = 0; index < input.length; index++) {
            if (input[index] != original[index]) {
                changed = true;
                break;
            }
        }
        assertNotEquals(false, changed);
    }

    @Test
    void loudBoostKeepsHeadroomAndAvoidsHardClipping() {
        byte[] input = new byte[44_100 * 2 * 2];
        for (int offset = 0; offset < input.length; offset += 2) {
            input[offset] = (byte) 0x30;
            input[offset + 1] = 0x70;
        }
        EqualizerSettings settings = EqualizerSettings.defaults();
        settings.setEnabled(true);
        settings.setGains(new double[]{12, 12, 12, 12, 12, 12, 12, 12, 12, 12});
        EqualizerProcessor processor = new EqualizerProcessor();

        processor.process(input, input.length, 2, 44_100, settings);

        int maximum = 0;
        for (int offset = 0; offset < input.length; offset += 2) {
            maximum = Math.max(maximum, Math.abs((short) ((input[offset] & 0xff)
                    | (input[offset + 1] << 8))));
        }
        assertTrue(maximum < Short.MAX_VALUE, "equalizer output should retain headroom");
    }
}
