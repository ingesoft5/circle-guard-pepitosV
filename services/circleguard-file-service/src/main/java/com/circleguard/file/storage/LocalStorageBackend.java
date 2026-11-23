package com.circleguard.file.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Files on a local directory: the default when no bucket is configured (local development and
 * Docker Compose). In Kubernetes the directory is an emptyDir, so files do not survive the pod.
 */
public final class LocalStorageBackend implements StorageBackend {

    private final Path root;

    public LocalStorageBackend(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not initialize storage", e);
        }
    }

    @Override
    public void put(String name, InputStream content, long size, String contentType) throws IOException {
        Files.copy(content, root.resolve(name));
    }

    @Override
    public Optional<StoredFile> get(String name) throws IOException {
        Path file = root.resolve(name);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(new StoredFile(Files.readAllBytes(file), Files.probeContentType(file)));
    }
}
