package club.muimi.kimusic.service;

import javafx.scene.image.Image;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.datatype.Artwork;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ArtworkService {
    private static final List<String> SIDE_CAR_NAMES = List.of(
            "cover.jpg", "cover.jpeg", "cover.png",
            "folder.jpg", "folder.jpeg", "folder.png",
            "front.jpg", "front.jpeg", "front.png"
    );

    public Optional<Image> load(Path track) {
        Optional<Image> embedded = loadEmbedded(track);
        return embedded.isPresent() ? embedded : loadSideCar(track);
    }

    private Optional<Image> loadEmbedded(Path track) {
        try {
            AudioFile audioFile = AudioFileIO.read(track.toFile());
            Tag tag = audioFile.getTag();
            if (tag == null) {
                return Optional.empty();
            }
            Artwork artwork = tag.getFirstArtwork();
            byte[] data = artwork == null ? null : artwork.getBinaryData();
            if (data == null || data.length == 0) {
                return Optional.empty();
            }
            Image image = new Image(new ByteArrayInputStream(data), 900, 900, true, true);
            return image.isError() ? Optional.empty() : Optional.of(image);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private Optional<Image> loadSideCar(Path track) {
        Path directory = track.getParent();
        if (directory == null) {
            return Optional.empty();
        }
        try (var files = Files.list(directory)) {
            Optional<Path> imagePath = files.filter(Files::isRegularFile)
                    .filter(path -> SIDE_CAR_NAMES.contains(
                            path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .findFirst();
            if (imagePath.isEmpty()) {
                return Optional.empty();
            }
            try (var input = Files.newInputStream(imagePath.get())) {
                Image image = new Image(input, 900, 900, true, true);
                return image.isError() ? Optional.empty() : Optional.of(image);
            }
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}
