package club.muimi.kimusic.service.playback;

import java.nio.file.Path;

public interface PlaybackEngine extends AutoCloseable {
    void open(Path track, PlaybackListener listener, double volume, boolean autoPlay) throws Exception;

    void play();

    void pause();

    void seek(double seconds);

    void setVolume(double volume);

    @Override
    void close();
}
