package com.circleguard.file.storage;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Picks the storage backend at startup: S3 when circleguard.storage.s3.bucket has a value (the
 * chart sets it per environment), the local directory otherwise.
 */
@Configuration
public class StorageConfig {

    @Bean
    public StorageBackend storageBackend(
            @Value("${circleguard.storage.s3.bucket:}") String bucket,
            @Value("${circleguard.storage.s3.region:us-east-1}") String region,
            @Value("${circleguard.storage.s3.prefix:uploads/}") String prefix,
            @Value("${circleguard.storage.local-dir:uploads}") String localDir) {
        if (bucket == null || bucket.isBlank()) {
            return new LocalStorageBackend(Path.of(localDir));
        }
        S3Client s3 = S3Client.builder()
                .region(Region.of(region))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
        return new S3StorageBackend(s3, bucket, prefix);
    }
}
