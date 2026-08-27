package club.muimi.kimusic.service.playback;

import club.muimi.kimusic.model.EqualizerSettings;

import java.nio.file.Path;

public interface PlaybackEngine extends AutoCloseable {
    void open(Path track, PlaybackListener listener, double volume, boolean autoPlay) throws Exception;

    void play();

    void pause();

    void seek(double seconds);

    void setVolume(double volume);

    default void setEqualizer(EqualizerSettings settings) {
        // Backends without PCM access may ignore the software equalizer.
    }

    @Override
    void close();
}
