package dev.langchain4j.data.document.source.github;

import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import org.kohsuke.github.GHContent;

import java.io.IOException;
import java.io.InputStream;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class GitHubSource implements DocumentSource {

    private final InputStream inputStream;

    private final GHContent content;

    public GitHubSource(GHContent content) throws IOException {
        this.content = ensureNotNull(content, "content");
        this.inputStream = ensureNotNull(content.read(), "inputStream");
    }

    @Override
    public InputStream inputStream() {
        return inputStream;
    }

    @Override
    public Metadata metadata() {
        Metadata metadata = new Metadata();
        metadata.add("git_url", content.getGitUrl());
        try {
            metadata.add("download_url", content.getDownloadUrl());
        } catch (IOException e) {
            // Ignore if download_url is not available
        }
        metadata.add("html_url", content.getHtmlUrl());
        metadata.add("name", content.getName());
        metadata.add("path", content.getPath());
        metadata.add("sha", content.getSha());
        metadata.add("size", Long.toString(content.getSize()));
        metadata.add("type", content.getType());
        metadata.add("url", content.getUrl());
        metadata.add("encoding", content.getEncoding());
        return metadata;
    }
}
