package club.muimi.kimusic.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NcmDumpServiceTest {
    private static final byte[] CORE_KEY = {
            0x68, 0x7a, 0x48, 0x52, 0x41, 0x6d, 0x73, 0x6f,
            0x35, 0x6b, 0x49, 0x6e, 0x62, 0x61, 0x78, 0x57};

    @TempDir
    Path temporaryDirectory;

    @Test
    void decodesNcmWithBuiltInJavaDecoderAcrossBufferBoundaries() throws Exception {
        byte[] audio = new byte[70_000];
        byte[] header = "ID3\u0004\u0000\u0000\u0000\u0000\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(header, 0, audio, 0, header.length);
        for (int index = header.length; index < audio.length; index++) {
            audio[index] = (byte) (index * 31 + 7);
        }
        Path source = writeSyntheticNcm("streaming.ncm", audio);
        AtomicBoolean fallbackCalled = new AtomicBoolean();
        NcmDumpService service = new NcmDumpService(
                temporaryDirectory,
                new NcmDecoder()::decode,
                (ignoredSource, ignoredOutput) -> fallbackCalled.set(true));

        Path decoded = service.decode(source);

        assertEquals("mp3", extension(decoded));
        assertArrayEquals(audio, Files.readAllBytes(decoded));
        assertFalse(fallbackCalled.get(), "external fallback must not run after built-in success");
    }

    @Test
    void reusesValidatedDecodedAudioFromCache() throws Exception {
        Path source = writeSyntheticNcm("cached.ncm", playableMp3());
        AtomicInteger decodeCount = new AtomicInteger();
        NcmDumpService.Decoder decoder = (input, output) -> {
            decodeCount.incrementAndGet();
            new NcmDecoder().decode(input, output);
        };
        NcmDumpService service = new NcmDumpService(temporaryDirectory, decoder, null);

        Path first = service.decode(source);
        Path second = service.decode(source);

        assertEquals(first, second);
        assertEquals(1, decodeCount.get());
    }

    @Test
    void concurrentDecodesReturnTheSamePersistentCachePath() throws Exception {
        Path source = writeSyntheticNcm("concurrent.ncm", playableMp3());
        AtomicInteger decodeCount = new AtomicInteger();
        NcmDumpService.Decoder slowDecoder = (input, output) -> {
            decodeCount.incrementAndGet();
            Thread.sleep(20);
            new NcmDecoder().decode(input, output);
        };
        NcmDumpService service = new NcmDumpService(temporaryDirectory, slowDecoder, null);

        List<Path> results;
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(16)) {
            var tasks = java.util.stream.IntStream.range(0, 32)
                    .mapToObj(ignored -> (java.util.concurrent.Callable<Path>) () -> service.decode(source))
                    .toList();
            results = executor.invokeAll(tasks).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).toList();
        }

        assertEquals(1, new HashSet<>(results).size());
        assertEquals(1, decodeCount.get());
        assertEquals("decoded.mp3", results.getFirst().getFileName().toString());
        assertTrue(Files.isRegularFile(results.getFirst()));
    }

    @Test
    void invokesFallbackOnlyAfterBuiltInDecoderFails() throws Exception {
        Path source = writeSyntheticNcm("fallback.ncm", playableMp3());
        AtomicBoolean fallbackCalled = new AtomicBoolean();
        NcmDumpService.Decoder builtIn = (ignoredSource, ignoredOutput) -> {
            throw new IOException("built-in failure");
        };
        NcmDumpService.Decoder fallback = (ignoredSource, output) -> {
            fallbackCalled.set(true);
            Files.write(output.resolve("decoded.mp3"), playableMp3());
        };
        NcmDumpService service = new NcmDumpService(temporaryDirectory, builtIn, fallback);

        Path decoded = service.decode(source);

        assertTrue(fallbackCalled.get());
        assertEquals("decoded.mp3", decoded.getFileName().toString());
    }

    @Test
    void invokesFallbackWhenBuiltInDecoderRejectsAudioHeader() throws Exception {
        Path source = writeSyntheticNcm("unsupported-audio.ncm", new byte[128]);
        AtomicBoolean fallbackCalled = new AtomicBoolean();
        NcmDumpService service = new NcmDumpService(
                temporaryDirectory,
                new NcmDecoder()::decode,
                (ignoredSource, output) -> {
                    fallbackCalled.set(true);
                    Files.write(output.resolve("decoded.mp3"), playableMp3());
                });

        Path decoded = service.decode(source);

        assertTrue(fallbackCalled.get());
        assertEquals("decoded.mp3", decoded.getFileName().toString());
    }

    @Test
    void doesNotDetectAnAudioHeaderFromLaterChunk() throws Exception {
        byte[] audio = new byte[40_000];
        System.arraycopy("ID3".getBytes(StandardCharsets.US_ASCII), 0, audio, 32_768, 3);
        Path source = writeSyntheticNcm("late-header.ncm", audio);

        assertThrows(IOException.class,
                () -> new NcmDecoder().decode(source, temporaryDirectory.resolve("late-output")));
    }

    @Test
    void distinguishesMpegAudioFramesFromAacAdts() {
        assertEquals("mp3", NcmDecoder.detectAudioExtension(
                new byte[]{(byte) 0xff, (byte) 0xfb}, 2));
        assertEquals("mp3", NcmDecoder.detectAudioExtension(
                new byte[]{(byte) 0xff, (byte) 0xe3}, 2));
        assertEquals("aac", NcmDecoder.detectAudioExtension(
                new byte[]{(byte) 0xff, (byte) 0xf1}, 2));
    }

    @Test
    void preservesBuiltInFailureWhenFallbackAlsoFails() throws Exception {
        Path source = writeSyntheticNcm("double-failure.ncm", playableMp3());
        NcmDumpService service = new NcmDumpService(
                temporaryDirectory,
                (ignoredSource, ignoredOutput) -> {
                    throw new IOException("built-in failure");
                },
                (ignoredSource, ignoredOutput) -> {
                    throw new IOException("fallback failure");
                });

        IOException failure = assertThrows(IOException.class, () -> service.decode(source));

        assertEquals("fallback failure", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("built-in failure", failure.getSuppressed()[0].getMessage());
    }

    @Test
    void rejectsFilesWithoutNcmHeaderBeforeEitherDecoderRuns() throws Exception {
        Path invalid = temporaryDirectory.resolve("not-ncm.ncm");
        Files.write(invalid, new byte[]{'I', 'D', '3'});
        AtomicBoolean builtInCalled = new AtomicBoolean();
        AtomicBoolean fallbackCalled = new AtomicBoolean();
        NcmDumpService service = new NcmDumpService(
                temporaryDirectory,
                (ignoredSource, ignoredOutput) -> builtInCalled.set(true),
                (ignoredSource, ignoredOutput) -> fallbackCalled.set(true));

        assertThrows(IOException.class, () -> service.decode(invalid));
        assertFalse(builtInCalled.get());
        assertFalse(fallbackCalled.get());
    }

    @Test
    void removesTemporaryOutputWhenBuiltInContainerParsingFails() throws Exception {
        Path truncated = temporaryDirectory.resolve("truncated.ncm");
        Files.write(truncated, NcmDecoder.MAGIC);
        Path output = temporaryDirectory.resolve("direct-output");

        assertThrows(IOException.class, () -> new NcmDecoder().decode(truncated, output));

        try (var files = Files.list(output)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void decodesUpstreamSampleWhenAvailable() throws Exception {
        Path sample = Path.of(".tmp-ncmdump-upstream", "test", "test.ncm");
        assumeTrue(Files.isRegularFile(sample));

        Path appDirectory = temporaryDirectory.resolve("upstream-sample");
        Path decoded = new NcmDumpService(appDirectory).decode(sample);

        assertTrue(Files.size(decoded) > 0);
        byte[] header;
        try (var input = Files.newInputStream(decoded)) {
            header = input.readNBytes(12);
        }
        assertTrue(NcmDecoder.detectAudioExtension(header, header.length) != null);
        var tag = AudioFileIO.read(decoded.toFile()).getTag();
        assertEquals("贝贝", tag.getFirst(FieldKey.TITLE));
        assertEquals("李荣浩", tag.getFirst(FieldKey.ARTIST));
        assertEquals("耳朵", tag.getFirst(FieldKey.ALBUM));
        assertEquals(1, tag.getArtworkList().size());
    }

    @Test
    void runsConfiguredExternalProgramAfterBuiltInFailureWhenAvailable() throws Exception {
        Path sample = Path.of(".tmp-ncmdump-upstream", "test", "test.ncm");
        Path executable = Path.of(".tmp-ncmdump-release", "ncmdump.exe");
        assumeTrue(System.getProperty("os.name", "").startsWith("Windows"));
        assumeTrue(Files.isRegularFile(sample) && Files.isRegularFile(executable));
        NcmDumpService service = new NcmDumpService(
                temporaryDirectory.resolve("real-fallback"),
                (ignoredSource, ignoredOutput) -> {
                    throw new IOException("force external fallback");
                },
                NcmDumpService.externalDecoder(executable.toString()));

        Path decoded = service.decode(sample);

        assertEquals("flac", extension(decoded));
        assertTrue(Files.size(decoded) > 0);
        assertEquals("贝贝", AudioFileIO.read(decoded.toFile()).getTag().getFirst(FieldKey.TITLE));
        try (var cacheEntries = Files.walk(decoded.getParent())) {
            assertFalse(cacheEntries.anyMatch(path -> path.getFileName().toString()
                    .startsWith("ncmdump-fallback-")));
        }
    }

    private Path writeSyntheticNcm(String fileName, byte[] audio) throws Exception {
        byte[] audioKey = "kimusic-stream-test-key".getBytes(StandardCharsets.US_ASCII);
        byte[] keyPlaintext = concatenate(
                "neteasecloudmusic".getBytes(StandardCharsets.US_ASCII), audioKey);
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(CORE_KEY, "AES"));
        byte[] encryptedKey = cipher.doFinal(keyPlaintext);
        for (int index = 0; index < encryptedKey.length; index++) {
            encryptedKey[index] ^= 0x64;
        }

        byte[] encryptedAudio = Arrays.copyOf(audio, audio.length);
        byte[] streamKey = buildStreamKey(buildKeyBox(audioKey));
        for (int index = 0; index < encryptedAudio.length; index++) {
            encryptedAudio[index] ^= streamKey[index & 0xff];
        }

        ByteArrayOutputStream container = new ByteArrayOutputStream();
        container.write(NcmDecoder.MAGIC);
        container.write(new byte[2]);
        writeUnsignedInt(container, encryptedKey.length);
        container.write(encryptedKey);
        writeUnsignedInt(container, 0);
        container.write(new byte[5]);
        writeUnsignedInt(container, 0);
        writeUnsignedInt(container, 0);
        container.write(encryptedAudio);

        Path source = temporaryDirectory.resolve(fileName);
        Files.write(source, container.toByteArray());
        return source;
    }

    private byte[] buildKeyBox(byte[] key) {
        byte[] box = new byte[256];
        for (int index = 0; index < box.length; index++) {
            box[index] = (byte) index;
        }
        int previous = 0;
        for (int index = 0; index < box.length; index++) {
            int current = Byte.toUnsignedInt(box[index]);
            int swapIndex = (current + previous + Byte.toUnsignedInt(key[index % key.length])) & 0xff;
            box[index] = box[swapIndex];
            box[swapIndex] = (byte) current;
            previous = swapIndex;
        }
        return box;
    }

    private byte[] buildStreamKey(byte[] keyBox) {
        byte[] streamKey = new byte[256];
        for (int index = 0; index < streamKey.length; index++) {
            int offset = (index + 1) & 0xff;
            int first = Byte.toUnsignedInt(keyBox[offset]);
            int second = Byte.toUnsignedInt(keyBox[(first + offset) & 0xff]);
            streamKey[index] = keyBox[(first + second) & 0xff];
        }
        return streamKey;
    }

    private void writeUnsignedInt(ByteArrayOutputStream output, int value) {
        output.writeBytes(ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array());
    }

    private byte[] concatenate(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private byte[] playableMp3() {
        return "ID3\u0004\u0000\u0000\u0000\u0000\u0000\u0000test-audio"
                .getBytes(StandardCharsets.ISO_8859_1);
    }

    private String extension(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
