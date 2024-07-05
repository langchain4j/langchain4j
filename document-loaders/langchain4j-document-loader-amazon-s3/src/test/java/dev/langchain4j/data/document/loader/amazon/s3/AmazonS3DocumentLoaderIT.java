package dev.langchain4j.data.document.loader.amazon.s3;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

class AmazonS3DocumentLoaderIT {

    private static final String TEST_REGION = "us-east-1";
    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_KEY = "test-file.txt";
    private static final String TEST_KEY_2 = "test-directory/test-file-2.txt";
    private static final String TEST_CONTENT = "Hello, World!";
    private static final String TEST_CONTENT_2 = "Hello again!";

    LocalStackContainer s3Container;

    S3Client s3Client;

    AmazonS3DocumentLoader loader;

    DocumentParser parser = new TextDocumentParser();

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
        System.setProperty("aws.region", TEST_REGION);
    }

    @BeforeEach
    public void beforeEach() {
        s3Container = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.1.0"))
                .withServices(S3)
                .withEnv("DEFAULT_REGION", TEST_REGION);
        s3Container.start();

        s3Client = S3Client.builder()
                .endpointOverride(s3Container.getEndpointOverride(S3))
                .build();
        s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(TEST_BUCKET)
                .build());

        loader = AmazonS3DocumentLoader.builder()
                .endpointUrl(s3Container.getEndpointOverride(S3).toString())
                .build();
    }

    @Test
    public void should_load_single_document() {

        // given
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(TEST_BUCKET)
                        .key(TEST_KEY)
                        .build(),
                RequestBody.fromString(TEST_CONTENT)
        );

        // when
        Document document = loader.loadDocument(TEST_BUCKET, TEST_KEY, parser);

        // then
        assertThat(document.text()).isEqualTo(TEST_CONTENT);
        assertThat(document.metadata().toMap()).hasSize(1);
        assertThat(document.metadata().getString("source")).isEqualTo("s3://test-bucket/test-file.txt");
    }

    @Test
    public void should_load_empty_list() {

        // when
        List<Document> documents = loader.loadDocuments(TEST_BUCKET, parser);

        // then
        assertThat(documents).isEmpty();
    }

    @Test
    public void should_load_multiple_documents() {

        // given
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(TEST_BUCKET)
                        .key(TEST_KEY)
                        .build(),
                RequestBody.fromString(TEST_CONTENT)
        );

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(TEST_BUCKET)
                        .key(TEST_KEY_2)
                        .build(),
                RequestBody.fromString(TEST_CONTENT_2)
        );

        // when
        List<Document> documents = loader.loadDocuments(TEST_BUCKET, parser);

        // then
        assertThat(documents).hasSize(2);

        assertThat(documents.get(0).text()).isEqualTo(TEST_CONTENT_2);
        assertThat(documents.get(0).metadata().toMap()).hasSize(1);
        assertThat(documents.get(0).metadata().getString("source")).isEqualTo("s3://test-bucket/test-directory/test-file-2.txt");

        assertThat(documents.get(1).text()).isEqualTo(TEST_CONTENT);
        assertThat(documents.get(1).metadata().toMap()).hasSize(1);
        assertThat(documents.get(1).metadata().getString("source")).isEqualTo("s3://test-bucket/test-file.txt");
    }

    @Test
    public void should_load_multiple_documents_with_prefix() {

        // given
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(TEST_BUCKET)
                        .key("other_directory/file.txt")
                        .build(),
                RequestBody.fromString("You cannot load me!")
        );

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(TEST_BUCKET)
                        .key(TEST_KEY)
                        .build(),
                RequestBody.fromString(TEST_CONTENT)
        );

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(TEST_BUCKET)
                        .key(TEST_KEY_2)
                        .build(),
                RequestBody.fromString(TEST_CONTENT_2)
        );

        // when
        List<Document> documents = loader.loadDocuments(TEST_BUCKET, "test", parser);

        // then
        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).text()).isEqualTo(TEST_CONTENT_2);
        assertThat(documents.get(1).text()).isEqualTo(TEST_CONTENT);
    }

    @AfterEach
    public void afterEach() {
        s3Container.stop();
    }

    @AfterAll
    public static void afterAll() {
        System.clearProperty("aws.accessKeyId");
        System.clearProperty("aws.secretAccessKey");
        System.clearProperty("aws.region");
    }
}