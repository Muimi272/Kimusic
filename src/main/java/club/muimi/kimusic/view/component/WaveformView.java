package club.muimi.kimusic.view.component;

import club.muimi.kimusic.service.playback.VisualizationFrame;
import club.muimi.kimusic.status.VisualizationMode;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.Arrays;

public final class WaveformView extends Region {
    private static final int HISTORY_SIZE = 240;

    private final Canvas canvas = new Canvas();
    private final float[] history = new float[HISTORY_SIZE];
    private final float[] spectrum = new float[64];
    private final float[] spectrumPeaks = new float[64];
    private final DoubleProperty progress = new SimpleDoubleProperty();
    private final ObjectProperty<VisualizationMode> mode =
            new SimpleObjectProperty<>(VisualizationMode.WAVEFORM);
    private int cursor;
    private double displayScale = 1.0;
    private double observedMinFrequencyHz = Double.NaN;

    public WaveformView() {
        getStyleClass().add("waveform-view");
        getChildren().add(canvas);
        widthProperty().addListener(observable -> draw());
        heightProperty().addListener(observable -> draw());
        progress.addListener(observable -> draw());
        mode.addListener(observable -> draw());
        setMinHeight(72);
        setPrefHeight(96);
    }

    public void push(VisualizationFrame frame) {
        if (frame == null) {
            return;
        }
        pushWaveform(frame.waveform());
        pushSpectrum(frame.spectrum(), frame.minFrequencyHz(), frame.maxFrequencyHz());
        draw();
    }

    private void pushWaveform(float[] samples) {
        if (samples == null || samples.length == 0) {
            return;
        }
        float peak = 0;
        for (float sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        history[cursor] = Math.max(0.035f, Math.min(1, peak));
        cursor = (cursor + 1) % history.length;
    }

    private void pushSpectrum(float[] values, double minFrequencyHz, double maxFrequencyHz) {
        if (values == null || values.length == 0) {
            return;
        }
        double sourceMaxFrequency = Math.max(1.0, maxFrequencyHz);
        double displayMaxFrequency = Math.min(20_000.0, sourceMaxFrequency);
        if (Double.isFinite(minFrequencyHz) && minFrequencyHz > 0) {
            double detectedMinimum = Math.max(1.0,
                    Math.min(displayMaxFrequency, minFrequencyHz));
            if (!Double.isFinite(observedMinFrequencyHz)
                    || detectedMinimum < observedMinFrequencyHz) {
                if (Double.isFinite(observedMinFrequencyHz)) {
                    Arrays.fill(spectrum, 0);
                    Arrays.fill(spectrumPeaks, 0);
                }
                observedMinFrequencyHz = detectedMinimum;
            }
        }
        double projectionMinimum = Double.isFinite(observedMinFrequencyHz)
                ? observedMinFrequencyHz : Math.max(1.0, sourceMaxFrequency / values.length);
        float[] projected = projectSpectrum(values, projectionMinimum,
                sourceMaxFrequency, displayMaxFrequency, spectrum.length);
        for (int index = 0; index < spectrum.length; index++) {
            float value = projected[index];
            // Preserve headroom so dense peaks can be normalized during drawing.
            spectrum[index] += (value - spectrum[index]) * (value > spectrum[index] ? 0.62f : 0.2f);
            spectrumPeaks[index] = Math.max(spectrum[index], spectrumPeaks[index] - 0.018f);
        }
    }

    static float[] projectSpectrum(float[] values, double minFrequencyHz,
                                   double sourceMaxFrequencyHz,
                                   double displayMaxFrequencyHz, int bandCount) {
        if (values == null || values.length == 0 || bandCount <= 0) {
            return new float[0];
        }
        double sourceMaximum = Double.isFinite(sourceMaxFrequencyHz) && sourceMaxFrequencyHz > 0
                ? sourceMaxFrequencyHz : 20_000.0;
        double maximum = Double.isFinite(displayMaxFrequencyHz) && displayMaxFrequencyHz > 0
                ? Math.min(displayMaxFrequencyHz, sourceMaximum) : Math.min(20_000.0, sourceMaximum);
        double minimum = Double.isFinite(minFrequencyHz) && minFrequencyHz > 0
                ? Math.min(minFrequencyHz, maximum) : Math.min(20.0, maximum);
        double ratio = maximum / minimum;
        float[] projected = new float[bandCount];
        for (int index = 0; index < bandCount; index++) {
            double lowExponent = (double) (bandCount - index - 1) / bandCount;
            double highExponent = (double) (bandCount - index) / bandCount;
            double fromBin = minimum * Math.pow(ratio, lowExponent) / sourceMaximum * values.length;
            double toBin = minimum * Math.pow(ratio, highExponent) / sourceMaximum * values.length;
            int firstBin = Math.max(0, (int) Math.floor(fromBin));
            int lastBin = Math.min(values.length - 1, (int) Math.ceil(toBin) - 1);
            double energy = 0;
            double count = 0;
            for (int source = firstBin; source <= lastBin; source++) {
                double weight = Math.min(toBin, source + 1.0) - Math.max(fromBin, source);
                if (weight <= 0) {
                    continue;
                }
                double value = Float.isFinite(values[source])
                        ? Math.max(0, Math.min(20, values[source])) : 0;
                energy += value * value * weight;
                count += weight;
            }
            projected[index] = count == 0 ? 0 : (float) Math.sqrt(energy / count);
        }
        return projected;
    }

    public void clear() {
        Arrays.fill(history, 0);
        Arrays.fill(spectrum, 0);
        Arrays.fill(spectrumPeaks, 0);
        cursor = 0;
        displayScale = 1.0;
        observedMinFrequencyHz = Double.NaN;
        draw();
    }

    public DoubleProperty progressProperty() {
        return progress;
    }

    public ObjectProperty<VisualizationMode> modeProperty() {
        return mode;
    }

    public VisualizationMode getMode() {
        return mode.get();
    }

    public void setMode(VisualizationMode value) {
        mode.set(value == null ? VisualizationMode.WAVEFORM : value);
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        draw();
    }

    private void draw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.clearRect(0, 0, width, height);
        if (mode.get() == VisualizationMode.SPECTRUM) {
            drawSpectrum(graphics, width, height);
        } else {
            drawWaveform(graphics, width, height);
        }
    }

    private void drawWaveform(GraphicsContext graphics, double width, double height) {
        double center = height / 2;
        double gap = 2;
        double barWidth = Math.max(1, width / history.length - gap);
        double playhead = width * Math.max(0, Math.min(1, progress.get()));
        for (int index = 0; index < history.length; index++) {
            int historyIndex = (cursor + index) % history.length;
            double x = index * width / history.length;
            double magnitude = history[historyIndex];
            if (magnitude == 0) {
                magnitude = 0.035 + 0.03 * Math.sin(index * 0.31);
            }
            double barHeight = Math.max(3, magnitude * (height - 10));
            graphics.setFill(x <= playhead ? Color.web("#1aa79b") : Color.web("#7f8b91", 0.38));
            graphics.fillRoundRect(x, center - barHeight / 2, barWidth, barHeight, 2, 2);
        }
    }

    private void drawSpectrum(GraphicsContext graphics, double width, double height) {
        double gap = 3;
        double bandWidth = Math.max(2, width / spectrum.length - gap);
        float maximum = 0;
        for (float value : spectrum) {
            maximum = Math.max(maximum, value);
        }
        // Normalize active frames against their highest band while bounding gain
        // on near-silent input so background noise does not fill the view.
        double targetScale = maximum > 0.08f ? 1.0 / maximum : 1.0;
        displayScale += (targetScale - displayScale) * 0.16;
        displayScale = Math.max(0.35, Math.min(6.0, displayScale));
        for (int index = 0; index < spectrum.length; index++) {
            double x = index * width / spectrum.length;
            float raw = (float) (spectrum[index] * displayScale);
            double db = 20 * Math.log10(raw + 1e-12);
            double normalized = (db + 120) / 120;
            double magnitude = Math.max(0.025, Math.min(1.0, normalized));
            double barHeight = Math.max(3, magnitude * (height - 13));
            graphics.setFill(Color.web("#1aa79b", 0.9));
            graphics.fillRoundRect(x, height - barHeight, bandWidth, barHeight, 3, 3);
            float peakRaw = (float) (spectrumPeaks[index] * displayScale);
            double peakDb = 20 * Math.log10(peakRaw + 1e-12);
            double peakNormalized = (peakDb + 120) / 120;
            double peakMagnitude = Math.max(0.025, Math.min(1.0, peakNormalized));
            double peakY = height - Math.max(3, peakMagnitude * (height - 13));
            graphics.setFill(Color.web("#8be0d5", 0.86));
            graphics.fillRoundRect(x, peakY, bandWidth, 2, 2, 2);
        }
    }
}
