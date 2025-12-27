package dev.langchain4j.data.document.source.gitlab;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class GitLabSource implements DocumentSource {

    private final byte[] bytes;
    private final Metadata metadata;

    public GitLabSource(byte[] bytes, Metadata metadata) {
        this.bytes = ensureNotNull(bytes, "bytes");
        this.metadata = ensureNotNull(metadata, "metadata");
    }

    @Override
    public InputStream inputStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public Metadata metadata() {
        return metadata;
    }
}
