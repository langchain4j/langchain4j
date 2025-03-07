package dev.langchain4j.data.document.source.alibaba.oss;

import static java.lang.String.format;

import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import java.io.IOException;
import java.io.InputStream;

public class AlibabaOssSource implements DocumentSource {
    public static final String SOURCE = "source";

    private final InputStream inputStream;
    private final String bucket;
    private final String key;

    public AlibabaOssSource(InputStream inputStream, String bucket, String key) {
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
        return Metadata.from(SOURCE, format("oss://%s/%s", bucket, key));
    }
}
