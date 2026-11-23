package com.circleguard.file.service;

/** A file could not be written to or read from the storage backend. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
