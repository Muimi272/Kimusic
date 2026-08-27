package club.muimi.kimusic.service.playback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualizationFrameTest {
    @Test
    void frameDefensivelyCopiesAnalysisArrays() {
        float[] waveform = {0.1f, 0.2f};
        float[] spectrum = {0.3f, 0.4f};
        VisualizationFrame frame = new VisualizationFrame(waveform, spectrum);
        waveform[0] = 1;
        spectrum[0] = 1;

        assertEquals(0.1f, frame.waveform()[0]);
        assertEquals(0.3f, frame.spectrum()[0]);
    }

    @Test
    void frameCarriesTheDetectedFrequencyRange() {
        VisualizationFrame frame = new VisualizationFrame(
                new float[0], new float[0], 86.0, 22_050.0, false);

        assertEquals(86.0, frame.minFrequencyHz());
        assertEquals(22_050.0, frame.maxFrequencyHz());
    }

    @Test
    void framePreservesAnUnknownMinimumForSilence() {
        VisualizationFrame frame = new VisualizationFrame(
                new float[0], new float[0], Double.NaN, 22_050.0, false);

        assertEquals(Double.NaN, frame.minFrequencyHz());
    }

    @Test
    void emptyFrameDoesNotInitializeTheDetectedMinimum() {
        assertTrue(Double.isNaN(VisualizationFrame.empty().minFrequencyHz()));
    }
}
