package dev.langchain4j.data.document.oss.loader.alibaba.oss;

import static org.assertj.core.api.Assertions.assertThat;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.PutObjectRequest;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.loader.alibaba.oss.AlibabaOssCredentials;
import dev.langchain4j.data.document.loader.alibaba.oss.AlibabaOssDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ALIBABA_OSS_SECRET_ACCESS_KEY", matches = ".+")
class AlibabaOssDocumentLoaderIT {

    private static OSS ossClient;

    private static AlibabaOssDocumentLoader loader;

    DocumentParser parser = new TextDocumentParser();

    // config info for user
    private static final String ENDPOINT = System.getenv("ALIBABA_OSS_ENDPOINT");
    private static final String REGION = System.getenv("ALIBABA_OSS_REGION");
    private static final String ACCESS_KEY_ID = System.getenv("ALIBABA_OSS_ACCESS_KEY_ID");
    private static final String SECRET_ACCESS_KEY = System.getenv("ALIBABA_OSS_SECRET_ACCESS_KEY");
    // test bucket info
    private static final String TEST_BUCKET = "test-by-reg";
    // test OSS file key info
    private static final String TEST_KEY_1 = "test-file-1.txt";
    private static final String TEST_KEY_2 = "test-directory/test-file-2.txt";
    private static final String TEST_KEY_3 = "other_directory/file-3.txt";
    // test OSS file content info
    private static final String TEST_CONTENT_1 = "Hello, World!";
    private static final String TEST_CONTENT_2 = "Hello again!";
    private static final String TEST_CONTENT_3 = "you can't load me";

    @BeforeAll
    static void beforeAll() {
        // init auth info
        AlibabaOssCredentials alibabaOssCredentials = new AlibabaOssCredentials(ACCESS_KEY_ID, SECRET_ACCESS_KEY, null);
        // init OSS Client
        ossClient = OSSClientBuilder.create()
                .endpoint(ENDPOINT)
                .credentialsProvider(alibabaOssCredentials.toCredentialsProvider())
                .region(REGION)
                .build();
        // init document loader with OSS Client
        loader = new AlibabaOssDocumentLoader(ossClient);
    }

    @BeforeEach
    void beforeEach() {
        // clean exist data
        final ListObjectsV2Result listObjectsV2Result = ossClient.listObjectsV2(TEST_BUCKET);
        listObjectsV2Result
                .getObjectSummaries()
                .forEach(ossObjectSummary -> ossClient.deleteObject(TEST_BUCKET, ossObjectSummary.getKey()));
    }

    @Test
    void should_load_empty_document() {
        // when
        List<Document> document = loader.loadDocuments(TEST_BUCKET, null, parser);
        // then
        assertThat(document).isEmpty();
    }

    @Test
    void should_load_single_document() {
        // given
        InputStream inputStream = new ByteArrayInputStream(TEST_CONTENT_1.getBytes(StandardCharsets.UTF_8));
        ossClient.putObject(new PutObjectRequest(TEST_BUCKET, TEST_KEY_1, inputStream));

        // when
        Document document = loader.loadDocument(TEST_BUCKET, TEST_KEY_1, parser);

        // then
        assertThat(document.text()).isEqualTo(TEST_CONTENT_1);
        assertThat(document.metadata().toMap()).hasSize(1);
        assertThat(document.metadata().getString("source"))
                .isEqualTo(String.format("oss://%s/%s", TEST_BUCKET, TEST_KEY_1));
    }

    @Test
    void should_load_multiple_documents() {

        // given
        ossClient.putObject(new PutObjectRequest(
                TEST_BUCKET, TEST_KEY_1, new ByteArrayInputStream(TEST_CONTENT_1.getBytes(StandardCharsets.UTF_8))));
        ossClient.putObject(new PutObjectRequest(
                TEST_BUCKET, TEST_KEY_2, new ByteArrayInputStream(TEST_CONTENT_2.getBytes(StandardCharsets.UTF_8))));

        // when
        List<Document> documents = loader.loadDocuments(TEST_BUCKET, parser);

        // then
        assertThat(documents).hasSize(2);

        assertThat(documents.get(0).text()).isEqualTo(TEST_CONTENT_2);
        assertThat(documents.get(0).metadata().toMap()).hasSize(1);
        assertThat(documents.get(0).metadata().toMap())
                .containsEntry("source", String.format("oss://%s/%s", TEST_BUCKET, TEST_KEY_2));

        assertThat(documents.get(1).text()).isEqualTo(TEST_CONTENT_1);
        assertThat(documents.get(1).metadata().toMap()).hasSize(1);
        assertThat(documents.get(1).metadata().toMap())
                .containsEntry("source", String.format("oss://%s/%s", TEST_BUCKET, TEST_KEY_1));
    }

    @Test
    void should_load_multiple_documents_with_prefix() {

        // given
        ossClient.putObject(new PutObjectRequest(
                TEST_BUCKET, TEST_KEY_1, new ByteArrayInputStream(TEST_CONTENT_1.getBytes(StandardCharsets.UTF_8))));
        ossClient.putObject(new PutObjectRequest(
                TEST_BUCKET, TEST_KEY_2, new ByteArrayInputStream(TEST_CONTENT_2.getBytes(StandardCharsets.UTF_8))));
        ossClient.putObject(new PutObjectRequest(
                TEST_BUCKET, TEST_KEY_3, new ByteArrayInputStream(TEST_CONTENT_3.getBytes(StandardCharsets.UTF_8))));

        // when
        List<Document> documents = loader.loadDocuments(TEST_BUCKET, "test", parser);

        // then
        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).text()).isEqualTo(TEST_CONTENT_2);
        assertThat(documents.get(1).text()).isEqualTo(TEST_CONTENT_1);
    }
}
