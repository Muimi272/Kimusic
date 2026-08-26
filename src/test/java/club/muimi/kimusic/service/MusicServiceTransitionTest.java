package club.muimi.kimusic.service;

import club.muimi.kimusic.service.playback.PlaybackEngine;
import club.muimi.kimusic.service.playback.PlaybackListener;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicServiceTransitionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void keepsOldTrackUntilCrossfadeAndFadesBeforePause() throws Exception {
        Path firstTrack = Files.write(temporaryDirectory.resolve("first.wav"), new byte[]{0});
        Path secondTrack = Files.write(temporaryDirectory.resolve("second.wav"), new byte[]{0});
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Runnable testBody = () -> {
            MusicService service = null;
            try {
                List<FakePlaybackEngine> engines = new ArrayList<>();
                service = new MusicService(NoticeService.init(), backend -> {
                    FakePlaybackEngine engine = new FakePlaybackEngine();
                    engines.add(engine);
                    return engine;
                });
                service.setQueue(List.of(firstTrack, secondTrack), firstTrack);
                service.open(firstTrack);
                FakePlaybackEngine first = engines.getFirst();
                first.signalReadyAndPlaying();

                service.open(secondTrack);
                FakePlaybackEngine second = engines.get(1);
                assertFalse(first.closed, "the outgoing track must remain alive while the next track loads");
                second.signalReadyAndPlaying();

                MusicService activeService = service;
                PauseTransition afterCrossfade = new PauseTransition(Duration.millis(230));
                afterCrossfade.setOnFinished(event -> {
                    try {
                        assertTrue(first.closed, "the outgoing track should close after the crossfade");
                        assertEquals(0.8, second.volume, 0.01);
                        activeService.togglePlayback();
                        assertFalse(second.paused, "pause should wait for the short fade-out");
                        PauseTransition afterPauseFade = new PauseTransition(Duration.millis(120));
                        afterPauseFade.setOnFinished(ignored -> {
                            try {
                                assertTrue(second.paused, "the backend should pause after fading out");
                                assertEquals(0.8, second.volume, 0.01,
                                        "paused engines retain the configured volume for immediate resume");
                            } catch (Throwable throwable) {
                                failure.set(throwable);
                            } finally {
                                activeService.dispose();
                                completed.countDown();
                            }
                        });
                        afterPauseFade.play();
                    } catch (Throwable throwable) {
                        failure.set(throwable);
                        activeService.dispose();
                        completed.countDown();
                    }
                });
                afterCrossfade.play();
            } catch (Throwable throwable) {
                failure.set(throwable);
                if (service != null) {
                    service.dispose();
                }
                completed.countDown();
            }
        };
        try {
            Platform.startup(testBody);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(testBody);
        }

        assertTrue(completed.await(5, TimeUnit.SECONDS), "playback transition test timed out");
        if (failure.get() != null) {
            throw new AssertionError("playback transition validation failed", failure.get());
        }
    }

    @Test
    void disablingAutomaticNcmDecodeDoesNotStartAPlaybackEngine() throws Exception {
        Path ncm = Files.write(temporaryDirectory.resolve("locked.ncm"), new byte[]{0});
        NoticeService notices = NoticeService.init();
        List<FakePlaybackEngine> engines = new ArrayList<>();
        MusicService service = new MusicService(notices, backend -> {
            FakePlaybackEngine engine = new FakePlaybackEngine();
            engines.add(engine);
            return engine;
        });
        service.setQueue(List.of(ncm), ncm);
        service.setAutoDecodeNcm(false);

        service.open(ncm);

        assertTrue(engines.isEmpty());
        Notice notice = notices.takeNoticeEntry();
        assertNotNull(notice);
        assertEquals(NoticeLevel.WARNING, notice.level());
        service.dispose();
    }

    @Test
    void rebuildsPlaybackEngineWhenUserRetriesAfterFinalBackendFailure() throws Exception {
        Path brokenTrack = Files.write(temporaryDirectory.resolve("broken.wav"), new byte[]{0});
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Runnable testBody = () -> {
            MusicService service = null;
            try {
                List<FakePlaybackEngine> engines = new ArrayList<>();
                service = new MusicService(NoticeService.init(), backend -> {
                    FakePlaybackEngine engine = new FakePlaybackEngine();
                    engines.add(engine);
                    return engine;
                });
                service.setQueue(List.of(brokenTrack), brokenTrack);
                service.open(brokenTrack);

                FakePlaybackEngine preferred = engines.getFirst();
                preferred.signalError();
                FakePlaybackEngine fallback = engines.get(1);
                fallback.signalError();

                assertTrue(preferred.closed, "the failed preferred backend must be closed before fallback");
                assertTrue(fallback.closed, "the final failed backend must release its resources");
                assertNull(service.currentTrackProperty().get());

                service.togglePlayback();
                assertEquals(3, engines.size(), "retrying playback must create a fresh backend");
                assertFalse(engines.get(2).closed);
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                if (service != null) {
                    service.dispose();
                }
                completed.countDown();
            }
        };
        try {
            Platform.startup(testBody);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(testBody);
        }

        assertTrue(completed.await(5, TimeUnit.SECONDS), "playback retry test timed out");
        if (failure.get() != null) {
            throw new AssertionError("playback retry validation failed", failure.get());
        }
    }

    private static final class FakePlaybackEngine implements PlaybackEngine {
        private PlaybackListener listener;
        private boolean autoPlay;
        private boolean paused;
        private boolean closed;
        private double volume;

        @Override
        public void open(Path track, PlaybackListener listener, double initialVolume, boolean autoPlay) {
            this.listener = listener;
            this.autoPlay = autoPlay;
            volume = initialVolume;
        }

        void signalReadyAndPlaying() {
            listener.onReady(120);
            if (autoPlay) {
                listener.onPlayingChanged(true);
            }
        }

        void signalError() {
            listener.onError(new IllegalStateException("simulated playback failure"));
        }

        @Override public void play() { paused = false; listener.onPlayingChanged(true); }
        @Override public void pause() { paused = true; listener.onPlayingChanged(false); }
        @Override public void seek(double seconds) { }
        @Override public void setVolume(double value) { volume = value; }
        @Override public void close() { closed = true; }
    }
}
