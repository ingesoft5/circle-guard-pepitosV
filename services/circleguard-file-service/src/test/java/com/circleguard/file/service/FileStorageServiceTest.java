package com.circleguard.file.service;

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

class FileStorageServiceTest {

    private final FileStorageService service = new FileStorageService();

    @Test
    void savedFileIsWrittenUnderAUniqueName() throws IOException {
        var file = new MockMultipartFile("file", "certificate.pdf", "application/pdf", "PDF".getBytes());

        String first = service.saveFile(file);
        String second = service.saveFile(file);

        try {
            assertThat(first).endsWith("_certificate.pdf").isNotEqualTo(second);
            assertThat(Files.readString(Path.of("uploads", first))).isEqualTo("PDF");
        } finally {
            Files.deleteIfExists(Path.of("uploads", first));
            Files.deleteIfExists(Path.of("uploads", second));
        }
    }

    @Test
    void unreadableUploadIsReportedAsAStorageError() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getOriginalFilename()).thenReturn("broken.pdf");
        when(broken.getInputStream()).thenThrow(new IOException("stream closed"));

        assertThatThrownBy(() -> service.saveFile(broken))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Could not store file")
                .hasRootCauseMessage("stream closed");
    }

    @Test
    void loadingFilesIsNotImplementedYet() {
        assertThat(service.loadFile("anything")).isNull();
    }
}
