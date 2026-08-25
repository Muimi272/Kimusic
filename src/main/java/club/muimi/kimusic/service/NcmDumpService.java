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
    private static final String VERSION = "1.5.1";
    private static final String WINDOWS_RESOURCE =
            "/club/muimi/kimusic/tools/windows-x64/ncmdump.exe";
    private static final String WINDOWS_SHA256 =
            "a1f6f6ce87500b7b1f2a89dbf85b13e81d327eea4641daf8afe0ab840f2c518c";
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(3);
    private static final List<String> OUTPUT_EXTENSIONS = List.of(
            "mp3", "flac", "ogg", "wav", "aac", "m4a");

    private final Path cacheDirectory;
    private final Path toolsDirectory;

    public NcmDumpService() {
        this(Path.of(System.getProperty("user.home"), ".kimusic"));
    }

    public NcmDumpService(Path appDirectory) {
        cacheDirectory = appDirectory.resolve("cache").resolve("ncm");
        toolsDirectory = appDirectory.resolve("tools");
    }

    public Path decode(Path source) throws IOException, InterruptedException {
        Path normalized = source.toAbsolutePath().normalize();
        validateNcm(normalized);
        Path outputDirectory = cacheDirectory.resolve(cacheKey(normalized));
        Path cached = findDecodedAudio(outputDirectory);
        if (cached != null) {
            return cached;
        }
        Files.createDirectories(outputDirectory);

        List<String> command = List.of(resolveExecutable(), normalized.toString(),
                "-o", outputDirectory.toString());
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean completed = process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new IOException("ncmdump timed out after " + PROCESS_TIMEOUT.toSeconds() + " seconds");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IOException("ncmdump failed with exit " + process.exitValue() + ": " + output.strip());
        }
        Path decoded = findDecodedAudio(outputDirectory);
        if (decoded == null) {
            throw new IOException("ncmdump did not produce playable audio (exit "
                    + process.exitValue() + "): " + output.strip());
        }
        return decoded;
    }

    private String resolveExecutable() throws IOException {
        String configured = System.getProperty("kimusic.ncmdump.path");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("KIMUSIC_NCMDUMP");
        }
        if (configured != null && !configured.isBlank()) {
            Path executable = Path.of(configured).toAbsolutePath().normalize();
            if (!Files.isRegularFile(executable)) {
                throw new IOException("Configured ncmdump executable does not exist: " + executable);
            }
            return executable.toString();
        }

        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (operatingSystem.contains("win") && (architecture.contains("amd64") || architecture.contains("x86_64"))) {
            return extractBundledWindowsExecutable().toString();
        }
        return "ncmdump";
    }

    private synchronized Path extractBundledWindowsExecutable() throws IOException {
        Path executable = toolsDirectory.resolve("ncmdump-" + VERSION + ".exe");
        if (Files.isRegularFile(executable) && WINDOWS_SHA256.equals(sha256(executable))) {
            return executable;
        }
        Files.createDirectories(toolsDirectory);
        Path temporary = executable.resolveSibling(executable.getFileName() + ".tmp");
        try (InputStream input = NcmDumpService.class.getResourceAsStream(WINDOWS_RESOURCE)) {
            if (input == null) {
                throw new IOException("Bundled ncmdump executable is missing");
            }
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!WINDOWS_SHA256.equals(sha256(temporary))) {
            Files.deleteIfExists(temporary);
            throw new IOException("Bundled ncmdump checksum mismatch");
        }
        Files.move(temporary, executable, StandardCopyOption.REPLACE_EXISTING);
        return executable;
    }

    private Path findDecodedAudio(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(this::hasSupportedExtension)
                    .filter(this::hasValidAudioHeader)
                    .findFirst()
                    .orElse(null);
        }
    }

    private boolean hasSupportedExtension(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return OUTPUT_EXTENSIONS.stream().anyMatch(extension -> fileName.endsWith("." + extension));
    }

    private boolean hasValidAudioHeader(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] header = input.readNBytes(12);
            return startsWith(header, "ID3".getBytes(StandardCharsets.US_ASCII))
                    || header.length >= 2 && (header[0] & 0xff) == 0xff && (header[1] & 0xe0) == 0xe0
                    || startsWith(header, "fLaC".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(header, "OggS".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(header, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    || header.length >= 8 && new String(header, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")
                    || header.length >= 2 && (header[0] & 0xff) == 0xff && (header[1] & 0xf6) == 0xf0;
        } catch (IOException exception) {
            return false;
        }
    }

    private void validateNcm(Path source) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException("NCM file does not exist: " + source);
        }
        try (InputStream input = Files.newInputStream(source)) {
            byte[] magic = input.readNBytes(8);
            if (!startsWith(magic, new byte[]{0x43, 0x54, 0x45, 0x4e, 0x46, 0x44, 0x41, 0x4d})) {
                throw new IOException("Invalid NCM container header");
            }
        }
    }

    private String cacheKey(Path source) throws IOException {
        String identity = source + "\n" + Files.size(source) + "\n"
                + Files.getLastModifiedTime(source).toMillis();
        return sha256(identity.getBytes(StandardCharsets.UTF_8)).substring(0, 24);
    }

    private String sha256(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[32_768];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
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
}
