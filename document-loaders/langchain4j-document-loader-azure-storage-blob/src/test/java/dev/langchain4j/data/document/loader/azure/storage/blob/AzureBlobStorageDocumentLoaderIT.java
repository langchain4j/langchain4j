package dev.langchain4j.data.document.loader.azure.storage.blob;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AZURE_STORAGE_ACCOUNT_NAME", matches = ".+")
public class AzureBlobStorageDocumentLoaderIT {

    private static final String TEST_CONTAINER = "test-container";
    private static final String TEST_BLOB = "test-file.txt";
    private static final String TEST_BLOB_2 = "test-directory/test-file-2.txt";
    private static final String TEST_CONTENT = "Hello, World!";
    private static final String TEST_CONTENT_2 = "Hello again!";;

    private static BlobServiceClient blobServiceClient;

    private final DocumentParser parser = new TextDocumentParser();

    @BeforeAll
    public static void beforeAll() {
        String storageAccountName = System.getenv("AZURE_STORAGE_ACCOUNT_NAME");
        String storageAccountKey = System.getenv("AZURE_STORAGE_ACCOUNT_KEY");
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(storageAccountName, storageAccountKey);

        blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(String.format("https://%s.blob.core.windows.net/", storageAccountName))
                .credential(credential)
                .buildClient();

        BlobContainerClient blobContainerClient = blobServiceClient.createBlobContainerIfNotExists(TEST_CONTAINER);

        blobContainerClient.getBlobClient(TEST_BLOB).upload(new ByteArrayInputStream(TEST_CONTENT.getBytes()), true);
        blobContainerClient.getBlobClient(TEST_BLOB_2).upload(new ByteArrayInputStream(TEST_CONTENT_2.getBytes()), true);
    }

    @Test
    public void should_load_single_document() {
        AzureBlobStorageDocumentLoader loader = new AzureBlobStorageDocumentLoader(blobServiceClient);
        Document document = loader.loadDocument(TEST_CONTAINER, TEST_BLOB, parser);

        assertThat(document.text()).isEqualTo(TEST_CONTENT);
        assertThat(document.metadata().asMap().size()).isEqualTo(4);
        assertThat(document.metadata("source")).endsWith("/test-file.txt");
    }

    @Test
    public void should_load_multiple_documents() {
        AzureBlobStorageDocumentLoader loader = new AzureBlobStorageDocumentLoader(blobServiceClient);
        List<Document> documents = loader.loadDocuments(TEST_CONTAINER, parser);

        assertThat(documents).hasSize(2);

        assertThat(documents.get(0).text()).isEqualTo(TEST_CONTENT_2);
        assertThat(documents.get(0).metadata().asMap()).hasSize(4);
        assertThat(documents.get(0).metadata("source")).endsWith("/test-directory/test-file-2.txt");

        assertThat(documents.get(1).text()).isEqualTo(TEST_CONTENT);
        assertThat(documents.get(1).metadata().asMap()).hasSize(4);
        assertThat(documents.get(1).metadata("source")).endsWith("/test-file.txt");
    }

    @AfterEach
    public void afterEach() {
        blobServiceClient.getBlobContainerClient(TEST_CONTAINER).delete();
    }
}