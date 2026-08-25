package club.muimi.kimusic.util;

import club.muimi.kimusic.model.AppState;
import club.muimi.kimusic.service.NoticeService;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class DataSerializer {
    private final NoticeService noticeService;
    private final Path stateFile;

    private DataSerializer(NoticeService noticeService, Path stateFile) {
        this.noticeService = noticeService;
        this.stateFile = stateFile;
    }

    public static DataSerializer init(NoticeService noticeService) {
        Path appDirectory = Path.of(System.getProperty("user.home"), ".kimusic");
        return new DataSerializer(noticeService, appDirectory.resolve("state.bin"));
    }

    public static DataSerializer init(NoticeService noticeService, Path stateFile) {
        return new DataSerializer(noticeService, stateFile);
    }

    public AppState load() {
        if (!Files.isRegularFile(stateFile)) {
            return AppState.empty();
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(stateFile)))) {
            Object value = input.readObject();
            if (value instanceof AppState state && state.getVersion() == 1) {
                return state;
            }
            noticeService.addNotice("The saved library uses an unsupported format.");
        } catch (IOException | ClassNotFoundException | RuntimeException exception) {
            noticeService.addNotice("The saved library could not be restored.");
        }
        return AppState.empty();
    }

    public boolean save(AppState state) {
        Path parent = stateFile.toAbsolutePath().getParent();
        Path temporary = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (ObjectOutputStream output = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.writeObject(state);
            }
            moveIntoPlace(temporary);
            return true;
        } catch (IOException exception) {
            noticeService.addNotice("The library state could not be saved.");
            return false;
        }
    }

    public Path getStateFile() {
        return stateFile;
    }

    private void moveIntoPlace(Path temporary) throws IOException {
        try {
            Files.move(temporary, stateFile, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
