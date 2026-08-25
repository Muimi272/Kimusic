package club.muimi.kimusic.service.playback;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.spi.AudioFileReader;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackEnginesTest {
    @Test
    void routesCrossFormatFilesToJavaSound() {
        for (String extension : List.of("mp3", "flac", "ogg", "wav", "aiff")) {
            assertEquals(PlaybackEngines.Backend.JAVA_SOUND,
                    PlaybackEngines.preferredBackend(Path.of("track." + extension)));
        }
        assertEquals(PlaybackEngines.Backend.JAVA_FX,
                PlaybackEngines.preferredBackend(Path.of("track.m4a")));
        assertEquals(PlaybackEngines.Backend.JAVA_FX,
                PlaybackEngines.preferredBackend(Path.of("track.ncm")));
    }

    @Test
    void decoderProvidersAreVisibleAtRuntime() {
        List<String> providers = ServiceLoader.load(AudioFileReader.class).stream()
                .map(provider -> provider.type().getName().toLowerCase())
                .toList();

        assertTrue(providers.stream().anyMatch(name -> name.contains("mp3spi")), providers::toString);
        assertTrue(providers.stream().anyMatch(name -> name.contains("vorbis")), providers::toString);
        assertTrue(providers.stream().anyMatch(name -> name.contains("jflac")), providers::toString);
    }
}
