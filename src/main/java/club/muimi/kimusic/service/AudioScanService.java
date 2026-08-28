package club.muimi.kimusic.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Finds user-facing audio files without descending into common system trees. */
public final class AudioScanService {
    private static final Set<String> EXCLUDED_DIRECTORY_NAMES = Set.of(
            "windows", "program files", "program files (x86)", "programdata", "appdata",
            "$recycle.bin", "system volume information", "recovery",
            "proc", "sys", "dev", "run", "usr", "bin", "sbin", "lib", "lib64",
            "boot", "var", "snap", "node_modules", ".git", "target",
            ".cache", ".thumbnails", "cache", "caches", "temp", "tmp");

    private AudioScanService() {
    }

    public static List<Path> defaultRoots() {
        File[] roots = File.listRoots();
        if (roots == null || roots.length == 0) {
            return List.of(Path.of(System.getProperty("user.home", ".")));
        }
        return java.util.Arrays.stream(roots).map(File::toPath).toList();
    }

    public static List<Path> scan(List<Path> roots, ProgressListener listener,
                                  BooleanSupplier cancelled) {
        ProgressListener progress = listener == null ? (visited, total, found) -> { } : listener;
        List<Path> normalizedRoots = normalizeRoots(roots);
        long total = countFiles(normalizedRoots, cancelled);
        progress.onProgress(0, total, 0);
        List<Path> result = new ArrayList<>();
        long[] visited = {0};
        for (Path root : normalizedRoots) {
            walk(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    checkCancelled(cancelled);
                    if (attrs.isRegularFile()) {
                        visited[0]++;
                        if (FileTreeService.isAudioFile(file)) {
                            result.add(file.toAbsolutePath().normalize());
                        }
                        progress.onProgress(visited[0], total, result.size());
                    }
                    return FileVisitResult.CONTINUE;
                }
            }, cancelled);
        }
        return result.stream().distinct()
                .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static long countFiles(List<Path> roots, BooleanSupplier cancelled) {
        long[] count = {0};
        for (Path root : roots) {
            walk(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    checkCancelled(cancelled);
                    if (attrs.isRegularFile()) count[0]++;
                    return FileVisitResult.CONTINUE;
                }
            }, cancelled);
        }
        return count[0];
    }

    private static void walk(Path root, SimpleFileVisitor<Path> visitor, BooleanSupplier cancelled) {
        checkCancelled(cancelled);
        if (root == null || !Files.exists(root)) return;
        try {
            Files.walkFileTree(root, visitorWithExclusions(root, visitor, cancelled));
        } catch (IOException | SecurityException ignored) {
            // Permission failures on one subtree should not abort the device scan.
        }
    }

    private static SimpleFileVisitor<Path> visitorWithExclusions(Path root,
                                                                  SimpleFileVisitor<Path> delegate,
                                                                  BooleanSupplier cancelled) {
        return new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                checkCancelled(cancelled);
                return !dir.equals(root) && shouldExclude(dir)
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    return delegate.visitFile(file, attrs);
                } catch (IOException exception) {
                    return FileVisitResult.CONTINUE;
                }
            }
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception) {
                checkCancelled(cancelled);
                return FileVisitResult.CONTINUE;
            }
        };
    }

    private static boolean shouldExclude(Path path) {
        Path name = path.getFileName();
        if (name == null) return false;
        String value = name.toString().toLowerCase(Locale.ROOT);
        return EXCLUDED_DIRECTORY_NAMES.contains(value);
    }

    private static List<Path> normalizeRoots(List<Path> roots) {
        Set<Path> unique = new HashSet<>();
        if (roots != null) for (Path root : roots) {
            if (root != null) unique.add(root.toAbsolutePath().normalize());
        }
        return unique.stream().sorted().toList();
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled != null && cancelled.getAsBoolean()) {
            throw new CancellationException("Audio scan cancelled");
        }
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(long visitedFiles, long totalFiles, int foundFiles);
    }
}
