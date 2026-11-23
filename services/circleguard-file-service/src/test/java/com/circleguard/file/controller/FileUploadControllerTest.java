package com.circleguard.file.controller;

import com.circleguard.file.service.FileStorageService;
import com.circleguard.file.storage.StoredFile;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileUploadController.class)
class FileUploadControllerTest {

    private static final String NAME = "123e4567-e89b-12d3-a456-426614174000_certificate.pdf";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileStorageService storageService;

    @Test
    void shouldUploadFileSuccessfully() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "certificate.pdf", "application/pdf", "mock content".getBytes());

        Mockito.when(storageService.saveFile(Mockito.any())).thenReturn("certificate.pdf");

        mockMvc.perform(multipart("/api/v1/files/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("certificate.pdf"));
    }

    @Test
    void storedFileIsDownloadedAsAnAttachment() throws Exception {
        Mockito.when(storageService.loadFile(NAME))
                .thenReturn(Optional.of(new StoredFile("PDF".getBytes(), "application/pdf")));

        mockMvc.perform(get("/api/v1/files/" + NAME))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes("PDF".getBytes()))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + NAME + "\""));
    }

    @Test
    void unknownFileIsNotFound() throws Exception {
        Mockito.when(storageService.loadFile(NAME)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/files/" + NAME))
                .andExpect(status().isNotFound());
    }

    @Test
    void unparseableStoredTypeIsServedAsBinary() throws Exception {
        Mockito.when(storageService.loadFile(NAME))
                .thenReturn(Optional.of(new StoredFile("x".getBytes(), "not a type")));

        mockMvc.perform(get("/api/v1/files/" + NAME))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"));
    }
}
