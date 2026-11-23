package com.circleguard.file.service;

import com.circleguard.file.storage.StorageBackend;
import com.circleguard.file.storage.StoredFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploads under "<uuid>_<safe original name>" and reads them back by that name. The name
 * never carries a path: it is the same key in the local directory and in the S3 bucket.
 */
@Service
public class FileStorageService {

    static final int MAX_NAME_LENGTH = 100;
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._-]");
    private static final Pattern STORED_NAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_[A-Za-z0-9._-]{1," + MAX_NAME_LENGTH + "}");

    private final StorageBackend backend;

    public FileStorageService(StorageBackend backend) {
        this.backend = backend;
    }

    public String saveFile(MultipartFile file) {
        String filename = UUID.randomUUID() + "_" + safeName(file.getOriginalFilename());
        try (InputStream content = file.getInputStream()) {
            backend.put(filename, content, file.getSize(), file.getContentType());
            return filename;
        } catch (IOException | RuntimeException e) {
            throw new StorageException("Could not store file", e);
        }
    }

    /** The stored file, or empty when the name was not issued by saveFile or the file is gone. */
    public Optional<StoredFile> loadFile(String filename) {
        if (filename == null || !STORED_NAME.matcher(filename).matches()) {
            return Optional.empty();
        }
        try {
            return backend.get(filename);
        } catch (IOException | RuntimeException e) {
            throw new StorageException("Could not read file", e);
        }
    }

    /** Last path segment of the client's name, with anything outside [A-Za-z0-9._-] replaced. */
    static String safeName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = UNSAFE.matcher(name).replaceAll("_").replaceFirst("^\\.+", "");
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(name.length() - MAX_NAME_LENGTH);
        }
        return name.isEmpty() ? "file" : name;
    }
}
