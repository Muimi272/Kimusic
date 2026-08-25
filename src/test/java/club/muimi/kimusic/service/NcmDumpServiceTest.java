package club.muimi.kimusic.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class NcmDumpServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsFilesWithoutNcmContainerHeaderBeforeLaunchingTool() throws Exception {
        Path invalid = temporaryDirectory.resolve("not-ncm.ncm");
        Files.write(invalid, new byte[]{'I', 'D', '3'});

        assertThrows(IOException.class, () -> new NcmDumpService(temporaryDirectory).decode(invalid));
    }
}
