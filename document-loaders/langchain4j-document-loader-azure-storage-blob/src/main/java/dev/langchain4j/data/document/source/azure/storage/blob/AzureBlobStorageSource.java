package dev.langchain4j.data.document.source.azure.storage.blob;

import com.azure.storage.blob.models.BlobProperties;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;

import java.io.InputStream;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.lang.String.format;

public class AzureBlobStorageSource implements DocumentSource {

    public static final String SOURCE = "source";

    private final InputStream inputStream;
    private final String accountName;
    private final String containerName;
    private final String blobName;
    private final BlobProperties properties;

    public AzureBlobStorageSource(InputStream inputStream, String containerName, String accountName, String blobName, BlobProperties properties) {
        this.inputStream = ensureNotNull(inputStream, "inputStream");
        this.accountName = ensureNotBlank(accountName, "accountName");
        this.containerName = ensureNotBlank(containerName, "containerName");
        this.blobName = ensureNotBlank(blobName, "blobName");
        this.properties = ensureNotNull(properties, "properties");
    }

    @Override
    public InputStream inputStream() {
        return inputStream;
    }

    @Override
    public Metadata metadata() {
        Metadata metadata = new Metadata();
        metadata.add(SOURCE, format("https://%s.blob.core.windows.net/%s/%s", accountName, containerName, blobName));
        metadata.add("Creation-Time", properties.getCreationTime());
        metadata.add("Last-Modified", properties.getLastModified());
        metadata.add("Content-Length", String.valueOf(properties.getBlobSize()));
        return metadata;
    }
}
