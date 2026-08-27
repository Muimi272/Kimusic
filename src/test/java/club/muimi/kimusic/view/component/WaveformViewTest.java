package club.muimi.kimusic.view.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveformViewTest {
    @Test
    void projectsLowestActiveFrequencyToTheRightmostBand() {
        float[] source = new float[128];
        source[8] = 1;
        source[127] = 0.75f;
        double maximum = 20_000.0;
        double minimum = 8 * maximum / source.length;

        float[] projected = WaveformView.projectSpectrum(
                source, minimum, maximum, maximum, 64);

        assertTrue(projected[63] > 0.5f, "the rightmost band must contain the lowest frequency");
        assertTrue(projected[0] > 0.3f, "the leftmost band must contain the highest frequency");
    }

    @Test
    void mapsDisplayFrequenciesAgainstTheFullNyquistRange() {
        float[] source = new float[128];
        double sourceMaximum = 22_050.0;
        int twentyKilohertzBin = (int) Math.floor(20_000.0 / sourceMaximum * source.length);
        source[twentyKilohertzBin] = 1;

        float[] projected = WaveformView.projectSpectrum(
                source, 80.0, sourceMaximum, 20_000.0, 64);

        assertTrue(projected[0] > 0.05f,
                "the leftmost band must end at the display limit, not the Nyquist bin");
    }
}
