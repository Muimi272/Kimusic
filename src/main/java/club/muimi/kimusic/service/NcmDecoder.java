package club.muimi.kimusic.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.datatype.Artwork;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

final class NcmDecoder {
    static final byte[] MAGIC = {0x43, 0x54, 0x45, 0x4e, 0x46, 0x44, 0x41, 0x4d};

    private static final byte[] CORE_KEY = {
            0x68, 0x7a, 0x48, 0x52, 0x41, 0x6d, 0x73, 0x6f,
            0x35, 0x6b, 0x49, 0x6e, 0x62, 0x61, 0x78, 0x57};
    private static final byte[] METADATA_KEY = {
            0x23, 0x31, 0x34, 0x6c, 0x6a, 0x6b, 0x5f, 0x21,
            0x5c, 0x5d, 0x26, 0x30, 0x55, 0x3c, 0x27, 0x28};
    private static final byte[] KEY_PREFIX = "neteasecloudmusic".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] METADATA_PREFIX =
            "163 key(Don't modify):".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] METADATA_JSON_PREFIX = "music:".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final int MAX_ENCRYPTED_KEY_LENGTH = 1024 * 1024;
    private static final int MAX_ENCRYPTED_METADATA_LENGTH = 16 * 1024 * 1024;
    private static final int MAX_COVER_DATA_LENGTH = 64 * 1024 * 1024;
    private static final int AUDIO_BUFFER_SIZE = 32 * 1024;

    static {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
    }

    // Container key derivation is adapted from qaralotte/ncmdump (MIT).
    void decode(Path source, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Path stagingDirectory = Files.createTempDirectory(outputDirectory, ".decode-");
        Path rawTemporary = Files.createTempFile(stagingDirectory, "decoded-", ".tmp");
        Path typedTemporary = null;
        try {
            DecodedContent decoded = decryptTo(source, rawTemporary);
            typedTemporary = Files.createTempFile(stagingDirectory, "decoded-", "." + decoded.extension());
            moveIntoPlace(rawTemporary, typedTemporary);
            applyMetadata(typedTemporary, decoded);
            Path target = outputDirectory.resolve("decoded." + decoded.extension());
            moveIntoPlace(typedTemporary, target);
        } finally {
            Files.deleteIfExists(rawTemporary);
            if (typedTemporary != null) {
                Files.deleteIfExists(typedTemporary);
            }
            deleteDirectoryQuietly(stagingDirectory);
        }
    }

    private DecodedContent decryptTo(Path source, Path target) throws IOException {
        long fileSize = Files.size(source);
        try (FileChannel input = FileChannel.open(source, StandardOpenOption.READ)) {
            byte[] magic = readBytes(input, MAGIC.length, "NCM header");
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IOException("Invalid NCM container header");
            }

            skip(input, 2, fileSize, "NCM header padding");
            long encryptedKeyLength = readUnsignedInt(input, "key length");
            if (encryptedKeyLength == 0 || encryptedKeyLength > MAX_ENCRYPTED_KEY_LENGTH) {
                throw new IOException("Invalid NCM key length: " + encryptedKeyLength);
            }
            ensureAvailable(input, encryptedKeyLength, fileSize, "encrypted key");
            byte[] encryptedKey = readBytes(input, Math.toIntExact(encryptedKeyLength), "encrypted key");
            for (int index = 0; index < encryptedKey.length; index++) {
                encryptedKey[index] ^= 0x64;
            }
            byte[] keyBox = buildKeyBox(decryptKey(encryptedKey));

            NcmMetadata metadata = readMetadata(input, fileSize);
            skip(input, 5, fileSize, "CRC and cover version");

            long coverFrameLength = readUnsignedInt(input, "cover frame length");
            long coverDataLength = readUnsignedInt(input, "cover data length");
            if (coverDataLength > coverFrameLength) {
                throw new IOException("Invalid NCM cover lengths: " + coverDataLength
                        + " exceeds frame " + coverFrameLength);
            }
            if (coverDataLength > MAX_COVER_DATA_LENGTH) {
                throw new IOException("NCM cover is too large: " + coverDataLength);
            }
            ensureAvailable(input, coverFrameLength, fileSize, "cover frame");
            byte[] coverData = readBytes(input, Math.toIntExact(coverDataLength), "cover data");
            skip(input, coverFrameLength - coverDataLength, fileSize, "cover frame padding");
            if (input.position() >= fileSize) {
                throw new IOException("NCM container has no encrypted audio data");
            }

            String extension = decryptAudio(input, target, keyBox);
            return new DecodedContent(extension, metadata, coverData);
        }
    }

    private NcmMetadata readMetadata(FileChannel input, long fileSize) throws IOException {
        long metadataLength = readUnsignedInt(input, "metadata length");
        if (metadataLength == 0) {
            return NcmMetadata.EMPTY;
        }
        if (metadataLength > MAX_ENCRYPTED_METADATA_LENGTH) {
            throw new IOException("NCM metadata is too large: " + metadataLength);
        }
        ensureAvailable(input, metadataLength, fileSize, "metadata");
        byte[] metadata = readBytes(input, Math.toIntExact(metadataLength), "metadata");
        for (int index = 0; index < metadata.length; index++) {
            metadata[index] ^= 0x63;
        }
        if (!startsWith(metadata, metadata.length, METADATA_PREFIX)) {
            throw new IOException("Invalid NCM metadata prefix");
        }

        try {
            byte[] encryptedJson = Base64.getDecoder().decode(
                    Arrays.copyOfRange(metadata, METADATA_PREFIX.length, metadata.length));
            byte[] decryptedJson = decryptAes(encryptedJson, METADATA_KEY, "metadata");
            if (!startsWith(decryptedJson, decryptedJson.length, METADATA_JSON_PREFIX)) {
                throw new IOException("Invalid NCM metadata JSON prefix");
            }
            String json = new String(decryptedJson, METADATA_JSON_PREFIX.length,
                    decryptedJson.length - METADATA_JSON_PREFIX.length, StandardCharsets.UTF_8);
            return parseMetadata(json);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid NCM metadata encoding", exception);
        }
    }

    private NcmMetadata parseMetadata(String json) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            List<String> artists = new ArrayList<>();
            JsonArray artistValues = root.getAsJsonArray("artist");
            if (artistValues != null) {
                for (JsonElement value : artistValues) {
                    if (value.isJsonArray() && value.getAsJsonArray().size() > 0) {
                        artists.add(value.getAsJsonArray().get(0).getAsString());
                    }
                }
            }
            return new NcmMetadata(
                    getString(root, "musicName"),
                    List.copyOf(artists),
                    getString(root, "album"));
        } catch (RuntimeException exception) {
            throw new IOException("Invalid NCM metadata JSON", exception);
        }
    }

    private String getString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private byte[] decryptKey(byte[] encryptedKey) throws IOException {
        byte[] decrypted = decryptAes(encryptedKey, CORE_KEY, "encrypted key");
        if (decrypted.length <= KEY_PREFIX.length
                || !Arrays.equals(KEY_PREFIX, Arrays.copyOf(decrypted, KEY_PREFIX.length))) {
            throw new IOException("Invalid NCM decrypted key prefix");
        }
        return Arrays.copyOfRange(decrypted, KEY_PREFIX.length, decrypted.length);
    }

    private byte[] decryptAes(byte[] encrypted, byte[] key, String field) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(encrypted);
        } catch (BadPaddingException | IllegalBlockSizeException exception) {
            throw new IOException("Invalid NCM " + field, exception);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException exception) {
            throw new IllegalStateException("Required AES cipher is unavailable", exception);
        } catch (GeneralSecurityException exception) {
            throw new IOException("Unable to initialize NCM key decryption", exception);
        }
    }

    private void applyMetadata(Path audio, DecodedContent decoded) throws IOException {
        if ((decoded.metadata().isEmpty() && decoded.coverData().length == 0)
                || !List.of("mp3", "flac", "m4a").contains(decoded.extension())) {
            return;
        }
        try {
            AudioFile audioFile = AudioFileIO.read(audio.toFile());
            Tag tag = audioFile.getTagOrCreateAndSetDefault();
            NcmMetadata metadata = decoded.metadata();
            if (!metadata.title().isBlank()) {
                tag.setField(FieldKey.TITLE, metadata.title());
            }
            if (!metadata.artists().isEmpty()) {
                tag.setField(FieldKey.ARTIST, String.join("/", metadata.artists()));
            }
            if (!metadata.album().isBlank()) {
                tag.setField(FieldKey.ALBUM, metadata.album());
            }
            if (decoded.coverData().length > 0) {
                Artwork artwork = new Artwork();
                artwork.setBinaryData(decoded.coverData());
                artwork.setMimeType(startsWith(decoded.coverData(), decoded.coverData().length, PNG_MAGIC)
                        ? "image/png" : "image/jpeg");
                artwork.setDescription("");
                artwork.setLinked(false);
                artwork.setPictureType(3);
                tag.deleteArtworkField();
                tag.setField(artwork);
            }
            audioFile.commit();
        } catch (Exception exception) {
            throw new IOException("Unable to write decoded NCM metadata", exception);
        }
    }

    private byte[] buildKeyBox(byte[] key) throws IOException {
        if (key.length == 0) {
            throw new IOException("NCM decrypted key is empty");
        }
        byte[] box = new byte[256];
        for (int index = 0; index < box.length; index++) {
            box[index] = (byte) index;
        }
        int previous = 0;
        int keyOffset = 0;
        for (int index = 0; index < box.length; index++) {
            int current = Byte.toUnsignedInt(box[index]);
            int swapIndex = (current + previous + Byte.toUnsignedInt(key[keyOffset])) & 0xff;
            box[index] = box[swapIndex];
            box[swapIndex] = (byte) current;
            previous = swapIndex;
            keyOffset = (keyOffset + 1) % key.length;
        }
        return box;
    }

    private String decryptAudio(FileChannel input, Path target, byte[] keyBox) throws IOException {
        byte[] streamKey = buildStreamKey(keyBox);
        ByteBuffer encrypted = ByteBuffer.allocate(AUDIO_BUFFER_SIZE);
        long audioOffset = 0;
        String extension = null;

        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) {
            while (input.read(encrypted) >= 0) {
                int length = encrypted.position();
                if (length == 0) {
                    encrypted.clear();
                    continue;
                }
                byte[] buffer = encrypted.array();
                for (int index = 0; index < length; index++) {
                    buffer[index] ^= streamKey[(int) ((audioOffset + index) & 0xff)];
                }
                if (audioOffset == 0) {
                    extension = detectAudioExtension(buffer, length);
                    if (extension == null) {
                        throw new IOException("NCM container produced an unsupported audio header");
                    }
                }
                output.write(buffer, 0, length);
                audioOffset += length;
                encrypted.clear();
            }
        }

        if (audioOffset == 0) {
            throw new IOException("NCM container produced empty audio data");
        }
        return extension;
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

    static String detectAudioExtension(byte[] header, int length) {
        if (startsWith(header, length, "ID3".getBytes(StandardCharsets.US_ASCII))) {
            return "mp3";
        }
        if (isMpegAudioFrame(header, length)) {
            return "mp3";
        }
        if (startsWith(header, length, "fLaC".getBytes(StandardCharsets.US_ASCII))) {
            return "flac";
        }
        if (startsWith(header, length, "OggS".getBytes(StandardCharsets.US_ASCII))) {
            return "ogg";
        }
        if (startsWith(header, length, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && length >= 12 && matchesAt(header, length, 8, "WAVE")) {
            return "wav";
        }
        if (length >= 8 && matchesAt(header, length, 4, "ftyp")) {
            return "m4a";
        }
        if (length >= 2 && (header[0] & 0xff) == 0xff && (header[1] & 0xf6) == 0xf0) {
            return "aac";
        }
        return null;
    }

    private static boolean isMpegAudioFrame(byte[] header, int length) {
        if (length < 2 || (header[0] & 0xff) != 0xff || (header[1] & 0xe0) != 0xe0) {
            return false;
        }
        int version = (header[1] >>> 3) & 0x03;
        int layer = (header[1] >>> 1) & 0x03;
        return version != 0x01 && layer != 0;
    }

    private static boolean startsWith(byte[] value, int length, byte[] prefix) {
        if (length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAt(byte[] value, int length, int offset, String expected) {
        byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (length < offset + bytes.length) {
            return false;
        }
        for (int index = 0; index < bytes.length; index++) {
            if (value[offset + index] != bytes[index]) {
                return false;
            }
        }
        return true;
    }

    private long readUnsignedInt(FileChannel input, String field) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(readBytes(input, Integer.BYTES, field))
                .order(ByteOrder.LITTLE_ENDIAN);
        return Integer.toUnsignedLong(buffer.getInt());
    }

    private byte[] readBytes(FileChannel input, int length, String field) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            if (input.read(buffer) < 0) {
                throw new IOException("Truncated NCM " + field);
            }
        }
        return buffer.array();
    }

    private void skip(FileChannel input, long length, long fileSize, String field) throws IOException {
        ensureAvailable(input, length, fileSize, field);
        input.position(input.position() + length);
    }

    private void ensureAvailable(FileChannel input, long length, long fileSize, String field) throws IOException {
        if (length < 0 || length > fileSize - input.position()) {
            throw new IOException("Invalid NCM " + field + " length: " + length);
        }
    }

    private void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
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

    private record DecodedContent(String extension, NcmMetadata metadata, byte[] coverData) {
    }

    private record NcmMetadata(String title, List<String> artists, String album) {
        private static final NcmMetadata EMPTY = new NcmMetadata("", List.of(), "");

        private boolean isEmpty() {
            return title.isBlank() && artists.isEmpty() && album.isBlank();
        }
    }
}
