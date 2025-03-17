package dev.langchain4j.data.document.loader.tencent.cos;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;
import java.net.URL;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TENCENT_SECRET_KEY", matches = ".+")
class TencentCosDocumentLoaderIT {

    private static final String TEST_BUCKET = "test-buket";
    private static final String TEST_KEY = "test-file.txt";
    private static final String TEST_KEY_2 = "test-directory/test-file-2.txt";
    private static final String TEST_CONTENT = "Hello, World!";
    private static final String TEST_CONTENT_2 = "Hello again!";

    static COSClient cosClient;

    static TencentCosDocumentLoader loader;

    DocumentParser parser = new TextDocumentParser();

    @BeforeAll
    public static void beforeAll() {
        TencentCredentials tencentCredentials = new TencentCredentials(
                System.getenv("TENCENT_SECRET_ID"),
                System.getenv("TENCENT_SECRET_KEY"),
                null
        );
        cosClient = new COSClient(
                tencentCredentials.toCredentialsProvider(),
                new ClientConfig(new Region("ap-shanghai"))
        );
        loader = new TencentCosDocumentLoader(cosClient);
    }

    @Test
    void should_load_empty_list() {

        // when
        List<Document> documents = loader.loadDocuments(TEST_BUCKET, parser);

        // then
        assertThat(documents).isEmpty();
    }

    @Test
    void should_load_single_document() {

        URL url = getClass().getClassLoader().getResource("test.txt");
        // given
        cosClient.putObject(
                new PutObjectRequest(TEST_BUCKET, TEST_KEY, new File(url.getFile()))
        );

        // when
        Document document = loader.loadDocument(TEST_BUCKET, TEST_KEY, parser);

        // then
        assertThat(document.text()).isEqualTo(TEST_CONTENT);
        assertThat(document.metadata().toMap()).hasSize(1);
        assertThat(document.metadata().getString("source")).isEqualTo(String.format("cos://%s/%s", TEST_BUCKET, TEST_KEY));
    }

    @Test
    void should_load_multiple_documents() {

        // given
        URL url = getClass().getClassLoader().getResource("test.txt");
        cosClient.putObject(
                new PutObjectRequest(TEST_BUCKET, TEST_KEY, new File(url.getFile()))
        );

        URL url2 = getClass().getClassLoader().getResource("test2.txt");
        cosClient.putObject(
                new PutObjectRequest(TEST_BUCKET, TEST_KEY_2, new File(url2.getFile()))
        );

        // when
        List<Document> documents = loader.loadDocuments(TEST_BUCKET, parser);

        // then
        assertThat(documents).hasSize(2);

        assertThat(documents.get(0).text()).isEqualTo(TEST_CONTENT_2);
        assertThat(documents.get(0).metadata().toMap()).hasSize(1);
        assertThat(documents.get(0).metadata().getString("source")).isEqualTo(String.format("cos://%s/%s", TEST_BUCKET, TEST_KEY_2));

        assertThat(documents.get(1).text()).isEqualTo(TEST_CONTENT);
        assertThat(documents.get(1).metadata().toMap()).hasSize(1);
        assertThat(documents.get(1).metadata("source")).isEqualTo(String.format("cos://%s/%s", TEST_BUCKET, TEST_KEY));
    }

    @Test
    void should_load_multiple_documents_with_prefix() {

        // given
        URL otherUrl = getClass().getClassLoader().getResource("other.txt");
        cosClient.putObject(
                new PutObjectRequest(TEST_BUCKET, "other_directory/file.txt", new File(otherUrl.getFile()))
        );

        URL url = getClass().getClassLoader().getResource("test.txt");
        cosClient.putObject(
                new PutObjectRequest(TEST_BUCKET, TEST_KEY, new File(url.getFile()))
        );

        URL url2 = getClass().getClassLoader().getResource("test2.txt");
        cosClient.putObject(
                new PutObjectRequest(TEST_BUCKET, TEST_KEY_2, new File(url2.getFile()))
        );


        // when
        List<Document> documents = loader.loadDocuments(TEST_BUCKET, "test", parser);

        // then
        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).text()).isEqualTo(TEST_CONTENT_2);
        assertThat(documents.get(1).text()).isEqualTo(TEST_CONTENT);
    }
}
