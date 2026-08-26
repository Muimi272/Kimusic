package club.muimi.kimusic;

import club.muimi.kimusic.model.AppState;
import club.muimi.kimusic.service.FileTreeService;
import club.muimi.kimusic.service.LyricsService;
import club.muimi.kimusic.service.MusicService;
import club.muimi.kimusic.service.NoticeService;
import club.muimi.kimusic.service.PlaylistService;
import club.muimi.kimusic.util.DataSerializer;
import club.muimi.kimusic.status.VisualizationMode;
import club.muimi.kimusic.status.Language;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

public final class AppContext {
    private final NoticeService notices = NoticeService.init();
    private final FileTreeService library = FileTreeService.init();
    private final PlaylistService playlists = new PlaylistService();
    private final LyricsService lyrics = new LyricsService();
    private final MusicService music = MusicService.init(notices);
    private final DataSerializer serializer = DataSerializer.init(notices);
    private boolean darkTheme;
    private VisualizationMode visualizationMode;
    private Language language;
    private String lyricHighlightColor;
    private boolean autoDecodeNcm;
    private int lyricsFontSize;
    private final Path startupTrack;

    public AppContext() {
        AppState state = serializer.load();
        library.restore(state.getLibraryRoots());
        playlists.restore(state.getPlaylists());
        List<Path> queue = state.getQueue().stream().map(Path::of)
                .filter(Files::isRegularFile).toList();
        Path savedTrack = null;
        if (state.getLastTrack() != null) {
            try {
                savedTrack = Path.of(state.getLastTrack());
            } catch (InvalidPathException ignored) {
                // Ignore a stale or malformed last-played path.
            }
        }
        startupTrack = savedTrack != null && Files.isRegularFile(savedTrack)
                ? savedTrack.toAbsolutePath().normalize() : null;
        music.setQueue(queue, startupTrack != null ? startupTrack : (queue.isEmpty() ? null : queue.getFirst()));
        music.volumeProperty().set(state.getVolume());
        darkTheme = state.isDarkTheme();
        visualizationMode = state.getVisualizationMode();
        language = state.getLanguage();
        lyricHighlightColor = state.getLyricHighlightColor();
        autoDecodeNcm = state.isAutoDecodeNcm();
        lyricsFontSize = state.getLyricsFontSize();
        music.setLanguage(language);
        music.setAutoDecodeNcm(autoDecodeNcm);
    }

    public void save() {
        List<String> queue = music.getQueue().stream().map(Path::toString).toList();
        Path current = music.currentTrackProperty().get();
        serializer.save(new AppState(library.snapshot(), playlists.snapshot(), queue,
                darkTheme, music.volumeProperty().get(), visualizationMode,
                current == null ? null : current.toString(), language,
                lyricHighlightColor, autoDecodeNcm, lyricsFontSize));
    }

    public void close() {
        save();
        music.dispose();
    }

    public Path startupTrack() {
        return startupTrack;
    }

    public NoticeService notices() {
        return notices;
    }

    public FileTreeService library() {
        return library;
    }

    public PlaylistService playlists() {
        return playlists;
    }

    public LyricsService lyrics() {
        return lyrics;
    }

    public MusicService music() {
        return music;
    }

    public boolean isDarkTheme() {
        return darkTheme;
    }

    public void setDarkTheme(boolean darkTheme) {
        this.darkTheme = darkTheme;
    }

    public VisualizationMode getVisualizationMode() {
        return visualizationMode;
    }

    public void setVisualizationMode(VisualizationMode visualizationMode) {
        this.visualizationMode = visualizationMode == null
                ? VisualizationMode.WAVEFORM : visualizationMode;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language == null ? Language.CHINESE : language;
        music.setLanguage(this.language);
    }

    public String getLyricHighlightColor() {
        return lyricHighlightColor;
    }

    public void setLyricHighlightColor(String lyricHighlightColor) {
        if (lyricHighlightColor != null
                && lyricHighlightColor.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            this.lyricHighlightColor = lyricHighlightColor.toUpperCase();
        }
    }

    public boolean isAutoDecodeNcm() {
        return autoDecodeNcm;
    }

    public void setAutoDecodeNcm(boolean autoDecodeNcm) {
        this.autoDecodeNcm = autoDecodeNcm;
        music.setAutoDecodeNcm(autoDecodeNcm);
    }

    public int getLyricsFontSize() {
        return lyricsFontSize;
    }

    public void setLyricsFontSize(int lyricsFontSize) {
        this.lyricsFontSize = Math.max(11, Math.min(24, lyricsFontSize));
    }
}
