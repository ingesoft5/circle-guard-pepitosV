package com.circleguard.file.controller;

import com.circleguard.file.service.FileStorageService;
import com.circleguard.file.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class FileUploadController {
    private final FileStorageService storageService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String filename = storageService.saveFile(file);
        return ResponseEntity.ok(Map.of("filename", filename));
    }

    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> download(@PathVariable String filename) {
        return storageService.loadFile(filename)
                .map(file -> ResponseEntity.ok()
                        .contentType(mediaType(file))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(filename).build().toString())
                        .body(file.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** The type the client sent on upload; a value Spring cannot parse is served as binary. */
    private static MediaType mediaType(StoredFile file) {
        try {
            return MediaType.parseMediaType(file.contentType());
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
