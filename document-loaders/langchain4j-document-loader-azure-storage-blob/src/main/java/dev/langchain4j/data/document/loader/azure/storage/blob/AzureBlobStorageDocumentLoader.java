package dev.langchain4j.data.document.loader.azure.storage.blob;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.specialized.BlobInputStream;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.source.azure.storage.blob.AzureBlobStorageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class AzureBlobStorageDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobStorageDocumentLoader.class);

    private final BlobServiceClient blobServiceClient;

    public AzureBlobStorageDocumentLoader(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = ensureNotNull(blobServiceClient, "blobServiceClient");
    }

    public Document loadDocument(String containerName, String blobName, DocumentParser parser) {
        BlobClient blobClient = blobServiceClient.getBlobContainerClient(containerName).getBlobClient(blobName);
        BlobProperties properties = blobClient.getProperties();
        BlobInputStream blobInputStream = blobClient.openInputStream();
        AzureBlobStorageSource source = new AzureBlobStorageSource(blobInputStream, blobClient.getAccountName(), containerName, blobName, properties);
        return DocumentLoader.load(source, parser);
    }

    /**
     * Loads all documents from an Azure Blob Storage container.
     * Skips any documents that fail to load.
     *
     * @param containerName The name of the container to load from.
     * @param parser The parser to be used for parsing text from the blob.
     * @return A list of documents.
     */
    public List<Document> loadDocuments(String containerName, DocumentParser parser) {
        List<Document> documents = new ArrayList<>();

        blobServiceClient.getBlobContainerClient(containerName)
                .listBlobs()
                .forEach(blob -> {
                    try {
                        documents.add(loadDocument(containerName, blob.getName(), parser));
                    } catch (Exception e) {
                        log.warn("Failed to load blob '{}' from container '{}', skipping it.", blob.getName(), containerName, e);
                    }
                });

        return documents;
    }
}
