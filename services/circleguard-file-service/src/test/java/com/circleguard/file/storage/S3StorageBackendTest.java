package com.circleguard.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3StorageBackendTest {

    private final S3Client s3 = mock(S3Client.class);
    private final S3StorageBackend backend = new S3StorageBackend(s3, "files-bucket", "uploads/");

    @Test
    void putWritesUnderThePrefixWithTypeAndLength() {
        backend.put("id_a.pdf", new ByteArrayInputStream("PDF".getBytes()), 3, "application/pdf");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("files-bucket");
        assertThat(request.getValue().key()).isEqualTo("uploads/id_a.pdf");
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(request.getValue().contentLength()).isEqualTo(3L);
    }

    @Test
    void putWithoutTypeStoresBinary() {
        backend.put("id_a.bin", new ByteArrayInputStream(new byte[0]), 0, null);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().contentType()).isEqualTo(StoredFile.DEFAULT_CONTENT_TYPE);
    }

    @Test
    void getReturnsTheObjectBytesAndType() {
        GetObjectResponse response = GetObjectResponse.builder().contentType("application/pdf").build();
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(response, "PDF".getBytes()));

        assertThat(backend.get("id_a.pdf")).hasValueSatisfying(file -> {
            assertThat(file.content()).asString().isEqualTo("PDF");
            assertThat(file.contentType()).isEqualTo("application/pdf");
        });
        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3).getObjectAsBytes(request.capture());
        assertThat(request.getValue().key()).isEqualTo("uploads/id_a.pdf");
    }

    @Test
    void missingKeyIsEmpty() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());

        assertThat(backend.get("id_gone.pdf")).isEmpty();
    }

    @Test
    void closeClosesTheClient() {
        backend.close();

        verify(s3).close();
    }

    @Test
    void noPrefixUsesTheNameAsKey() {
        new S3StorageBackend(s3, "files-bucket", null)
                .put("id_a.pdf", new ByteArrayInputStream(new byte[0]), 0, "application/pdf");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().key()).isEqualTo("id_a.pdf");
    }
}
