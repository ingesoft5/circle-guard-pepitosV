package com.circleguard.file.storage;

/** A stored file read back: its bytes and the content type it was uploaded with. */
public final class StoredFile {

    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final byte[] content;
    private final String contentType;

    public StoredFile(byte[] content, String contentType) {
        this.content = content.clone();
        this.contentType = contentType == null || contentType.isBlank() ? DEFAULT_CONTENT_TYPE : contentType;
    }

    public byte[] content() {
        return content.clone();
    }

    public String contentType() {
        return contentType;
    }
}
