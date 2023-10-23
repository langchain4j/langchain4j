package dev.langchain4j.data.document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class S3FileLoaderTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private GetObjectResponse getObjectResponse;

    private S3FileLoader s3FileLoader;

    @BeforeEach
    public void setUp() {
        s3FileLoader = S3FileLoader.builder()
                .bucket("langchain4j")
                .key("key.txt")
                .build();
    }

    @Test
    public void should_load_document() {
        ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(getObjectResponse, new ByteArrayInputStream("test".getBytes()));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

        Document result = s3FileLoader.load(s3Client);

        assertNotNull(result);
        assertEquals("test", result.text());
        assertEquals("s3://langchain4j/key.txt", result.metadata("source"));
    }

    @Test
    public void should_throw_s3_exception() {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(S3Exception.builder().message("S3 error").build());

        assertThrows(RuntimeException.class, () -> s3FileLoader.load(s3Client));
    }

    @Test
    public void should_throw_invalid_key() {
        assertThrows(IllegalArgumentException.class, () -> S3FileLoader.builder()
                .bucket("testBucket")
                .build());
    }

    @Test
    public void should_throw_invalid_bucket() {
        assertThrows(IllegalArgumentException.class, () -> S3FileLoader.builder()
                .key("testKey")
                .build());
    }
}
