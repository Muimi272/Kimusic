package club.muimi.kimusic.service.playback;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class PlaybackEngines {
    private static final Set<String> JAVA_SOUND_EXTENSIONS = Set.of(
            "mp3", "flac", "ogg", "wav", "aiff", "aif"
    );

    private PlaybackEngines() {
    }

    public static PlaybackEngine create(Path track) {
        return create(preferredBackend(track));
    }

    public static PlaybackEngine create(Backend backend) {
        return backend == Backend.JAVA_SOUND
                ? new JavaSoundPlaybackEngine()
                : new JavaFxPlaybackEngine();
    }

    public static Backend preferredBackend(Path track) {
        String name = track.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return JAVA_SOUND_EXTENSIONS.contains(extension) ? Backend.JAVA_SOUND : Backend.JAVA_FX;
    }

    public enum Backend {
        JAVA_SOUND,
        JAVA_FX
    }
}
