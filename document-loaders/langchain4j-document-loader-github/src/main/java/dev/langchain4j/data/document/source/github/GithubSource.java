package dev.langchain4j.data.document.source.github;

import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;

import java.io.InputStream;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class GithubSource implements DocumentSource {

    public static final String SOURCE = "source";

    private final InputStream inputStream;

    public GithubSource(InputStream inputStream) {
        this.inputStream = ensureNotNull(inputStream, "inputStream");
    }

    @Override
    public InputStream inputStream() {
        return inputStream;
    }

    @Override
    public Metadata metadata() {
        return Metadata.from(SOURCE, "");
    }
}
