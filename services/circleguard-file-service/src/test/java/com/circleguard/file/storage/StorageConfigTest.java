package com.circleguard.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageConfigTest {

    @TempDir
    Path dir;

    private final StorageConfig config = new StorageConfig();

    @Test
    void withoutBucketFilesStayInTheLocalDirectory() throws IOException {
        StorageBackend backend = config.storageBackend("", "us-east-1", "uploads/", dir.toString());

        assertThat(backend).isInstanceOf(LocalStorageBackend.class);
        backend.put("a.txt", new ByteArrayInputStream("x".getBytes()), 1, "text/plain");
        assertThat(dir.resolve("a.txt")).hasContent("x");
    }

    @Test
    void withBucketFilesGoToS3() throws Exception {
        StorageBackend backend = config.storageBackend("files-bucket", "us-east-1", "uploads/", dir.toString());

        assertThat(backend).isInstanceOf(S3StorageBackend.class);
        ((S3StorageBackend) backend).close();
    }

    @Test
    void localDirectoryThatCannotBeCreatedFailsAtStartup() throws IOException {
        Path file = Files.writeString(dir.resolve("not-a-dir"), "x");

        assertThatThrownBy(() -> new LocalStorageBackend(file.resolve("uploads")))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessage("Could not initialize storage");
    }

    @Test
    void storedFileWithoutTypeIsBinary() {
        assertThat(new StoredFile(new byte[0], null).contentType()).isEqualTo(StoredFile.DEFAULT_CONTENT_TYPE);
        assertThat(new StoredFile(new byte[0], " ").contentType()).isEqualTo(StoredFile.DEFAULT_CONTENT_TYPE);
    }
}
