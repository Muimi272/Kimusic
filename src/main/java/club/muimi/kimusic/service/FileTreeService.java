package club.muimi.kimusic.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class FileTreeService {
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "aac", "aiff", "aif", "flac", "m4a", "mp3", "ncm", "ogg", "wav"
    );

    private final LinkedHashSet<Path> roots = new LinkedHashSet<>();
    private final LinkedHashSet<Path> discoveredFiles = new LinkedHashSet<>();

    private FileTreeService() {
    }

    public static FileTreeService init() {
        return new FileTreeService();
    }

    public synchronized boolean addRootFile(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        if (file.isFile() && !isAudioFile(file.toPath())) {
            return false;
        }
        Path candidate = normalize(file.toPath());
        if (roots.stream().anyMatch(root -> candidate.startsWith(root))) {
            return false;
        }
        roots.removeIf(root -> root.startsWith(candidate));
        return roots.add(candidate);
    }

    public synchronized boolean removeRootFile(File file) {
        return file != null && roots.remove(normalize(file.toPath()));
    }

    public synchronized List<File> getRootFiles() {
        return roots.stream().map(Path::toFile).toList();
    }

    /** Returns folder roots plus files discovered by the device scan. */
    public synchronized List<File> getLibraryEntries() {
        List<File> entries = new ArrayList<>(roots.stream().map(Path::toFile).toList());
        entries.addAll(discoveredFiles.stream().map(Path::toFile).toList());
        return List.copyOf(entries);
    }

    public synchronized boolean removeLibraryEntry(File file) {
        if (file == null) return false;
        Path path = normalize(file.toPath());
        return roots.remove(path) || discoveredFiles.remove(path);
    }

    public synchronized List<String> snapshot() {
        return roots.stream().map(Path::toString).toList();
    }

    public synchronized List<String> snapshotDiscoveredFiles() {
        return discoveredFiles.stream().map(Path::toString).toList();
    }

    public synchronized void restore(List<String> paths) {
        roots.clear();
        if (paths != null) {
            paths.stream().map(Path::of).filter(Files::exists).forEach(path -> addRootFile(path.toFile()));
        }
    }

    public synchronized void restoreDiscoveredFiles(List<String> paths) {
        discoveredFiles.clear();
        if (paths != null) {
            paths.stream().map(Path::of).filter(Files::isRegularFile)
                    .filter(FileTreeService::isAudioFile)
                    .map(FileTreeService::normalize)
                    .filter(path -> roots.stream().noneMatch(path::startsWith))
                    .forEach(discoveredFiles::add);
        }
    }

    public synchronized int addDiscoveredFiles(List<Path> paths) {
        if (paths == null) {
            return 0;
        }
        int before = discoveredFiles.size();
        paths.stream().filter(Files::isRegularFile)
                .filter(FileTreeService::isAudioFile)
                .map(FileTreeService::normalize)
                .filter(path -> roots.stream().noneMatch(path::startsWith))
                .forEach(discoveredFiles::add);
        return discoveredFiles.size() - before;
    }

    public List<Path> scanAudioFiles() {
        List<Path> result = new ArrayList<>();
        for (File root : getRootFiles()) {
            Path path = root.toPath();
            if (Files.isRegularFile(path)) {
                if (isAudioFile(path)) {
                    result.add(normalize(path));
                }
                continue;
            }
            try (Stream<Path> stream = Files.walk(path)) {
                stream.filter(Files::isRegularFile)
                        .filter(FileTreeService::isAudioFile)
                        .map(FileTreeService::normalize)
                        .forEach(result::add);
            } catch (IOException ignored) {
                // An unreadable directory should not hide the rest of the library.
            }
        }
        synchronized (this) {
            result.addAll(discoveredFiles);
        }
        return result.stream().distinct()
                .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                .toList();
    }

    public static boolean isAudioFile(Path path) {
        if (path == null) {
            return false;
        }
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && AUDIO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public static Set<String> supportedExtensions() {
        return AUDIO_EXTENSIONS;
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
