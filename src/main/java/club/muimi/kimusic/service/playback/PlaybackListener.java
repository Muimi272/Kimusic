package club.muimi.kimusic.service.playback;

import javafx.scene.image.Image;

public interface PlaybackListener {
    void onReady(double durationSeconds);

    void onProgress(double currentSeconds, VisualizationFrame visualization);

    void onPlayingChanged(boolean playing);

    void onArtwork(Image image);

    void onEnd();

    void onError(Throwable error);
}
