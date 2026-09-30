package dev.langchain4j.data.document.loader.github;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;

class GitHubDocumentLoaderTest {

    @Test
    void loadDocument_wraps_io_error_with_file_url_in_message() throws IOException {
        String htmlUrl = "https://github.com/owner/repo/blob/main/README.md";

        GHContent content = Mockito.mock(GHContent.class);
        when(content.isFile()).thenReturn(true);
        when(content.read()).thenThrow(new IOException("boom"));
        when(content.getHtmlUrl()).thenReturn(htmlUrl);

        GHRepository repository = Mockito.mock(GHRepository.class);
        when(repository.getFileContent("README.md", "main")).thenReturn(content);

        GitHub gitHub = Mockito.mock(GitHub.class);
        when(gitHub.getRepository("owner/repo")).thenReturn(repository);

        GitHubDocumentLoader loader = new GitHubDocumentLoader(gitHub);
        DocumentParser parser = new TextDocumentParser();

        assertThatThrownBy(() -> loader.loadDocument("owner", "repo", "main", "README.md", parser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(htmlUrl)
                .hasMessageNotContaining("{}");
    }
}
