package com.circleguard.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.circleguard.file.storage.LocalStorageBackend;
import com.circleguard.file.storage.StorageBackend;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class FileStorageServiceTest {

    @TempDir
    Path root;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService(new LocalStorageBackend(root));
    }

    @Test
    void savedFileIsWrittenUnderAUniqueName() throws IOException {
        var file = new MockMultipartFile("file", "certificate.pdf", "application/pdf", "PDF".getBytes());

        String first = service.saveFile(file);
        String second = service.saveFile(file);

        assertThat(first).endsWith("_certificate.pdf").isNotEqualTo(second);
        assertThat(Files.readString(root.resolve(first))).isEqualTo("PDF");
    }

    @Test
    void savedFileIsReadBack() {
        var file = new MockMultipartFile("file", "certificate.pdf", "application/pdf", "PDF".getBytes());

        String name = service.saveFile(file);

        assertThat(service.loadFile(name)).hasValueSatisfying(stored ->
                assertThat(stored.content()).asString().isEqualTo("PDF"));
    }

    @Test
    void pathInTheOriginalNameNeverLeavesTheStorageDirectory() throws IOException {
        var file = new MockMultipartFile("file", "../../etc/passwd", "text/plain", "x".getBytes());

        String name = service.saveFile(file);

        assertThat(name).doesNotContain("/").endsWith("_passwd");
        assertThat(Files.list(root)).singleElement().isEqualTo(root.resolve(name));
    }

    @Test
    void namesThatSaveFileDidNotIssueAreNotRead() {
        assertThat(service.loadFile("../secret")).isEmpty();
        assertThat(service.loadFile("certificate.pdf")).isEmpty();
        assertThat(service.loadFile(null)).isEmpty();
    }

    @Test
    void missingFileIsEmpty() {
        assertThat(service.loadFile("123e4567-e89b-12d3-a456-426614174000_gone.pdf")).isEmpty();
    }

    @Test
    void unreadableUploadIsReportedAsAStorageError() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getOriginalFilename()).thenReturn("broken.pdf");
        when(broken.getInputStream()).thenThrow(new IOException("stream closed"));

        assertThatThrownBy(() -> service.saveFile(broken))
                .isInstanceOf(StorageException.class)
                .hasMessage("Could not store file")
                .hasRootCauseMessage("stream closed");
    }

    @Test
    void backendReadFailureIsReportedAsAStorageError() throws IOException {
        StorageBackend backend = mock(StorageBackend.class);
        when(backend.get(anyString())).thenThrow(new IOException("disk gone"));

        assertThatThrownBy(() -> new FileStorageService(backend)
                .loadFile("123e4567-e89b-12d3-a456-426614174000_a.pdf"))
                .isInstanceOf(StorageException.class)
                .hasMessage("Could not read file")
                .hasRootCauseMessage("disk gone");
    }

    @Test
    void backendWriteFailureIsReportedAsAStorageError() throws IOException {
        StorageBackend backend = mock(StorageBackend.class);
        doThrow(new IllegalStateException("bucket unreachable"))
                .when(backend).put(anyString(), any(), anyLong(), any());
        var file = new MockMultipartFile("file", "a.pdf", "application/pdf", "x".getBytes());

        assertThatThrownBy(() -> new FileStorageService(backend).saveFile(file))
                .isInstanceOf(StorageException.class)
                .hasRootCauseMessage("bucket unreachable");
    }

    @Test
    void safeNameKeepsOnlyTheLastSegmentAndSafeCharacters() {
        assertThat(FileStorageService.safeName("C:\\Users\\ana\\informe médico.pdf")).isEqualTo("informe_m_dico.pdf");
        assertThat(FileStorageService.safeName("...hidden")).isEqualTo("hidden");
        assertThat(FileStorageService.safeName("")).isEqualTo("file");
        assertThat(FileStorageService.safeName(null)).isEqualTo("file");
        assertThat(FileStorageService.safeName("a".repeat(150) + ".pdf"))
                .hasSize(FileStorageService.MAX_NAME_LENGTH).endsWith(".pdf");
    }
}
