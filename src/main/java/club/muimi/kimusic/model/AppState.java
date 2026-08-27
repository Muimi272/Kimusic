package club.muimi.kimusic.model;

import club.muimi.kimusic.status.VisualizationMode;
import club.muimi.kimusic.status.Language;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AppState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int version;
    private final List<String> libraryRoots;
    private final Map<String, List<String>> playlists;
    private final List<String> queue;
    private final String lastTrack;
    private final boolean darkTheme;
    private final double volume;
    private final VisualizationMode visualizationMode;
    private final Language language;
    private final String lyricHighlightColor;
    private final Boolean autoDecodeNcm;
    private final int lyricsFontSize;
    private final EqualizerSettings equalizerSettings;
    private final List<String> discoveredFiles;

    public AppState(List<String> libraryRoots, Map<String, List<String>> playlists,
                    List<String> queue, boolean darkTheme, double volume) {
        this(libraryRoots, playlists, queue, darkTheme, volume, VisualizationMode.WAVEFORM);
    }

    public AppState(List<String> libraryRoots, Map<String, List<String>> playlists,
                    List<String> queue, boolean darkTheme, double volume,
                    VisualizationMode visualizationMode) {
        this(libraryRoots, playlists, queue, darkTheme, volume, visualizationMode, null);
    }

    public AppState(List<String> libraryRoots, Map<String, List<String>> playlists,
                    List<String> queue, boolean darkTheme, double volume,
                    VisualizationMode visualizationMode, String lastTrack) {
        this(libraryRoots, playlists, queue, darkTheme, volume, visualizationMode,
                lastTrack, Language.CHINESE);
    }

    public AppState(List<String> libraryRoots, Map<String, List<String>> playlists,
                    List<String> queue, boolean darkTheme, double volume,
                    VisualizationMode visualizationMode, String lastTrack, Language language) {
        this(libraryRoots, playlists, queue, darkTheme, volume, visualizationMode,
                lastTrack, language, "#1AA79B", true, 14);
    }

    public AppState(List<String> libraryRoots, Map<String, List<String>> playlists,
                    List<String> queue, boolean darkTheme, double volume,
                    VisualizationMode visualizationMode, String lastTrack, Language language,
                    String lyricHighlightColor, boolean autoDecodeNcm, int lyricsFontSize) {
        this(libraryRoots, playlists, queue, darkTheme, volume, visualizationMode, lastTrack,
                language, lyricHighlightColor, autoDecodeNcm, lyricsFontSize,
                EqualizerSettings.defaults(), List.of());
    }

    public AppState(List<String> libraryRoots, Map<String, List<String>> playlists,
                    List<String> queue, boolean darkTheme, double volume,
                    VisualizationMode visualizationMode, String lastTrack, Language language,
                    String lyricHighlightColor, boolean autoDecodeNcm, int lyricsFontSize,
                    EqualizerSettings equalizerSettings) {
        this(libraryRoots, playlists, queue, darkTheme, volume, visualizationMode, lastTrack,
                language, lyricHighlightColor, autoDecodeNcm, lyricsFontSize,
                equalizerSettings, List.of());
    }

    public AppState(List<String> libraryRoots, Map<String, List<String>> playlists,
                    List<String> queue, boolean darkTheme, double volume,
                    VisualizationMode visualizationMode, String lastTrack, Language language,
                    String lyricHighlightColor, boolean autoDecodeNcm, int lyricsFontSize,
                    EqualizerSettings equalizerSettings, List<String> discoveredFiles) {
        this.version = 1;
        this.libraryRoots = new ArrayList<>(libraryRoots);
        this.playlists = new LinkedHashMap<>();
        playlists.forEach((name, tracks) -> this.playlists.put(name, new ArrayList<>(tracks)));
        this.queue = new ArrayList<>(queue);
        this.lastTrack = lastTrack;
        this.darkTheme = darkTheme;
        this.volume = Math.max(0, Math.min(1, volume));
        this.visualizationMode = visualizationMode == null
                ? VisualizationMode.WAVEFORM : visualizationMode;
        this.language = language == null ? Language.CHINESE : language;
        this.lyricHighlightColor = normalizeColor(lyricHighlightColor);
        this.autoDecodeNcm = autoDecodeNcm;
        this.lyricsFontSize = Math.max(11, Math.min(24, lyricsFontSize));
        this.equalizerSettings = equalizerSettings == null
                ? EqualizerSettings.defaults() : equalizerSettings.copy();
        this.discoveredFiles = discoveredFiles == null ? List.of() : List.copyOf(discoveredFiles);
    }

    public static AppState empty() {
        return new AppState(List.of(), Map.of(), List.of(), false, 0.8);
    }

    public int getVersion() {
        return version;
    }

    public List<String> getLibraryRoots() {
        return List.copyOf(libraryRoots);
    }

    public Map<String, List<String>> getPlaylists() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        playlists.forEach((name, tracks) -> copy.put(name, List.copyOf(tracks)));
        return copy;
    }

    public List<String> getQueue() {
        return List.copyOf(queue);
    }

    public String getLastTrack() {
        return lastTrack;
    }

    public boolean isDarkTheme() {
        return darkTheme;
    }

    public double getVolume() {
        return volume;
    }

    public VisualizationMode getVisualizationMode() {
        return visualizationMode == null ? VisualizationMode.WAVEFORM : visualizationMode;
    }

    public Language getLanguage() {
        return language == null ? Language.CHINESE : language;
    }

    public String getLyricHighlightColor() {
        return normalizeColor(lyricHighlightColor);
    }

    public boolean isAutoDecodeNcm() {
        return autoDecodeNcm == null || autoDecodeNcm;
    }

    public int getLyricsFontSize() {
        return lyricsFontSize >= 11 && lyricsFontSize <= 24 ? lyricsFontSize : 14;
    }

    public EqualizerSettings getEqualizerSettings() {
        return equalizerSettings == null ? EqualizerSettings.defaults() : equalizerSettings.copy();
    }

    public List<String> getDiscoveredFiles() {
        return discoveredFiles == null ? List.of() : List.copyOf(discoveredFiles);
    }

    private static String normalizeColor(String value) {
        if (value != null && value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            return value.toUpperCase();
        }
        return "#1AA79B";
    }
}
