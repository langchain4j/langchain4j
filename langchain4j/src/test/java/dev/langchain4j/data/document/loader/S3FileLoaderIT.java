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

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;
import static org.junit.jupiter.api.Assertions.*;

@Disabled("To run this test, you need a Docker-API compatible container runtime, such as using Testcontainers Cloud or installing Docker locally.")
public class S3FileLoaderIT {

    private LocalStackContainer s3Container;

    private S3Client s3Client;

    private static final DockerImageName localstackImage = DockerImageName.parse("localstack/localstack:2.0");

    @BeforeEach
    public void setUp() {
        s3Container = new LocalStackContainer(localstackImage)
                .withServices(S3)
                .withEnv("DEFAULT_REGION", "us-east-1");
        s3Container.start();

        s3Client = S3Client.builder()
                .endpointOverride(s3Container.getEndpointOverride(LocalStackContainer.Service.S3))
                .build();

        s3Client.createBucket(CreateBucketRequest.builder().bucket("test-bucket").build());
        s3Client.putObject(PutObjectRequest.builder().bucket("test-bucket").key("test-file.txt").build(),
                RequestBody.fromString("Hello, World!"));
    }

    @Test
    public void should_load_document() {
        S3FileLoader s3FileLoader = S3FileLoader.builder("test-bucket", "test-file.txt")
                .endpointUrl(s3Container.getEndpointOverride(LocalStackContainer.Service.S3).toString())
                .build();

        Document document = s3FileLoader.load();

        assertNotNull(document);
        assertEquals("Hello, World!", document.text());
        assertEquals("s3://test-bucket/test-file.txt", document.metadata("source"));
    }

    @Test
    public void should_throw_invalid_document_type() {
        S3Client s3Client = S3Client.builder()
                .endpointOverride(s3Container.getEndpointOverride(LocalStackContainer.Service.S3))
                .build();

        s3Client.createBucket(CreateBucketRequest.builder().bucket("test-bucket").build());
        s3Client.putObject(PutObjectRequest.builder().bucket("test-bucket").key("invalid-test-file.invalid").build(),
                RequestBody.fromString("Hello, World!"));

        S3FileLoader s3FileLoader = S3FileLoader.builder("test-bucket", "invalid-test-file.invalid")
                .endpointUrl(s3Container.getEndpointOverride(LocalStackContainer.Service.S3).toString())
                .build();

        assertThrows(RuntimeException.class, s3FileLoader::load);
    }

    @AfterEach
    public void tearDown() {
        s3Container.stop();
    }

}
