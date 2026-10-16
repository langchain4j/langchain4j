package dev.langchain4j.data.document.source.gcs;

import static dev.langchain4j.data.document.source.gcs.GcsSource.BUCKET;
import static dev.langchain4j.data.document.source.gcs.GcsSource.CONTENT_TYPE;
import static dev.langchain4j.data.document.source.gcs.GcsSource.CREATE_TIME;
import static dev.langchain4j.data.document.source.gcs.GcsSource.NAME;
import static dev.langchain4j.data.document.source.gcs.GcsSource.SIZE;
import static dev.langchain4j.data.document.source.gcs.GcsSource.SOURCE;
import static dev.langchain4j.data.document.source.gcs.GcsSource.UPDATE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import dev.langchain4j.data.document.Metadata;
import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class GcsSourceTest {

    private static Blob mockBlob(String contentType) {
        Blob blob = mock(Blob.class);
        // reader() must be non-null: GcsSource wraps it with Channels.newInputStream(...).
        ReadChannel readChannel = mock(ReadChannel.class);
        try {
            // Simulate an empty (already exhausted) channel.
            lenient()
                    .when(readChannel.read(org.mockito.ArgumentMatchers.any(ByteBuffer.class)))
                    .thenReturn(-1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(blob.reader()).thenReturn(readChannel);
        when(blob.getBucket()).thenReturn("my-bucket");
        when(blob.getName()).thenReturn("my-object.txt");
        when(blob.getSize()).thenReturn(123L);
        when(blob.getCreateTimeOffsetDateTime()).thenReturn(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
        when(blob.getUpdateTimeOffsetDateTime()).thenReturn(OffsetDateTime.parse("2024-01-02T00:00:00Z"));
        when(blob.getContentType()).thenReturn(contentType);
        return blob;
    }

    @Test
    void should_build_source_when_content_type_is_null() {
        // given a valid object whose content type is not set (nullable per GCS Blob API)
        Blob blob = mockBlob(null);

        // when
        GcsSource source = new GcsSource(blob);

        // then no exception is thrown and the contentType key is simply absent
        assertThat(source.metadata().getString("contentType")).isNull();
        assertThat(source.metadata().containsKey("contentType")).isFalse();
        // other metadata is still populated
        assertThat(source.metadata().getString("source")).isEqualTo("gs://my-bucket/my-object.txt");
        assertThat(source.metadata().getString("bucket")).isEqualTo("my-bucket");
        assertThat(source.metadata().getString("name")).isEqualTo("my-object.txt");
    }

    @Test
    void should_populate_content_type_when_present() {
        // given
        Blob blob = mockBlob("text/plain");

        // when
        GcsSource source = new GcsSource(blob);

        // then
        assertThat(source.metadata().getString("contentType")).isEqualTo("text/plain");
    }

    @Test
    void should_expose_metadata_keys_as_public_constants() {
        // constant values must stay unchanged for backward compatibility with persisted metadata
        assertThat(SOURCE).isEqualTo("source");
        assertThat(BUCKET).isEqualTo("bucket");
        assertThat(NAME).isEqualTo("name");
        assertThat(CONTENT_TYPE).isEqualTo("contentType");
        assertThat(SIZE).isEqualTo("size");
        assertThat(CREATE_TIME).isEqualTo("createTime");
        assertThat(UPDATE_TIME).isEqualTo("updateTime");
    }

    @Test
    void should_populate_all_documented_keys_with_documented_types() {
        // given
        Blob blob = mockBlob("text/plain");

        // when
        Metadata metadata = new GcsSource(blob).metadata();

        // then
        assertThat(metadata.toMap())
                .containsOnlyKeys(SOURCE, BUCKET, NAME, CONTENT_TYPE, SIZE, CREATE_TIME, UPDATE_TIME);
        assertThat(metadata.getString(SOURCE)).isEqualTo("gs://my-bucket/my-object.txt");
        assertThat(metadata.getString(BUCKET)).isEqualTo("my-bucket");
        assertThat(metadata.getString(NAME)).isEqualTo("my-object.txt");
        assertThat(metadata.getString(CONTENT_TYPE)).isEqualTo("text/plain");
        assertThat(metadata.getLong(SIZE)).isEqualTo(123L);
        assertThat(metadata.getString(CREATE_TIME)).isEqualTo("2024-01-01T00:00Z");
        assertThat(metadata.getString(UPDATE_TIME)).isEqualTo("2024-01-02T00:00Z");
    }
}
