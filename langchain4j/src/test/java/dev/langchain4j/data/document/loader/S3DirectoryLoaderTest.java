package dev.langchain4j.data.document.loader;

import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class S3DirectoryLoaderTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private ListObjectsV2Response listObjectsV2Response;

    @Mock
    private GetObjectResponse getObjectResponse;

    private S3DirectoryLoader s3DirectoryLoader;

    @BeforeEach
    public void setUp() {
        s3DirectoryLoader = S3DirectoryLoader.builder("langchain4j", "testPrefix").build();
    }

    @Test
    public void should_load_documents_from_directory() {
        S3Object s3Object1 = S3Object.builder().key("testPrefix/testKey1.txt").size(10L).build();
        S3Object s3Object2 = S3Object.builder().key("testPrefix/testKey2.txt").size(20L).build();
        when(listObjectsV2Response.contents()).thenReturn(Arrays.asList(s3Object1, s3Object2));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listObjectsV2Response);

        ResponseInputStream<GetObjectResponse> responseInputStream1 = new ResponseInputStream<>(getObjectResponse, new ByteArrayInputStream("test1".getBytes()));
        ResponseInputStream<GetObjectResponse> responseInputStream2 = new ResponseInputStream<>(getObjectResponse, new ByteArrayInputStream("test2".getBytes()));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream1).thenReturn(responseInputStream2);

        List<Document> documents = s3DirectoryLoader.load(s3Client);

        assertEquals(2, documents.size());
        assertEquals("test1", documents.get(0).text());
        assertEquals("test2", documents.get(1).text());
        assertEquals("s3://langchain4j/testPrefix/testKey1.txt", documents.get(0).metadata("source"));
        assertEquals("s3://langchain4j/testPrefix/testKey2.txt", documents.get(1).metadata("source"));
    }

    @Test
    public void should_load_documents_from_directory_ignoring_unsupported_types() {
        S3Object s3Object1 = S3Object.builder().key("testPrefix/testKey1.txt").size(10L).build();
        S3Object s3Object2 = S3Object.builder().key("testPrefix/testKey2.txt").size(20L).build();
        S3Object s3Object3 = S3Object.builder().key("testPrefix/testKey2.invalid").size(30L).build();

        when(listObjectsV2Response.contents()).thenReturn(Arrays.asList(s3Object1, s3Object2, s3Object3));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listObjectsV2Response);

        ResponseInputStream<GetObjectResponse> responseInputStream1 = new ResponseInputStream<>(getObjectResponse, new ByteArrayInputStream("test1".getBytes()));
        ResponseInputStream<GetObjectResponse> responseInputStream2 = new ResponseInputStream<>(getObjectResponse, new ByteArrayInputStream("test2".getBytes()));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream1).thenReturn(responseInputStream2);

        List<Document> documents = s3DirectoryLoader.load(s3Client);

        assertEquals(2, documents.size());
        assertEquals("test1", documents.get(0).text());
        assertEquals("test2", documents.get(1).text());
        assertEquals("s3://langchain4j/testPrefix/testKey1.txt", documents.get(0).metadata("source"));
        assertEquals("s3://langchain4j/testPrefix/testKey2.txt", documents.get(1).metadata("source"));
    }

    @Test
    public void should_return_empty_list_when_no_objects() {
        when(listObjectsV2Response.contents()).thenReturn(Arrays.asList());
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listObjectsV2Response);

        List<Document> documents = s3DirectoryLoader.load(s3Client);

        assertTrue(documents.isEmpty());
    }

    @Test
    public void should_throw_s3_exception() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(S3Exception.builder().message("S3 error").build());

        assertThrows(RuntimeException.class, () -> s3DirectoryLoader.load(s3Client));
    }

    @Test
    public void should_throw_invalid_bucket() {
        assertThrows(IllegalArgumentException.class, () -> S3DirectoryLoader.builder(null, "testPrefix").build());
    }
}
