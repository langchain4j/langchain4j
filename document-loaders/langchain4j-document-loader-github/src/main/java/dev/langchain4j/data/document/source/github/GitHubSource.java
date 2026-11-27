package dev.langchain4j.data.document.source.github;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import java.io.IOException;
import java.io.InputStream;
import org.kohsuke.github.GHContent;

public class GitHubSource implements DocumentSource {

    public static final String GITHUB_GIT_URL = "github_git_url";
    public static final String GITHUB_DOWNLOAD_URL = "github_download_url";
    public static final String GITHUB_HTML_URL = "github_html_url";
    public static final String GITHUB_URL = "github_url";
    public static final String GITHUB_FILE_NAME = "github_file_name";
    public static final String GITHUB_FILE_PATH = "github_file_path";
    public static final String GITHUB_FILE_SHA = "github_file_sha";
    public static final String GITHUB_FILE_SIZE = "github_file_size";
    public static final String GITHUB_FILE_ENCODING = "github_file_encoding";

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

    /**
     * Returns the metadata of the GitHub file. All values are {@link String}s.
     * <ul>
     *     <li>{@link #GITHUB_GIT_URL}: the Git API URL of the file</li>
     *     <li>{@link #GITHUB_DOWNLOAD_URL}: the raw download URL of the file (optional: absent when not available)</li>
     *     <li>{@link #GITHUB_HTML_URL}: the URL of the file on github.com</li>
     *     <li>{@link #GITHUB_URL}: the contents API URL of the file</li>
     *     <li>{@link #GITHUB_FILE_NAME}: the name of the file</li>
     *     <li>{@link #GITHUB_FILE_PATH}: the path of the file in the repository</li>
     *     <li>{@link #GITHUB_FILE_SHA}: the SHA of the file</li>
     *     <li>{@link #GITHUB_FILE_SIZE}: the size of the file in bytes</li>
     *     <li>{@link #GITHUB_FILE_ENCODING}: the encoding of the file content</li>
     * </ul>
     *
     * @return the metadata of the GitHub file
     */
    @Override
    public Metadata metadata() {
        Metadata metadata = new Metadata();
        metadata.put(GITHUB_GIT_URL, content.getGitUrl());
        try {
            metadata.put(GITHUB_DOWNLOAD_URL, content.getDownloadUrl());
        } catch (IOException e) {
            // Ignore if download_url is not available
        }
        metadata.put(GITHUB_HTML_URL, content.getHtmlUrl());
        metadata.put(GITHUB_URL, content.getUrl());
        metadata.put(GITHUB_FILE_NAME, content.getName());
        metadata.put(GITHUB_FILE_PATH, content.getPath());
        metadata.put(GITHUB_FILE_SHA, content.getSha());
        metadata.put(GITHUB_FILE_SIZE, Long.toString(content.getSize()));
        metadata.put(GITHUB_FILE_ENCODING, content.getEncoding());
        return metadata;
    }
}
