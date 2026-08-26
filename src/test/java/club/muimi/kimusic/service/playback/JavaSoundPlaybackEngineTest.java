package club.muimi.kimusic.service.playback;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JavaSoundPlaybackEngineTest {
    @Test
    void coalescesRapidSeekRequestsToTheLatestPosition() {
        JavaSoundPlaybackEngine engine = new JavaSoundPlaybackEngine();

        engine.seek(1.25);
        engine.seek(8.5);
        engine.seek(4.75);

        assertEquals(4.75, engine.takeRequestedSeek());
        assertEquals(-1, engine.takeRequestedSeek());
    }

    @Test
    void convertsDecodedFlacPcmWithoutChangingStereoSamples() {
        JavaSoundPlaybackEngine engine = new JavaSoundPlaybackEngine();
        byte[] decoded = {0x34, 0x12, (byte) 0xcc, (byte) 0xed,
                0x00, 0x40, 0x00, (byte) 0xc0};
        byte[] output = new byte[decoded.length];

        int length = engine.convertFlacToPcm16(decoded, decoded.length, 16, 2, 2, output);

        assertEquals(decoded.length, length);
        assertArrayEquals(decoded, output);
    }

    @Test
    void seeksWithinARealFlacStreamWithoutEndingPlayback() throws Exception {
        String configuredFile = System.getProperty("kimusic.flac.test");
        assumeTrue(configuredFile != null && Files.isRegularFile(Path.of(configuredFile)));
        JavaSoundPlaybackEngine engine = new JavaSoundPlaybackEngine();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Double> duration = new AtomicReference<>(0.0);
        AtomicReference<Double> target = new AtomicReference<>(Double.POSITIVE_INFINITY);
        AtomicBoolean seekRequested = new AtomicBoolean();
        AtomicBoolean resumedAfterSeek = new AtomicBoolean();

        engine.open(Path.of(configuredFile), new PlaybackListener() {
            @Override
            public void onReady(double seconds) {
                duration.set(seconds);
            }

            @Override
            public void onProgress(double seconds, VisualizationFrame visualization) {
                if (seconds > 0.05 && seekRequested.compareAndSet(false, true)) {
                    double seekTarget = duration.get() * 0.65;
                    target.set(seekTarget);
                    engine.seek(seekTarget);
                } else if (seekRequested.get() && seconds >= target.get()) {
                    resumedAfterSeek.set(true);
                    completed.countDown();
                }
            }

            @Override public void onPlayingChanged(boolean playing) { }
            @Override public void onArtwork(javafx.scene.image.Image image) { }
            @Override public void onEnd() { completed.countDown(); }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                completed.countDown();
            }
        }, 0.01, true);

        assertTrue(completed.await(15, TimeUnit.SECONDS), "FLAC seek did not resume in time");
        engine.close();
        assertNull(error.get(), () -> "FLAC seek failed: " + error.get());
        assertTrue(seekRequested.get(), "the probe ended before issuing a seek");
        assertTrue(resumedAfterSeek.get(), "the FLAC stream ended instead of resuming after seek");
    }
}
