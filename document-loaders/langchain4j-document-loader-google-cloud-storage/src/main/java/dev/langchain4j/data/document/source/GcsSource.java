package dev.langchain4j.data.document.source;

import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;

import java.io.IOException;
import java.io.InputStream;

public class GcsSource implements DocumentSource {

    private final String bucket;
    private final String objectName;
    private final InputStream inputStream;
    private final Metadata metadata;

    public GcsSource(String bucket, String objectName, InputStream inputStream, Metadata metadata) {
        this.bucket = bucket;
        this.objectName = objectName;
        this.inputStream = inputStream;
        this.metadata = metadata;
    }

    @Override
    public InputStream inputStream() throws IOException {
        return inputStream;
    }

    @Override
    public Metadata metadata() {
        return metadata;
    }
}
