package dev.langchain4j.data.document.loader.azure.storage.blob;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class LocalAzureBlobStorageDocumentLoaderIT {

    private static final int AZURE_STORAGE_BLOB_PORT = 10000;

    @Container
    private static final GenericContainer<?> azurite = new GenericContainer<>(
            "mcr.microsoft.com/azure-storage/azurite:latest")
            .withExposedPorts(AZURE_STORAGE_BLOB_PORT);

    private static final String TEST_CONTAINER = "test-container";
    private static final String TEST_BLOB = "test-file.txt";
    private static final String TEST_BLOB_2 = "test-directory/test-file-2.txt";
    private static final String TEST_CONTENT = "Hello, World!";
    private static final String TEST_CONTENT_2 = "Hello again!";;

    private static BlobServiceClient blobServiceClient;

    private final DocumentParser parser = new TextDocumentParser();

    @BeforeAll
    public static void beforeAll() {
        String azuriteHost = azurite.getHost();
        int azuriteBlobMappedPort = azurite.getMappedPort(AZURE_STORAGE_BLOB_PORT);
        String connectionString = String.format("DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://%s:%d/devstoreaccount1;",
                azuriteHost, azuriteBlobMappedPort);

        blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
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
        assertThat(document.metadata().toMap()).hasSize(4);
        assertThat(document.metadata().getString("source")).endsWith("/test-file.txt");
    }

    @Test
    public void should_load_multiple_documents() {
        AzureBlobStorageDocumentLoader loader = new AzureBlobStorageDocumentLoader(blobServiceClient);
        List<Document> documents = loader.loadDocuments(TEST_CONTAINER, parser);

        assertThat(documents).hasSize(2);

        assertThat(documents.get(0).text()).isEqualTo(TEST_CONTENT_2);
        assertThat(documents.get(0).metadata().toMap()).hasSize(4);
        assertThat(documents.get(0).metadata().getString("source")).endsWith("/test-directory/test-file-2.txt");

        assertThat(documents.get(1).text()).isEqualTo(TEST_CONTENT);
        assertThat(documents.get(1).metadata().toMap()).hasSize(4);
        assertThat(documents.get(1).metadata().getString("source")).endsWith("/test-file.txt");
    }

    @AfterAll
    public static void afterAll() {
        blobServiceClient.getBlobContainerClient(TEST_CONTAINER).delete();
    }
}