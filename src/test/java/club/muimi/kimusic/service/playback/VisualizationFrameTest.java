package club.muimi.kimusic.service.playback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
