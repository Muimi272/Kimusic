package club.muimi.kimusic.service;

import club.muimi.kimusic.service.playback.PlaybackEngine;
import club.muimi.kimusic.service.playback.PlaybackEngines;
import club.muimi.kimusic.service.playback.PlaybackListener;
import club.muimi.kimusic.service.playback.VisualizationFrame;
import club.muimi.kimusic.status.PlayMode;
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

public final class MusicService {
    private final NoticeService noticeService;
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
    private final ObjectProperty<Image> artwork = new SimpleObjectProperty<>();
    private final ObjectProperty<VisualizationFrame> visualization =
            new SimpleObjectProperty<>(VisualizationFrame.empty());

    private PlaybackEngine engine;
    private PlaybackEngines.Backend backend;
    private PauseTransition loadingTimeout;
    private Timeline volumeFade;
    private long playbackSession;
    private boolean fallbackAttempted;
    private boolean engineReady;
    private boolean playbackEnded;

    private MusicService(NoticeService noticeService) {
        this.noticeService = noticeService;
        volume.addListener((observable, oldValue, newValue) -> {
            double bounded = Math.max(0, Math.min(1, newValue.doubleValue()));
            if (bounded != newValue.doubleValue()) {
                volume.set(bounded);
            } else if (engine != null) {
                if (volumeFade != null) {
                    volumeFade.stop();
                    volumeFade = null;
                }
                engine.setVolume(bounded);
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
            noticeService.addNotice("所选音频文件已不存在。");
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
                noticeService.addNotice("请先导入音乐。");
            }
            return;
        }
        if (playing.get()) {
            engine.pause();
        } else {
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
            engine.seek(0);
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
            engine.seek(durationSeconds.get() * Math.max(0, Math.min(1, fraction)));
        }
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

    private void openAt(int index, boolean autoPlay) {
        if (index < 0 || index >= queue.size()) {
            return;
        }
        playbackSession++;
        long session = playbackSession;
        disposeEngine();
        Path track = queue.get(index);
        currentIndex.set(index);
        currentTrack.set(track);
        currentSeconds.set(0);
        durationSeconds.set(0);
        artwork.set(null);
        visualization.set(VisualizationFrame.empty());
        fallbackAttempted = false;
        playbackEnded = false;
        if (isNcm(track)) {
            prepareNcm(track, autoPlay, session);
        } else {
            loadArtwork(track, session);
            startEngine(track, track, autoPlay, session, PlaybackEngines.preferredBackend(track));
        }
    }

    private void prepareNcm(Path source, boolean autoPlay, long session) {
        noticeService.addNotice("正在解码「" + source.getFileName() + "」…");
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
                    currentTrack.set(null);
                    currentSeconds.set(0);
                    durationSeconds.set(0);
                    artwork.set(null);
                    noticeService.addNotice("无法解码「" + source.getFileName()
                            + "」，请确认文件完整且 ncmdump 可用。");
                });
            }
        });
    }

    private void startEngine(Path playbackTrack, Path displayTrack, boolean autoPlay, long session,
                             PlaybackEngines.Backend selectedBackend) {
        backend = selectedBackend;
        engineReady = false;
        engine = PlaybackEngines.create(selectedBackend);
        PlaybackEngine activeEngine = engine;
        try {
            activeEngine.open(playbackTrack, listenerFor(session, displayTrack, playbackTrack, autoPlay),
                    autoPlay ? 0 : volume.get(), autoPlay);
            startLoadingTimeout(session, displayTrack, playbackTrack, autoPlay);
        } catch (Throwable error) {
            handlePlaybackError(session, displayTrack, playbackTrack, autoPlay, error);
        }
    }

    private PlaybackListener listenerFor(long session, Path displayTrack, Path playbackTrack, boolean autoPlay) {
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
                    playing.set(value);
                    fadeVolume(value ? volume.get() : 0);
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
            if (engine != null) {
                engine.close();
            }
            startEngine(playbackTrack, displayTrack, autoPlay, session, PlaybackEngines.Backend.JAVA_FX);
            return;
        }
        playing.set(false);
        currentTrack.set(null);
        currentSeconds.set(0);
        durationSeconds.set(0);
        artwork.set(null);
        noticeService.addNotice("无法播放「" + displayTrack.getFileName() + "」，文件可能损坏或编码不受支持。");
    }

    private void fadeVolume(double target) {
        if (engine == null) {
            return;
        }
        if (volumeFade != null) {
            volumeFade.stop();
        }
        double current = target == 0 ? volume.get() : 0;
        VolumeProperty fadingVolume = new VolumeProperty(current);
        volumeFade = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(fadingVolume, current)),
                new KeyFrame(Duration.millis(120), event -> {
                    if (engine != null) {
                        engine.setVolume(target);
                    }
                    volumeFade = null;
                }, new KeyValue(fadingVolume, target)));
        volumeFade.play();
    }

    /** Adapter property used solely to interpolate a backend volume value. */
    private final class VolumeProperty extends javafx.beans.property.SimpleDoubleProperty {
        private VolumeProperty(double initial) {
            super(initial);
        }

        @Override
        protected void invalidated() {
            if (engine != null) {
                engine.setVolume(get());
            }
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
        Thread.ofVirtual().name("kimusic-artwork-loader").start(() ->
                artworkService.load(track).ifPresent(image ->
                        runForSession(session, () -> artwork.set(image))));
    }

    private void disposeEngine() {
        stopLoadingTimeout();
        if (volumeFade != null) {
            volumeFade.stop();
            volumeFade = null;
        }
        if (engine != null) {
            engine.close();
            engine = null;
        }
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
