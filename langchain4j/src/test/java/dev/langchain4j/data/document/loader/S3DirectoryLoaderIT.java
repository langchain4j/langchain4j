package dev.langchain4j.data.document.loader;

import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@Disabled("To run this test, you need a Docker-API compatible container runtime, such as using Testcontainers Cloud or installing Docker locally.")
public class S3DirectoryLoaderIT {

    private LocalStackContainer s3Container;
    private S3Client s3Client;

    @BeforeEach
    public void setUp() {
        s3Container = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.0"))
                .withServices(S3)
                .withEnv("DEFAULT_REGION", "us-east-1");
        s3Container.start();

        s3Client = S3Client.builder()
                .endpointOverride(s3Container.getEndpointOverride(S3))
                .build();

        s3Client.createBucket(CreateBucketRequest.builder().bucket("test-bucket").build());
    }

    @Test
    public void should_load_empty_list() {
        S3DirectoryLoader s3DirectoryLoader = S3DirectoryLoader.builder("test-bucket")
                .endpointUrl(s3Container.getEndpointOverride(S3).toString())
                .build();

        List<Document> documents = s3DirectoryLoader.load();

        assertTrue(documents.isEmpty());
    }

    @Test
    public void should_load_multiple_files_without_prefix() {
        s3Client.putObject(PutObjectRequest.builder().bucket("test-bucket").key("file1.txt").build(),
                RequestBody.fromString("Hello, World!"));
        s3Client.putObject(PutObjectRequest.builder().bucket("test-bucket").key("directory/file2.txt").build(),
                RequestBody.fromString("Hello, again!"));

        S3DirectoryLoader s3DirectoryLoader = S3DirectoryLoader.builder("test-bucket")
                .endpointUrl(s3Container.getEndpointOverride(S3).toString())
                .build();

        List<Document> documents = s3DirectoryLoader.load();

        assertEquals(2, documents.size());
        assertEquals("Hello, again!", documents.get(0).text());
        assertEquals("s3://test-bucket/directory/file2.txt", documents.get(0).metadata("source"));
        assertEquals("Hello, World!", documents.get(1).text());
        assertEquals("s3://test-bucket/file1.txt", documents.get(1).metadata("source"));
    }

    @Test
    public void should_load_multiple_files_with_prefix() {
        s3Client.putObject(PutObjectRequest.builder().bucket("test-bucket").key("other_directory/file1.txt").build(),
                RequestBody.fromString("You cannot load me!"));
        s3Client.putObject(PutObjectRequest.builder().bucket("test-bucket").key("directory/file2.txt").build(),
                RequestBody.fromString("Hello, World!"));
        s3Client.putObject(PutObjectRequest.builder().bucket("test-bucket").key("directory/file3.txt").build(),
                RequestBody.fromString("Hello, again!"));

        S3DirectoryLoader s3DirectoryLoader = S3DirectoryLoader.builder("test-bucket", "directory")
                .endpointUrl(s3Container.getEndpointOverride(S3).toString())
                .build();

        List<Document> documents = s3DirectoryLoader.load();

        assertEquals(2, documents.size());
        assertEquals("Hello, World!", documents.get(0).text());
        assertEquals("s3://test-bucket/directory/file2.txt", documents.get(0).metadata("source"));
        assertEquals("Hello, again!", documents.get(1).text());
        assertEquals("s3://test-bucket/directory/file3.txt", documents.get(1).metadata("source"));
    }

    @Test
    public void should_load_ignoring_unsupported_types() {
        s3Client.putObject(PutObjectRequest.builder().bucket("test-bucket").key("directory/file2.invalid").build(),
                RequestBody.fromString("Hello, World!"));
        s3Client.putObject(PutObjectRequest.builder().bucket("test-bucket").key("directory/file3.txt").build(),
                RequestBody.fromString("Hello, again!"));

        S3DirectoryLoader s3DirectoryLoader = S3DirectoryLoader.builder("test-bucket", "directory")
                .endpointUrl(s3Container.getEndpointOverride(S3).toString())
                .build();

        List<Document> documents = s3DirectoryLoader.load();

        assertEquals(1, documents.size());
        assertEquals("Hello, again!", documents.get(0).text());
        assertEquals("s3://test-bucket/directory/file3.txt", documents.get(0).metadata("source"));
    }

    @AfterEach
    public void tearDown() {
        s3Container.stop();
    }
}

