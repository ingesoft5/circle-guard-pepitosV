package com.circleguard.file.storage;

import java.io.InputStream;
import java.util.Optional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Files in the S3 bucket of the environment (Terraform module storage). The client takes its
 * credentials from the default chain: in the cluster, the IAM role of the k3s node through IMDSv2.
 */
public final class S3StorageBackend implements StorageBackend, AutoCloseable {

    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    public S3StorageBackend(S3Client s3, String bucket, String prefix) {
        this.s3 = s3;
        this.bucket = bucket;
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public void put(String name, InputStream content, long size, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(prefix + name)
                .contentType(contentType == null || contentType.isBlank() ? StoredFile.DEFAULT_CONTENT_TYPE : contentType)
                .contentLength(size)
                .build();
        s3.putObject(request, RequestBody.fromInputStream(content, size));
    }

    @Override
    public Optional<StoredFile> get(String name) {
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(prefix + name).build();
        try {
            ResponseBytes<GetObjectResponse> object = s3.getObjectAsBytes(request);
            return Optional.of(new StoredFile(object.asByteArray(), object.response().contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void close() {
        s3.close();
    }
}
