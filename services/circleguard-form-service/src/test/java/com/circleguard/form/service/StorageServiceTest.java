package com.circleguard.form.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class StorageServiceTest {

    private final StorageService service = new StorageService();

    @Test
    void storedFileKeepsItsNameAfterAUniquePrefix() throws IOException {
        var file = new MockMultipartFile("file", "certificate.pdf", "application/pdf", "PDF".getBytes());

        String filename = service.store(file);

        Path stored = Path.of("/tmp/circleguard-uploads", filename);
        try {
            assertThat(filename).endsWith("_certificate.pdf");
            assertThat(Files.readString(stored)).isEqualTo("PDF");
        } finally {
            Files.deleteIfExists(stored);
        }
    }

    @Test
    void unreadableUploadIsReportedAsAStorageError() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getOriginalFilename()).thenReturn("broken.pdf");
        when(broken.getInputStream()).thenThrow(new IOException("stream closed"));

        assertThatThrownBy(() -> service.store(broken))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("stream closed");
    }
}
