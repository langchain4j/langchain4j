package dev.langchain4j.data.document.loader.gcs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import com.google.cloud.storage.StorageException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import java.util.List;
import org.junit.jupiter.api.Test;

public class GoogleCloudStorageDocumentLoaderIT {

    public static final String BUCKET_NAME = "genai-java-demos-langchain4j-test-bucket";
    public static final String FILE_NAME = "cymbal-starlight-2024.txt";

    @Test
    void should_load_single_document() {
        // given
        GoogleCloudStorageDocumentLoader gcsLoader = GoogleCloudStorageDocumentLoader.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .build();

        // when
        Document document = gcsLoader.loadDocument(BUCKET_NAME, FILE_NAME, new TextDocumentParser());

        // then
        assertThat(document.toTextSegment().text()).contains("Cymbal Starlight");
    }

    @Test
    void should_fail_for_missing_document() {
        // given
        GoogleCloudStorageDocumentLoader gcsLoader = GoogleCloudStorageDocumentLoader.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .build();
        final String DUMMY_FILE_NAME = "DUMMY_XYZ.txt";

        // when
        IllegalArgumentException exception = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> gcsLoader.loadDocument(BUCKET_NAME, DUMMY_FILE_NAME, new TextDocumentParser()))
                .actual();

        // then
        assertThat(exception.getMessage()).contains(DUMMY_FILE_NAME);
    }

    @Test
    void should_load_multipe_documents() {
        // given
        GoogleCloudStorageDocumentLoader gcsLoader = GoogleCloudStorageDocumentLoader.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .build();

        // when
        List<Document> documents = gcsLoader.loadDocuments(BUCKET_NAME, new TextDocumentParser());

        // then
        assertThat(documents).hasSize(2);
    }

    @Test
    void should_fail_for_wrong_bucket() {
        // given
        GoogleCloudStorageDocumentLoader gcsLoader = GoogleCloudStorageDocumentLoader.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .build();

        // then
        StorageException exception = assertThatExceptionOfType(StorageException.class)
                .isThrownBy(() -> gcsLoader.loadDocuments("DUMMY_BUCKET", new TextDocumentParser()))
                .actual();
        assertThat(exception.getMessage()).contains("The specified bucket does not exist");
    }

    @Test
    void should_load_document_with_glob() {
        // given
        GoogleCloudStorageDocumentLoader gcsLoader = GoogleCloudStorageDocumentLoader.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .build();

        // when
        List<Document> documents = gcsLoader.loadDocuments(BUCKET_NAME, "*.txt", new TextDocumentParser());

        // then
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).metadata().getString("source")).isEqualTo("gs://" + BUCKET_NAME + "/" + FILE_NAME);
    }
}
