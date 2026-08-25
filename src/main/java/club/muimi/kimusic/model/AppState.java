package club.muimi.kimusic.model;

import club.muimi.kimusic.status.VisualizationMode;

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
}
