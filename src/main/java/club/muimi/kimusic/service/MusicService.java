package club.muimi.kimusic.service;

import club.muimi.kimusic.service.playback.PlaybackEngine;
import club.muimi.kimusic.service.playback.PlaybackEngines;
import club.muimi.kimusic.service.playback.PlaybackListener;
import club.muimi.kimusic.service.playback.VisualizationFrame;
import club.muimi.kimusic.model.EqualizerSettings;
import club.muimi.kimusic.status.PlayMode;
import club.muimi.kimusic.status.Language;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public final class MusicService {
    private final NoticeService noticeService;
    private final Function<PlaybackEngines.Backend, PlaybackEngine> engineFactory;
    private final ArtworkService artworkService = new ArtworkService();
    private final NcmDumpService ncmDumpService = new NcmDumpService();
    private final ObservableList<Path> queue = FXCollections.observableArrayList();
    private final IntegerProperty currentIndex = new SimpleIntegerProperty(-1);
    private final ObjectProperty<Path> currentTrack = new SimpleObjectProperty<>();
    private final ObjectProperty<PlayMode> playMode = new SimpleObjectProperty<>(PlayMode.SEQUENTIAL);
    private final BooleanProperty playing = new SimpleBooleanProperty(false);
    private final DoubleProperty currentSeconds = new SimpleDoubleProperty(0);
    private final DoubleProperty durationSeconds = new SimpleDoubleProperty(0);
    private final DoubleProperty volume = new SimpleDoubleProperty(0.8);
    private final ObjectProperty<EqualizerSettings> equalizerSettings =
            new SimpleObjectProperty<>(EqualizerSettings.defaults());
    private final ObjectProperty<Image> artwork = new SimpleObjectProperty<>();
    private final ObjectProperty<VisualizationFrame> visualization =
            new SimpleObjectProperty<>(VisualizationFrame.empty());

    private PlaybackEngine engine;
    private PlaybackEngine outgoingEngine;
    private PlaybackEngines.Backend backend;
    private PauseTransition loadingTimeout;
    private Timeline volumeFade;
    private long playbackSession;
    private boolean fallbackAttempted;
    private boolean engineReady;
    private boolean playbackEnded;
    private boolean autoDecodeNcm = true;
    private boolean pausePending;
    private Language language = Language.CHINESE;

    private MusicService(NoticeService noticeService) {
        this(noticeService, PlaybackEngines::create);
    }

    MusicService(NoticeService noticeService,
                 Function<PlaybackEngines.Backend, PlaybackEngine> engineFactory) {
        this.noticeService = noticeService;
        this.engineFactory = engineFactory;
        volume.addListener((observable, oldValue, newValue) -> {
            double bounded = Math.max(0, Math.min(1, newValue.doubleValue()));
            if (bounded != newValue.doubleValue()) {
                volume.set(bounded);
            } else if (engine != null) {
                if (volumeFade != null) {
                    volumeFade.stop();
                    volumeFade = null;
                }
                closeOutgoingEngine();
                engine.setVolume(bounded);
            }
        });
        equalizerSettings.addListener((observable, oldValue, newValue) -> {
            EqualizerSettings settings = newValue == null
                    ? EqualizerSettings.defaults() : newValue.copy();
            if (newValue == null) {
                equalizerSettings.set(settings);
                return;
            }
            if (engine != null) {
                engine.setEqualizer(settings);
            }
            if (outgoingEngine != null) {
                outgoingEngine.setEqualizer(settings);
            }
        });
    }

    public static MusicService init(NoticeService noticeService) {
        return new MusicService(noticeService);
    }

    public void setQueue(List<Path> tracks, Path selectedTrack) {
        List<Path> playable = tracks.stream()
                .filter(path -> path != null && Files.isRegularFile(path))
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .toList();
        queue.setAll(playable);
        int selected = selectedTrack == null ? -1 : queue.indexOf(selectedTrack.toAbsolutePath().normalize());
        currentIndex.set(selected >= 0 ? selected : (queue.isEmpty() ? -1 : 0));
    }

    public void open(Path track) {
        if (track == null || !Files.isRegularFile(track)) {
            noticeService.addError(language == Language.ENGLISH
                    ? "The selected audio file no longer exists." : "所选音频文件已不存在。");
            return;
        }
        Path normalized = track.toAbsolutePath().normalize();
        int index = queue.indexOf(normalized);
        if (index < 0) {
            queue.setAll(normalized);
            index = 0;
        }
        openAt(index, true);
    }

    public void togglePlayback() {
        if (playbackEnded && currentIndex.get() >= 0) {
            openAt(currentIndex.get(), true);
            return;
        }
        if (engine == null) {
            if (currentIndex.get() < 0 && !queue.isEmpty()) {
                currentIndex.set(0);
            }
            if (currentIndex.get() >= 0) {
                openAt(currentIndex.get(), true);
            } else {
                noticeService.addWarning(language == Language.ENGLISH
                        ? "Import some music first." : "请先导入音乐。");
            }
            return;
        }
        if (pausePending) {
            pausePending = false;
            if (volumeFade != null) {
                volumeFade.stop();
                volumeFade = null;
            }
            fadeEngine(engine, 0, volume.get(), 90, null);
        } else if (playing.get()) {
            PlaybackEngine active = engine;
            pausePending = true;
            fadeEngine(active, volume.get(), 0, 90, () -> {
                if (active == engine) {
                    pausePending = false;
                    active.pause();
                    active.setVolume(volume.get());
                }
            });
        } else {
            engine.setVolume(0);
            engine.play();
        }
    }

    public void next() {
        advance(false);
    }

    public void previous() {
        if (queue.isEmpty()) {
            return;
        }
        if (engine != null && currentSeconds.get() > 3) {
            seekToSeconds(0);
            return;
        }
        int target = currentIndex.get() - 1;
        if (target < 0) {
            target = playMode.get() == PlayMode.REPEAT_ALL ? queue.size() - 1 : 0;
        }
        openAt(target, true);
    }

    public void playQueueIndex(int index) {
        openAt(index, true);
    }

    public void removeQueueIndex(int index) {
        if (index < 0 || index >= queue.size()) {
            return;
        }
        boolean removingCurrent = index == currentIndex.get();
        queue.remove(index);
        if (queue.isEmpty()) {
            disposeEngine();
            currentIndex.set(-1);
            currentTrack.set(null);
        } else if (removingCurrent) {
            openAt(Math.min(index, queue.size() - 1), true);
        } else if (index < currentIndex.get()) {
            currentIndex.set(currentIndex.get() - 1);
        }
    }

    public void seekToFraction(double fraction) {
        if (engine != null && durationSeconds.get() > 0) {
            seekToSeconds(durationSeconds.get() * Math.max(0, Math.min(1, fraction)));
        }
    }

    public void seekToSeconds(double seconds) {
        if (engine == null) {
            return;
        }
        double target = Math.max(0, durationSeconds.get() > 0
                ? Math.min(durationSeconds.get(), seconds) : seconds);
        currentSeconds.set(target);
        engine.seek(target);
    }

    public void dispose() {
        playbackSession++;
        disposeEngine();
    }

    public ObservableList<Path> getQueue() {
        return FXCollections.unmodifiableObservableList(queue);
    }

    public ReadOnlyIntegerProperty currentIndexProperty() {
        return currentIndex;
    }

    public ReadOnlyObjectProperty<Path> currentTrackProperty() {
        return currentTrack;
    }

    public ReadOnlyBooleanProperty playingProperty() {
        return playing;
    }

    public ReadOnlyDoubleProperty currentSecondsProperty() {
        return currentSeconds;
    }

    public ReadOnlyDoubleProperty durationSecondsProperty() {
        return durationSeconds;
    }

    public ReadOnlyObjectProperty<Image> artworkProperty() {
        return artwork;
    }

    public ReadOnlyObjectProperty<VisualizationFrame> visualizationProperty() {
        return visualization;
    }

    public ObjectProperty<PlayMode> playModeProperty() {
        return playMode;
    }

    public DoubleProperty volumeProperty() {
        return volume;
    }

    public ObjectProperty<EqualizerSettings> equalizerSettingsProperty() {
        return equalizerSettings;
    }

    public EqualizerSettings getEqualizerSettings() {
        return equalizerSettings.get().copy();
    }

    public void setEqualizerSettings(EqualizerSettings settings) {
        equalizerSettings.set(settings == null ? EqualizerSettings.defaults() : settings.copy());
    }

    public void setLanguage(Language language) {
        this.language = language == null ? Language.CHINESE : language;
    }

    public void setAutoDecodeNcm(boolean autoDecodeNcm) {
        this.autoDecodeNcm = autoDecodeNcm;
    }

    public boolean isAutoDecodeNcm() {
        return autoDecodeNcm;
    }

    private void openAt(int index, boolean autoPlay) {
        if (index < 0 || index >= queue.size()) {
            return;
        }
        playbackSession++;
        long session = playbackSession;
        prepareEngineSwitch(autoPlay);
        Path track = queue.get(index);
        currentIndex.set(index);
        currentTrack.set(track);
        currentSeconds.set(0);
        durationSeconds.set(0);
        visualization.set(VisualizationFrame.empty());
        fallbackAttempted = false;
        playbackEnded = false;
        if (isNcm(track)) {
            if (autoDecodeNcm) {
                prepareNcm(track, autoPlay, session);
            } else {
                playing.set(false);
                closeOutgoingEngine();
                noticeService.addWarning(language == Language.ENGLISH
                        ? "Automatic NCM decoding is disabled in Playback settings."
                        : "播放设置中已关闭自动解码 NCM。");
            }
        } else {
            loadArtwork(track, session);
            startEngine(track, track, autoPlay, session, PlaybackEngines.preferredBackend(track));
        }
    }

    private void prepareNcm(Path source, boolean autoPlay, long session) {
        Thread.ofVirtual().name("kimusic-ncmdump").start(() -> {
            try {
                Path decoded = ncmDumpService.decode(source);
                runForSession(session, () -> {
                    loadArtwork(decoded, session);
                    startEngine(decoded, source, autoPlay, session,
                            PlaybackEngines.preferredBackend(decoded));
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                runForSession(session, () -> {
                    playing.set(false);
                    closeOutgoingEngine();
                    currentTrack.set(null);
                    currentSeconds.set(0);
                    durationSeconds.set(0);
                    artwork.set(null);
                    noticeService.addError(language == Language.ENGLISH
                            ? "Unable to decode \"" + source.getFileName() + "\". Check that the file is intact."
                            : "无法解码「" + source.getFileName() + "」，请确认文件完整。");
                });
            }
        });
    }

    private void startEngine(Path playbackTrack, Path displayTrack, boolean autoPlay, long session,
                             PlaybackEngines.Backend selectedBackend) {
        backend = selectedBackend;
        engineReady = false;
        engine = engineFactory.apply(selectedBackend);
        PlaybackEngine activeEngine = engine;
        try {
            activeEngine.setEqualizer(equalizerSettings.get());
            activeEngine.open(playbackTrack, listenerFor(
                            session, displayTrack, playbackTrack, autoPlay, activeEngine),
                    autoPlay ? 0 : volume.get(), autoPlay);
            startLoadingTimeout(session, displayTrack, playbackTrack, autoPlay);
        } catch (Throwable error) {
            handlePlaybackError(session, displayTrack, playbackTrack, autoPlay, error);
        }
    }

    private PlaybackListener listenerFor(long session, Path displayTrack, Path playbackTrack,
                                         boolean autoPlay, PlaybackEngine activeEngine) {
        return new PlaybackListener() {
            @Override
            public void onReady(double duration) {
                runForSession(session, () -> {
                    engineReady = true;
                    stopLoadingTimeout();
                    durationSeconds.set(Math.max(0, duration));
                });
            }

            @Override
            public void onProgress(double seconds, VisualizationFrame frame) {
                runForSession(session, () -> {
                    currentSeconds.set(Math.max(0, seconds));
                    visualization.set(frame);
                });
            }

            @Override
            public void onPlayingChanged(boolean value) {
                runForSession(session, () -> {
                    if (engine != activeEngine) {
                        return;
                    }
                    if (!value) {
                        pausePending = false;
                    }
                    playing.set(value);
                    if (value) {
                        if (outgoingEngine != null) {
                            crossfadeTo(activeEngine);
                        } else {
                            fadeEngine(activeEngine, 0, volume.get(), 110, null);
                        }
                    }
                });
            }

            @Override
            public void onArtwork(Image image) {
                runForSession(session, () -> artwork.set(image));
            }

            @Override
            public void onEnd() {
                runForSession(session, () -> {
                    playbackEnded = true;
                    advance(true);
                });
            }

            @Override
            public void onError(Throwable error) {
                runForSession(session, () -> handlePlaybackError(
                        session, displayTrack, playbackTrack, autoPlay, error));
            }
        };
    }

    private void handlePlaybackError(long session, Path displayTrack, Path playbackTrack,
                                     boolean autoPlay, Throwable error) {
        if (session != playbackSession) {
            return;
        }
        stopLoadingTimeout();
        if (backend == PlaybackEngines.Backend.JAVA_SOUND && !fallbackAttempted) {
            fallbackAttempted = true;
            if (volumeFade != null) {
                volumeFade.stop();
                volumeFade = null;
            }
            if (outgoingEngine != null) {
                outgoingEngine.setVolume(volume.get());
            }
            if (engine != null) {
                engine.close();
            }
            startEngine(playbackTrack, displayTrack, autoPlay, session, PlaybackEngines.Backend.JAVA_FX);
            return;
        }
        disposeEngine();
        currentTrack.set(null);
        currentSeconds.set(0);
        durationSeconds.set(0);
        artwork.set(null);
        noticeService.addError(language == Language.ENGLISH
                ? "Unable to play \"" + displayTrack.getFileName() + "\". The file may be damaged or unsupported."
                : "无法播放「" + displayTrack.getFileName() + "」，文件可能损坏或编码不受支持。");
    }

    private void crossfadeTo(PlaybackEngine activeEngine) {
        PlaybackEngine oldEngine = outgoingEngine;
        if (oldEngine == null) {
            fadeEngine(activeEngine, 0, volume.get(), 110, null);
            return;
        }
        if (volumeFade != null) {
            volumeFade.stop();
        }
        EngineVolumeProperty incoming = new EngineVolumeProperty(activeEngine, 0);
        EngineVolumeProperty outgoing = new EngineVolumeProperty(oldEngine, volume.get());
        volumeFade = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(incoming, 0), new KeyValue(outgoing, volume.get())),
                new KeyFrame(Duration.millis(180), event -> {
                    activeEngine.setVolume(volume.get());
                    oldEngine.close();
                    if (outgoingEngine == oldEngine) {
                        outgoingEngine = null;
                    }
                    volumeFade = null;
                }, new KeyValue(incoming, volume.get()), new KeyValue(outgoing, 0)));
        volumeFade.play();
    }

    private void fadeEngine(PlaybackEngine targetEngine, double from, double target,
                            double millis, Runnable after) {
        if (targetEngine == null) {
            return;
        }
        if (volumeFade != null) {
            volumeFade.stop();
        }
        EngineVolumeProperty fadingVolume = new EngineVolumeProperty(targetEngine, from);
        volumeFade = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(fadingVolume, from)),
                new KeyFrame(Duration.millis(millis), event -> {
                    targetEngine.setVolume(target);
                    volumeFade = null;
                    if (after != null) {
                        after.run();
                    }
                }, new KeyValue(fadingVolume, target)));
        volumeFade.play();
    }

    private static final class EngineVolumeProperty extends javafx.beans.property.SimpleDoubleProperty {
        private final PlaybackEngine targetEngine;

        private EngineVolumeProperty(PlaybackEngine targetEngine, double initial) {
            super(initial);
            this.targetEngine = targetEngine;
        }

        @Override
        protected void invalidated() {
            targetEngine.setVolume(get());
        }
    }

    private void startLoadingTimeout(long session, Path displayTrack, Path playbackTrack, boolean autoPlay) {
        stopLoadingTimeout();
        loadingTimeout = new PauseTransition(Duration.seconds(10));
        loadingTimeout.setOnFinished(event -> {
            if (session == playbackSession && !engineReady) {
                handlePlaybackError(session, displayTrack, playbackTrack, autoPlay,
                        new IllegalStateException("Audio backend timed out"));
            }
        });
        loadingTimeout.play();
    }

    private void advance(boolean automatic) {
        if (queue.isEmpty()) {
            return;
        }
        PlayMode mode = playMode.get();
        if (automatic && (mode == PlayMode.REPEAT_ONE
                || mode == PlayMode.SHUFFLE && queue.size() == 1)) {
            openAt(currentIndex.get(), true);
            return;
        }

        int target;
        if (mode == PlayMode.SHUFFLE && queue.size() > 1) {
            do {
                target = ThreadLocalRandom.current().nextInt(queue.size());
            } while (target == currentIndex.get());
        } else {
            target = currentIndex.get() + 1;
            if (target >= queue.size()) {
                if (mode == PlayMode.REPEAT_ALL) {
                    target = 0;
                } else {
                    playing.set(false);
                    return;
                }
            }
        }
        openAt(target, true);
    }

    private void runForSession(long session, Runnable action) {
        Runnable guarded = () -> {
            if (session == playbackSession) {
                action.run();
            }
        };
        if (Platform.isFxApplicationThread()) {
            guarded.run();
        } else {
            Platform.runLater(guarded);
        }
    }

    private void loadArtwork(Path track, long session) {
        Thread.ofVirtual().name("kimusic-artwork-loader").start(() -> {
            Image image = artworkService.load(track).orElse(null);
            runForSession(session, () -> artwork.set(image));
        });
    }

    private void prepareEngineSwitch(boolean autoPlay) {
        stopLoadingTimeout();
        pausePending = false;
        if (volumeFade != null) {
            volumeFade.stop();
            volumeFade = null;
        }
        closeOutgoingEngine();
        PlaybackEngine previous = engine;
        engine = null;
        if (previous != null && autoPlay && playing.get()) {
            outgoingEngine = previous;
            outgoingEngine.setVolume(volume.get());
        } else if (previous != null) {
            previous.close();
            playing.set(false);
        }
        engineReady = false;
    }

    private void closeOutgoingEngine() {
        if (outgoingEngine != null) {
            outgoingEngine.close();
            outgoingEngine = null;
        }
    }

    private void disposeEngine() {
        stopLoadingTimeout();
        pausePending = false;
        if (volumeFade != null) {
            volumeFade.stop();
            volumeFade = null;
        }
        if (engine != null) {
            engine.close();
            engine = null;
        }
        closeOutgoingEngine();
        playing.set(false);
        engineReady = false;
    }

    private void stopLoadingTimeout() {
        if (loadingTimeout != null) {
            loadingTimeout.stop();
            loadingTimeout = null;
        }
    }

    private boolean isNcm(Path track) {
        return track.getFileName().toString().toLowerCase().endsWith(".ncm");
    }
}
