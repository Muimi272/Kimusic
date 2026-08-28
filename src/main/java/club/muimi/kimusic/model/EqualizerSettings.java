package club.muimi.kimusic.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EqualizerSettings implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    public static final int BAND_COUNT = 10;
    public static final double[] BAND_FREQUENCIES = {
            31.0, 62.0, 125.0, 250.0, 500.0,
            1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0
    };
    private boolean enabled;
    private String activePreset;
    private double[] gains;
    private Map<String, double[]> customPresets;

    public EqualizerSettings(boolean enabled, String activePreset,
                             double[] gains, Map<String, double[]> customPresets) {
        this.enabled = enabled;
        this.activePreset = normalizeName(activePreset);
        this.gains = normalizeGains(gains);
        this.customPresets = normalizePresets(customPresets);
    }

    public static EqualizerSettings defaults() {
        return new EqualizerSettings(false, "Flat", new double[BAND_COUNT], Map.of());
    }

    public static Map<String, double[]> builtInPresets() {
        Map<String, double[]> presets = new LinkedHashMap<>();
        presets.put("Flat", new double[BAND_COUNT]);
        presets.put("Bass Boost", new double[]{6, 5, 4, 2, 1, 0, 0, 0, 0, 0});
        presets.put("Treble Boost", new double[]{0, 0, 0, 0, 0, 1, 2, 4, 5, 6});
        presets.put("Vocal", new double[]{-2, -1, 0, 3, 4, 4, 3, 1, -1, -2});
        presets.put("Rock", new double[]{5, 3, 1, -1, -2, 1, 3, 5, 5, 4});
        presets.put("Classical", new double[]{4, 3, 2, 1, 0, 0, -1, -2, 3, 4});
        return presets;
    }

    public EqualizerSettings copy() {
        return new EqualizerSettings(enabled, activePreset, gains, customPresets);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getActivePreset() { return activePreset; }
    public void setActivePreset(String value) { activePreset = normalizeName(value); }
    public double[] getGains() { return gains.clone(); }
    public void setGains(double[] value) { gains = normalizeGains(value); }
    public Map<String, double[]> getCustomPresets() { return copyPresets(customPresets); }
    public void setCustomPreset(String name, double[] value) {
        String normalized = normalizeName(name);
        if (!normalized.isEmpty() && value != null) customPresets.put(normalized, normalizeGains(value));
    }
    public Map<String, double[]> allPresets() {
        Map<String, double[]> result = builtInPresets();
        result.putAll(copyPresets(customPresets));
        return result;
    }
    public static double clampGain(double gain) {
        return Double.isFinite(gain) ? Math.max(-12, Math.min(12, gain)) : 0;
    }
    private static String normalizeName(String value) { return value == null ? "" : value.strip(); }
    private static double[] normalizeGains(double[] values) {
        double[] result = new double[BAND_COUNT];
        if (values != null) for (int i = 0; i < Math.min(values.length, BAND_COUNT); i++) result[i] = clampGain(values[i]);
        return result;
    }
    private static Map<String, double[]> normalizePresets(Map<String, double[]> values) {
        Map<String, double[]> result = new LinkedHashMap<>();
        if (values != null) values.forEach((name, gains) -> {
            String normalized = normalizeName(name);
            if (!normalized.isEmpty() && gains != null) result.put(normalized, normalizeGains(gains));
        });
        return result;
    }
    private static Map<String, double[]> copyPresets(Map<String, double[]> values) {
        Map<String, double[]> result = new LinkedHashMap<>();
        values.forEach((name, gains) -> result.put(name, Arrays.copyOf(gains, gains.length)));
        return result;
    }
}
