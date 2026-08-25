package club.muimi.kimusic.service.playback;

public record VisualizationFrame(float[] waveform, float[] spectrum, double maxFrequencyHz) {
    public VisualizationFrame(float[] waveform, float[] spectrum) {
        this(waveform, spectrum, 20_000.0);
    }

    public VisualizationFrame {
        waveform = waveform == null ? new float[0] : waveform.clone();
        spectrum = spectrum == null ? new float[0] : spectrum.clone();
        maxFrequencyHz = Double.isFinite(maxFrequencyHz) && maxFrequencyHz > 0
                ? maxFrequencyHz : 20_000.0;
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
        return new VisualizationFrame(new float[64], new float[64], 20_000.0);
    }
}
