package dev.langchain4j.data.document.source;

import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;

import java.io.IOException;
import java.io.InputStream;

public class S3Source implements DocumentSource {

    private static final String SOURCE = "source";

    private InputStream inputStream;

    private final String bucket;

    private final String key;

    public S3Source(String bucket, String key, InputStream inputStream) {
        this.inputStream = inputStream;
        this.bucket = bucket;
        this.key = key;
    }

    @Override
    public InputStream inputStream() throws IOException {
        return inputStream;
    }

    @Override
    public Metadata metadata() {
        return new Metadata()
                .add(SOURCE, String.format("s3://%s/%s", bucket, key));
    }
}
