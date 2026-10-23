package dev.langchain4j.data.document.source.gcs;

import com.google.cloud.storage.Blob;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import java.io.InputStream;
import java.nio.channels.Channels;

public class GcsSource implements DocumentSource {

    public static final String SOURCE = "source";
    public static final String BUCKET = "bucket";
    public static final String NAME = "name";
    public static final String CONTENT_TYPE = "contentType";
    public static final String SIZE = "size";
    public static final String CREATE_TIME = "createTime";
    public static final String UPDATE_TIME = "updateTime";

    private final InputStream inputStream;
    private final Metadata metadata;

    public GcsSource(Blob blob) {
        this.inputStream = Channels.newInputStream(blob.reader());
        this.metadata = getMetadataForBlob(blob);
    }

    @Override
    public InputStream inputStream() {
        return inputStream;
    }

    /**
     * Returns the metadata of the Google Cloud Storage object.
     * <ul>
     *     <li>{@link #SOURCE} ({@link String}): the {@code gs://bucket/name} URI of the object</li>
     *     <li>{@link #BUCKET} ({@link String}): the name of the bucket</li>
     *     <li>{@link #NAME} ({@link String}): the name of the object</li>
     *     <li>{@link #CONTENT_TYPE} ({@link String}): the content type of the object (optional: absent when not set)</li>
     *     <li>{@link #SIZE} ({@link Long}): the size of the object in bytes</li>
     *     <li>{@link #CREATE_TIME} ({@link String}): the creation time of the object, in ISO-8601 format</li>
     *     <li>{@link #UPDATE_TIME} ({@link String}): the last update time of the object, in ISO-8601 format</li>
     * </ul>
     *
     * @return the metadata of the Google Cloud Storage object
     */
    @Override
    public Metadata metadata() {
        return metadata;
    }

    private static Metadata getMetadataForBlob(Blob blob) {
        Metadata metadata = new Metadata();
        metadata.put(SOURCE, "gs://" + blob.getBucket() + "/" + blob.getName());
        metadata.put(BUCKET, blob.getBucket());
        metadata.put(NAME, blob.getName());
        if (blob.getContentType() != null) {
            metadata.put(CONTENT_TYPE, blob.getContentType());
        }
        metadata.put(SIZE, blob.getSize());
        metadata.put(CREATE_TIME, blob.getCreateTimeOffsetDateTime().toString());
        metadata.put(UPDATE_TIME, blob.getUpdateTimeOffsetDateTime().toString());
        return metadata;
    }
}
