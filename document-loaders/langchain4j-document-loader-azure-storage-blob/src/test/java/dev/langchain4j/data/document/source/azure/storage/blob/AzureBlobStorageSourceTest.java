package dev.langchain4j.data.document.source.azure.storage.blob;

import static dev.langchain4j.data.document.source.azure.storage.blob.AzureBlobStorageSource.AZURE_STORAGE_BLOB_CONTENT_LENGTH;
import static dev.langchain4j.data.document.source.azure.storage.blob.AzureBlobStorageSource.AZURE_STORAGE_BLOB_CREATION_TIME;
import static dev.langchain4j.data.document.source.azure.storage.blob.AzureBlobStorageSource.AZURE_STORAGE_BLOB_LAST_MODIFIED;
import static dev.langchain4j.data.document.source.azure.storage.blob.AzureBlobStorageSource.SOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.storage.blob.models.BlobProperties;
import dev.langchain4j.data.document.Metadata;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AzureBlobStorageSourceTest {

    @Test
    void should_expose_metadata_keys_as_public_constants() {
        // constant values must stay unchanged for backward compatibility with persisted metadata
        assertThat(SOURCE).isEqualTo("source");
        assertThat(AZURE_STORAGE_BLOB_CREATION_TIME).isEqualTo("azure_storage_blob_creation_time");
        assertThat(AZURE_STORAGE_BLOB_LAST_MODIFIED).isEqualTo("azure_storage_blob_last_modified");
        assertThat(AZURE_STORAGE_BLOB_CONTENT_LENGTH).isEqualTo("azure_storage_blob_content_length");
    }

    @Test
    void should_populate_all_documented_keys() {
        // given
        BlobProperties properties = mock(BlobProperties.class);
        when(properties.getCreationTime()).thenReturn(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
        when(properties.getLastModified()).thenReturn(OffsetDateTime.parse("2024-01-02T00:00:00Z"));
        when(properties.getBlobSize()).thenReturn(123L);

        // when
        Metadata metadata = new AzureBlobStorageSource(
                        new ByteArrayInputStream(new byte[0]), "my-container", "myaccount", "docs/file.txt", properties)
                .metadata();

        // then
        assertThat(metadata.toMap())
                .containsOnlyKeys(
                        SOURCE,
                        AZURE_STORAGE_BLOB_CREATION_TIME,
                        AZURE_STORAGE_BLOB_LAST_MODIFIED,
                        AZURE_STORAGE_BLOB_CONTENT_LENGTH);
        assertThat(metadata.getString(SOURCE))
                .isEqualTo("https://myaccount.blob.core.windows.net/my-container/docs/file.txt");
        assertThat(metadata.getString(AZURE_STORAGE_BLOB_CREATION_TIME)).isEqualTo("2024-01-01T00:00Z");
        assertThat(metadata.getString(AZURE_STORAGE_BLOB_LAST_MODIFIED)).isEqualTo("2024-01-02T00:00Z");
        assertThat(metadata.getString(AZURE_STORAGE_BLOB_CONTENT_LENGTH)).isEqualTo("123");
    }

    @Test
    void should_fail_when_properties_are_missing() {
        assertThatThrownBy(() -> new AzureBlobStorageSource(
                        new ByteArrayInputStream(new byte[0]), "my-container", "myaccount", "docs/file.txt", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("properties");
    }
}
