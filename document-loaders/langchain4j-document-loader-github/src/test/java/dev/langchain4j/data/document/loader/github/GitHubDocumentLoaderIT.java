package dev.langchain4j.data.document.loader.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.kohsuke.github.GHFileNotFoundException;

@EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
class GitHubDocumentLoaderIT {

    private static final String TEST_OWNER = "langchain4j";
    private static final String TEST_REPO = "langchain4j";

    GitHubDocumentLoader loader;

    DocumentParser parser = new TextDocumentParser();

    @BeforeEach
    void beforeEach() {
        loader = GitHubDocumentLoader.builder()
                .gitHubToken(System.getenv("GITHUB_TOKEN"))
                .build();
    }

    @Test
    void should_load_file() {
        Document document = loader.loadDocument(TEST_OWNER, TEST_REPO, "main", "pom.xml", parser);

        assertThat(document.text()).contains("<groupId>dev.langchain4j</groupId>");
        assertThat(document.metadata().toMap()).hasSize(9);
        assertThat(document.metadata().getString("github_git_url"))
                .startsWith("https://api.github.com/repos/langchain4j/langchain4j");
    }

    @Test
    void manage_exception_on_wrong_repository() {
        try {
            loader.loadDocument(TEST_OWNER, "repository_that_do_not_exist", "main", "pom.xml", parser);
            fail("Should throw an exception");
        } catch (Exception e) {
            assertThat(e).isInstanceOf(RuntimeException.class);
            assertThat(e.getCause()).isInstanceOf(GHFileNotFoundException.class);
        }
    }

    @Test
    void manage_exception_on_wrong_file() {
        try {
            loader.loadDocument(TEST_OWNER, TEST_REPO, "main", "file_that_do_not_exist.txt", parser);
            fail("Should throw an exception");
        } catch (Exception e) {
            assertThat(e).isInstanceOf(RuntimeException.class);
            assertThat(e.getCause()).isInstanceOf(GHFileNotFoundException.class);
        }
    }

    @Test
    void should_load_repository() {
        List<Document> documents = loader.loadDocuments(TEST_OWNER, "awesome-langchain4j", "main", parser);

        assertThat(documents.size()).isGreaterThan(1);
    }
}
