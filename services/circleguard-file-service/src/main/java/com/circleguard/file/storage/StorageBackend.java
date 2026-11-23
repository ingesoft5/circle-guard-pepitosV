package com.circleguard.file.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Where uploaded files live. The name is already safe (no path separators): FileStorageService
 * builds it and checks it before calling a backend.
 */
public interface StorageBackend {

    void put(String name, InputStream content, long size, String contentType) throws IOException;

    Optional<StoredFile> get(String name) throws IOException;
}
