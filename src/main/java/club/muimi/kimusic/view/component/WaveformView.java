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
        pushSpectrum(frame.spectrum(), frame.maxFrequencyHz(), frame.spectrumIsBandAggregated());
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

    private void pushSpectrum(float[] values, double maxFrequencyHz, boolean bandAggregated) {
        if (values == null || values.length == 0) {
            return;
        }
        double displayMaxFrequency = Math.max(20.0, Math.min(20_000.0, maxFrequencyHz));
        for (int index = 0; index < spectrum.length; index++) {
            int from;
            int to;
            if (bandAggregated) {
                from = Math.min(values.length - 1,
                        (int) ((long) index * values.length / spectrum.length));
                to = Math.min(values.length, from + 1);
            } else {
                double fromFrequency = 20.0 * Math.pow(displayMaxFrequency / 20.0,
                        (double) index / spectrum.length);
                double toFrequency = 20.0 * Math.pow(displayMaxFrequency / 20.0,
                        (double) (index + 1) / spectrum.length);
                double binsPerHertz = values.length / maxFrequencyHz;
                from = Math.max(1, (int) Math.floor(fromFrequency * binsPerHertz));
                to = Math.min(values.length, Math.max(from + 1,
                        (int) Math.ceil(toFrequency * binsPerHertz)));
            }
            double energy = 0;
            int count = 0;
            for (int source = from; source < to; source++) {
                double value = Math.max(0, Math.min(20, values[source]));
                energy += value * value;
                count++;
            }
            float value = count == 0 ? 0 : (float) Math.sqrt(energy / count);
            // Preserve headroom so dense peaks can be normalized during drawing.
            spectrum[index] += (value - spectrum[index]) * (value > spectrum[index] ? 0.62f : 0.2f);
            spectrumPeaks[index] = Math.max(spectrum[index], spectrumPeaks[index] - 0.018f);
        }
    }

    public void clear() {
        Arrays.fill(history, 0);
        Arrays.fill(spectrum, 0);
        Arrays.fill(spectrumPeaks, 0);
        cursor = 0;
        displayScale = 1.0;
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
