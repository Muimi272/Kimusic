package club.muimi.kimusic.service.playback;

import javafx.collections.MapChangeListener;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.nio.file.Path;

final class JavaFxPlaybackEngine implements PlaybackEngine {
    private MediaPlayer player;
    private boolean closed;

    @Override
    public void open(Path track, PlaybackListener listener, double volume, boolean autoPlay) {
        double maxFrequencyHz = AudioSampleRateProbe.nyquistHz(track);
        Media media = new Media(track.toUri().toString());
        media.getMetadata().addListener((MapChangeListener<String, Object>) change -> {
            if (!closed && "image".equals(change.getKey()) && change.getValueAdded() instanceof Image image) {
                listener.onArtwork(image);
            }
        });
        MediaPlayer created = new MediaPlayer(media);
        player = created;
        created.setVolume(volume);
        created.setAudioSpectrumNumBands(64);
        created.setAudioSpectrumInterval(0.05);
        created.setAudioSpectrumThreshold(-60);
        created.setAudioSpectrumListener((timestamp, duration, magnitudes, phases) -> {
            if (!closed && player == created) {
                float[] waveform = new float[magnitudes.length];
                float[] spectrum = new float[magnitudes.length];
                for (int index = 0; index < magnitudes.length; index++) {
                    double db = Math.max(-60, Math.min(0, magnitudes[index]));
                    spectrum[index] = (float) Math.pow(10, db / 20.0);
                    waveform[index] = (float) ((db + 60) / 60.0);
                }
                double minFrequencyHz = JavaSoundPlaybackEngine.lowestActiveFrequency(
                        spectrum, maxFrequencyHz, true);
                listener.onProgress(created.getCurrentTime().toSeconds(),
                        new VisualizationFrame(waveform, spectrum,
                                minFrequencyHz, maxFrequencyHz, true));
            }
        });
        created.setOnReady(() -> {
            if (closed || player != created) {
                return;
            }
            Object image = media.getMetadata().get("image");
            if (image instanceof Image artwork) {
                listener.onArtwork(artwork);
            }
            listener.onReady(created.getTotalDuration().toSeconds());
            if (autoPlay) {
                created.play();
            }
        });
        created.setOnPlaying(() -> listener.onPlayingChanged(true));
        created.setOnPaused(() -> listener.onPlayingChanged(false));
        created.setOnStopped(() -> listener.onPlayingChanged(false));
        created.setOnEndOfMedia(listener::onEnd);
        created.setOnError(() -> listener.onError(created.getError()));
        media.setOnError(() -> listener.onError(media.getError()));
    }

    @Override
    public void play() {
        if (player != null) {
            player.play();
        }
    }

    @Override
    public void pause() {
        if (player != null) {
            player.pause();
        }
    }

    @Override
    public void seek(double seconds) {
        if (player != null) {
            player.seek(Duration.seconds(Math.max(0, seconds)));
        }
    }

    @Override
    public void setVolume(double volume) {
        if (player != null) {
            player.setVolume(Math.max(0, Math.min(1, volume)));
        }
    }

    @Override
    public void close() {
        closed = true;
        if (player != null) {
            player.setAudioSpectrumListener(null);
            player.stop();
            player.dispose();
            player = null;
        }
    }
}
