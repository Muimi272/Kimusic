package club.muimi.kimusic.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class NcmDumpService {
    private static final String FALLBACK_ENVIRONMENT_VARIABLE = "KIMUSIC_NCMDUMP";
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(3);
    private static final List<String> OUTPUT_EXTENSIONS = List.of(
            "mp3", "flac", "ogg", "wav", "aac", "m4a");
    private static final Object[] DECODE_LOCKS = createDecodeLocks(256);

    private final Path cacheDirectory;
    private final Decoder builtInDecoder;
    private final Decoder fallbackDecoder;

    public NcmDumpService() {
        this(Path.of(System.getProperty("user.home"), ".kimusic"));
    }

    public NcmDumpService(Path appDirectory) {
        this(appDirectory, new NcmDecoder()::decode,
                externalDecoder(System.getenv(FALLBACK_ENVIRONMENT_VARIABLE)));
    }

    NcmDumpService(Path appDirectory, Decoder builtInDecoder, Decoder fallbackDecoder) {
        cacheDirectory = appDirectory.resolve("cache").resolve("ncm");
        this.builtInDecoder = builtInDecoder;
        this.fallbackDecoder = fallbackDecoder;
    }

    public Path decode(Path source) throws IOException, InterruptedException {
        Path normalized = source.toAbsolutePath().normalize();
        validateNcm(normalized);
        Path outputDirectory = cacheDirectory.resolve(cacheKey(normalized));
        Object lock = DECODE_LOCKS[Math.floorMod(outputDirectory.hashCode(), DECODE_LOCKS.length)];
        synchronized (lock) {
            return decodeLocked(normalized, outputDirectory);
        }
    }

    private Path decodeLocked(Path source, Path outputDirectory) throws IOException, InterruptedException {
        Path cached = findCachedAudio(outputDirectory);
        if (cached != null) {
            return cached;
        }
        Files.createDirectories(outputDirectory);

        IOException builtInFailure;
        try {
            return decodeWith(builtInDecoder, source, outputDirectory, "Built-in NCM decoder");
        } catch (IOException exception) {
            builtInFailure = exception;
        }

        if (fallbackDecoder == null) {
            throw builtInFailure;
        }
        try {
            return decodeWith(fallbackDecoder, source, outputDirectory, "External ncmdump fallback");
        } catch (IOException exception) {
            exception.addSuppressed(builtInFailure);
            throw exception;
        }
    }

    private static Object[] createDecodeLocks(int count) {
        Object[] locks = new Object[count];
        java.util.Arrays.setAll(locks, ignored -> new Object());
        return locks;
    }

    private Path decodeWith(Decoder decoder, Path source, Path outputDirectory, String name)
            throws IOException, InterruptedException {
        decoder.decode(source, outputDirectory);
        Path decoded = findCachedAudio(outputDirectory);
        if (decoded == null) {
            throw new IOException(name + " did not produce playable audio");
        }
        return decoded;
    }

    static Decoder externalDecoder(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String executableValue = configured.strip();
        return (source, outputDirectory) -> runExternalDecoder(executableValue, source, outputDirectory);
    }

    private static void runExternalDecoder(String configured, Path source, Path outputDirectory)
            throws IOException, InterruptedException {
        Path executable;
        try {
            executable = Path.of(configured).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            throw new IOException("Invalid " + FALLBACK_ENVIRONMENT_VARIABLE + " path: " + configured, exception);
        }
        if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            throw new IOException(FALLBACK_ENVIRONMENT_VARIABLE
                    + " does not point to an executable file: " + executable);
        }

        Path stagingDirectory = Files.createTempDirectory(outputDirectory, "ncmdump-fallback-");
        Path processLog = Files.createTempFile(stagingDirectory, "process-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(executable.toString(), source.toString(),
                    "-o", stagingDirectory.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(processLog.toFile())
                    .start();
            boolean completed = process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                terminateProcessTree(process);
                throw new IOException("External ncmdump fallback timed out after "
                        + PROCESS_TIMEOUT.toSeconds() + " seconds");
            }
            if (process.exitValue() != 0) {
                throw new IOException("External ncmdump fallback failed with exit " + process.exitValue()
                        + ": " + readProcessOutput(processLog));
            }
            Path decoded = findProducedAudio(stagingDirectory);
            if (decoded == null) {
                throw new IOException("External ncmdump fallback did not produce playable audio");
            }
            String extension = fileExtension(decoded);
            moveIntoPlace(decoded, outputDirectory.resolve("decoded." + extension));
        } catch (InterruptedException exception) {
            if (process != null) {
                terminateProcessTree(process);
            }
            throw exception;
        } finally {
            deleteDirectoryQuietly(stagingDirectory);
        }
    }

    private static void terminateProcessTree(Process process) {
        process.descendants().toList().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static String readProcessOutput(Path processLog) {
        try {
            return Files.readString(processLog, StandardCharsets.UTF_8).strip();
        } catch (IOException exception) {
            return "unable to read process output: " + exception.getMessage();
        }
    }

    private static Path findCachedAudio(Path directory) throws IOException {
        return findAudio(directory, true);
    }

    private static Path findProducedAudio(Path directory) throws IOException {
        return findAudio(directory, false);
    }

    private static Path findAudio(Path directory, boolean requireCanonicalName) throws IOException {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> !requireCanonicalName || path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).equals("decoded." + fileExtension(path)))
                    .filter(NcmDumpService::hasSupportedExtension)
                    .filter(NcmDumpService::hasValidAudioHeader)
                    .findFirst()
                    .orElse(null);
        }
    }

    private static boolean hasSupportedExtension(Path path) {
        return OUTPUT_EXTENSIONS.contains(fileExtension(path));
    }

    private static boolean hasValidAudioHeader(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] header = input.readNBytes(12);
            return fileExtension(path).equals(NcmDecoder.detectAudioExtension(header, header.length));
        } catch (IOException exception) {
            return false;
        }
    }

    private static String fileExtension(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int separator = fileName.lastIndexOf('.');
        return separator >= 0 ? fileName.substring(separator + 1) : "";
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteDirectoryQuietly(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    path.toFile().deleteOnExit();
                }
            });
        } catch (IOException ignored) {
            directory.toFile().deleteOnExit();
        }
    }

    private void validateNcm(Path source) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException("NCM file does not exist: " + source);
        }
        try (InputStream input = Files.newInputStream(source)) {
            byte[] magic = input.readNBytes(NcmDecoder.MAGIC.length);
            if (!startsWith(magic, NcmDecoder.MAGIC)) {
                throw new IOException("Invalid NCM container header");
            }
        }
    }

    private String cacheKey(Path source) throws IOException {
        String identity = source + "\n" + Files.size(source) + "\n"
                + Files.getLastModifiedTime(source).toMillis();
        return sha256(identity.getBytes(StandardCharsets.UTF_8)).substring(0, 24);
    }

    private String sha256(byte[] value) {
        return HexFormat.of().formatHex(sha256Digest().digest(value));
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    @FunctionalInterface
    interface Decoder {
        void decode(Path source, Path outputDirectory) throws IOException, InterruptedException;
    }
}
