package club.muimi.kimusic.service.playback;

public record VisualizationFrame(float[] waveform, float[] spectrum,
                                 double minFrequencyHz, double maxFrequencyHz,
                                 boolean spectrumIsBandAggregated) {
    public VisualizationFrame(float[] waveform, float[] spectrum) {
        this(waveform, spectrum, 20.0, 20_000.0, false);
    }

    public VisualizationFrame(float[] waveform, float[] spectrum, double maxFrequencyHz) {
        this(waveform, spectrum, Math.min(20.0, maxFrequencyHz), maxFrequencyHz, false);
    }

    public VisualizationFrame(float[] waveform, float[] spectrum,
                              double maxFrequencyHz, boolean spectrumIsBandAggregated) {
        this(waveform, spectrum, Math.min(20.0, maxFrequencyHz),
                maxFrequencyHz, spectrumIsBandAggregated);
    }

    public VisualizationFrame {
        waveform = waveform == null ? new float[0] : waveform.clone();
        spectrum = spectrum == null ? new float[0] : spectrum.clone();
        maxFrequencyHz = Double.isFinite(maxFrequencyHz) && maxFrequencyHz > 0
                ? maxFrequencyHz : 20_000.0;
        minFrequencyHz = Double.isNaN(minFrequencyHz) ? Double.NaN
                : Double.isFinite(minFrequencyHz) && minFrequencyHz > 0
                ? Math.min(minFrequencyHz, maxFrequencyHz) : Math.min(20.0, maxFrequencyHz);
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
        return new VisualizationFrame(new float[64], new float[64],
                Double.NaN, 20_000.0, false);
    }
}
