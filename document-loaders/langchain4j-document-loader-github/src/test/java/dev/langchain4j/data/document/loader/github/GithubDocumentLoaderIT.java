package dev.langchain4j.data.document.loader.github;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class GithubDocumentLoaderIT {

    private static final String TEST_OWNER = "langchain4j";
    private static final String TEST_REPO = "langchain4j";

    GithubDocumentLoader loader;

    DocumentParser parser = new TextDocumentParser();

    @BeforeEach
    public void beforeEach() {
        loader = GithubDocumentLoader.builder()
                .build();
    }

    @Test
    public void should_load_file() throws IOException {
        Document document = loader.loadDocument(TEST_OWNER, TEST_REPO, "main", "pom.xml", parser);

        assertThat(document.text()).contains("<groupId>dev.langchain4j</groupId>");
        assertThat(document.metadata().asMap().size()).isEqualTo(1);
        assertThat(document.metadata("git_url")).startsWith("https://api.github.com/repos/langchain4j/langchain4j");
    }
}