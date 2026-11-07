package dev.langchain4j.data.document.source.azure.storage.blob;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.lang.String.format;

import com.azure.storage.blob.models.BlobProperties;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import java.io.InputStream;

public class AzureBlobStorageSource implements DocumentSource {

    public static final String SOURCE = "source";
    public static final String AZURE_STORAGE_BLOB_CREATION_TIME = "azure_storage_blob_creation_time";
    public static final String AZURE_STORAGE_BLOB_LAST_MODIFIED = "azure_storage_blob_last_modified";
    public static final String AZURE_STORAGE_BLOB_CONTENT_LENGTH = "azure_storage_blob_content_length";

    private final InputStream inputStream;
    private final String accountName;
    private final String containerName;
    private final String blobName;
    private final BlobProperties properties;

    public AzureBlobStorageSource(
            InputStream inputStream,
            String containerName,
            String accountName,
            String blobName,
            BlobProperties properties) {
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

    /**
     * Returns the metadata of the Azure Storage blob. All values are {@link String}s.
     * <ul>
     *     <li>{@link #SOURCE}: the URL of the blob</li>
     *     <li>{@link #AZURE_STORAGE_BLOB_CREATION_TIME}: the creation time of the blob</li>
     *     <li>{@link #AZURE_STORAGE_BLOB_LAST_MODIFIED}: the last modified time of the blob</li>
     *     <li>{@link #AZURE_STORAGE_BLOB_CONTENT_LENGTH}: the size of the blob in bytes</li>
     * </ul>
     *
     * @return the metadata of the Azure Storage blob
     */
    @Override
    public Metadata metadata() {
        Metadata metadata = new Metadata();
        metadata.put(SOURCE, format("https://%s.blob.core.windows.net/%s/%s", accountName, containerName, blobName));
        metadata.put(AZURE_STORAGE_BLOB_CREATION_TIME, String.valueOf(properties.getCreationTime()));
        metadata.put(AZURE_STORAGE_BLOB_LAST_MODIFIED, String.valueOf(properties.getLastModified()));
        metadata.put(AZURE_STORAGE_BLOB_CONTENT_LENGTH, String.valueOf(properties.getBlobSize()));
        return metadata;
    }
}
