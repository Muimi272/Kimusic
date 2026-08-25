package club.muimi.kimusic.service.playback;

public record VisualizationFrame(float[] waveform, float[] spectrum) {
    public VisualizationFrame {
        waveform = waveform == null ? new float[0] : waveform.clone();
        spectrum = spectrum == null ? new float[0] : spectrum.clone();
    }

    @Override
    public float[] waveform() {
        return waveform.clone();
    }

    @Override
    public float[] spectrum() {
        return spectrum.clone();
    }

    public static VisualizationFrame empty() {
        return new VisualizationFrame(new float[64], new float[64]);
    }
}
