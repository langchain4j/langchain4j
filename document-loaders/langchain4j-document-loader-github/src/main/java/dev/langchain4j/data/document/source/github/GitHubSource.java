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
        metadata.put("github_git_url", content.getGitUrl());
        try {
            metadata.put("github_download_url", content.getDownloadUrl());
        } catch (IOException e) {
            // Ignore if download_url is not available
        }
        metadata.put("github_html_url", content.getHtmlUrl());
        metadata.put("github_url", content.getUrl());
        metadata.put("github_file_name", content.getName());
        metadata.put("github_file_path", content.getPath());
        metadata.put("github_file_sha", content.getSha());
        metadata.put("github_file_size", Long.toString(content.getSize()));
        metadata.put("github_file_encoding", content.getEncoding());
        return metadata;
    }
}
