package dev.langchain4j.data.document.source.github;

import static dev.langchain4j.data.document.source.github.GitHubSource.GITHUB_DOWNLOAD_URL;
import static dev.langchain4j.data.document.source.github.GitHubSource.GITHUB_FILE_ENCODING;
import static dev.langchain4j.data.document.source.github.GitHubSource.GITHUB_FILE_NAME;
import static dev.langchain4j.data.document.source.github.GitHubSource.GITHUB_FILE_PATH;
import static dev.langchain4j.data.document.source.github.GitHubSource.GITHUB_FILE_SHA;
import static dev.langchain4j.data.document.source.github.GitHubSource.GITHUB_FILE_SIZE;
import static dev.langchain4j.data.document.source.github.GitHubSource.GITHUB_GIT_URL;
import static dev.langchain4j.data.document.source.github.GitHubSource.GITHUB_HTML_URL;
import static dev.langchain4j.data.document.source.github.GitHubSource.GITHUB_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Metadata;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHContent;

class GitHubSourceTest {

    private static GHContent mockContent() throws IOException {
        GHContent content = mock(GHContent.class);
        when(content.read()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(content.getGitUrl()).thenReturn("https://api.github.com/repos/owner/repo/git/blobs/abc123");
        when(content.getHtmlUrl()).thenReturn("https://github.com/owner/repo/blob/main/docs/README.md");
        when(content.getUrl()).thenReturn("https://api.github.com/repos/owner/repo/contents/docs/README.md");
        when(content.getName()).thenReturn("README.md");
        when(content.getPath()).thenReturn("docs/README.md");
        when(content.getSha()).thenReturn("abc123");
        when(content.getSize()).thenReturn(42L);
        when(content.getEncoding()).thenReturn("base64");
        return content;
    }

    @Test
    void should_expose_metadata_keys_as_public_constants() {
        // constant values must stay unchanged for backward compatibility with persisted metadata
        assertThat(GITHUB_GIT_URL).isEqualTo("github_git_url");
        assertThat(GITHUB_DOWNLOAD_URL).isEqualTo("github_download_url");
        assertThat(GITHUB_HTML_URL).isEqualTo("github_html_url");
        assertThat(GITHUB_URL).isEqualTo("github_url");
        assertThat(GITHUB_FILE_NAME).isEqualTo("github_file_name");
        assertThat(GITHUB_FILE_PATH).isEqualTo("github_file_path");
        assertThat(GITHUB_FILE_SHA).isEqualTo("github_file_sha");
        assertThat(GITHUB_FILE_SIZE).isEqualTo("github_file_size");
        assertThat(GITHUB_FILE_ENCODING).isEqualTo("github_file_encoding");
    }

    @Test
    void should_populate_all_documented_keys() throws IOException {
        // given
        GHContent content = mockContent();
        when(content.getDownloadUrl()).thenReturn("https://raw.githubusercontent.com/owner/repo/main/docs/README.md");

        // when
        Metadata metadata = new GitHubSource(content).metadata();

        // then
        assertThat(metadata.toMap())
                .containsOnlyKeys(
                        GITHUB_GIT_URL,
                        GITHUB_DOWNLOAD_URL,
                        GITHUB_HTML_URL,
                        GITHUB_URL,
                        GITHUB_FILE_NAME,
                        GITHUB_FILE_PATH,
                        GITHUB_FILE_SHA,
                        GITHUB_FILE_SIZE,
                        GITHUB_FILE_ENCODING);
        assertThat(metadata.getString(GITHUB_GIT_URL))
                .isEqualTo("https://api.github.com/repos/owner/repo/git/blobs/abc123");
        assertThat(metadata.getString(GITHUB_DOWNLOAD_URL))
                .isEqualTo("https://raw.githubusercontent.com/owner/repo/main/docs/README.md");
        assertThat(metadata.getString(GITHUB_HTML_URL))
                .isEqualTo("https://github.com/owner/repo/blob/main/docs/README.md");
        assertThat(metadata.getString(GITHUB_URL))
                .isEqualTo("https://api.github.com/repos/owner/repo/contents/docs/README.md");
        assertThat(metadata.getString(GITHUB_FILE_NAME)).isEqualTo("README.md");
        assertThat(metadata.getString(GITHUB_FILE_PATH)).isEqualTo("docs/README.md");
        assertThat(metadata.getString(GITHUB_FILE_SHA)).isEqualTo("abc123");
        assertThat(metadata.getString(GITHUB_FILE_SIZE)).isEqualTo("42");
        assertThat(metadata.getString(GITHUB_FILE_ENCODING)).isEqualTo("base64");
    }

    @Test
    void should_omit_download_url_when_not_available() throws IOException {
        // given
        GHContent content = mockContent();
        when(content.getDownloadUrl()).thenThrow(new IOException("download_url is not available"));

        // when
        Metadata metadata = new GitHubSource(content).metadata();

        // then
        assertThat(metadata.containsKey(GITHUB_DOWNLOAD_URL)).isFalse();
        assertThat(metadata.getString(GITHUB_FILE_PATH)).isEqualTo("docs/README.md");
    }
}
