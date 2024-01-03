package dev.langchain4j.data.document.loader.github;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubDocumentLoaderIT {

    private static final String TEST_OWNER = "langchain4j";
    private static final String TEST_REPO = "langchain4j";

    GitHubDocumentLoader loader;

    DocumentParser parser = new TextDocumentParser();

    @BeforeEach
    public void beforeEach() throws IOException {
        loader = GitHubDocumentLoader.builder()
                .gitHubToken(System.getenv("GITHUB_TOKEN"))
                .build();
    }

    @Test
    public void should_load_file() throws IOException {
        Document document = loader.loadDocument(TEST_OWNER, TEST_REPO, "main", "pom.xml", parser);

        assertThat(document.text()).contains("<groupId>dev.langchain4j</groupId>");
        assertThat(document.metadata().asMap().size()).isEqualTo(10);
        assertThat(document.metadata("github_git_url")).startsWith("https://api.github.com/repos/langchain4j/langchain4j");
    }

    @Test
    public void should_load_repository() throws IOException {
        List<Document> documents = loader.loadDocuments(TEST_OWNER, TEST_REPO, "main", parser);

        assertThat(documents.size()).isGreaterThan(1);
    }
}
