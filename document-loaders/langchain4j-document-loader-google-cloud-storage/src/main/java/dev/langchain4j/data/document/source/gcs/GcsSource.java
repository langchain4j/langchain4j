package dev.langchain4j.data.document.source.gcs;

import com.google.cloud.storage.Blob;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;

import java.io.InputStream;
import java.nio.channels.Channels;

public class GcsSource implements DocumentSource {

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

    @Override
    public Metadata metadata() {
        return metadata;
    }

    private static Metadata getMetadataForBlob(Blob blob) {
        Metadata metadata = new Metadata();
        metadata.put("source", "gs://" + blob.getBucket() + "/" + blob.getName());
        metadata.put("bucket", blob.getBucket());
        metadata.put("name", blob.getName());
        metadata.put("contentType", blob.getContentType());
        metadata.put("size", blob.getSize());
        metadata.put("createTime", blob.getCreateTimeOffsetDateTime().toString());
        metadata.put("updateTime", blob.getUpdateTimeOffsetDateTime().toString());
        return metadata;
    }
}
