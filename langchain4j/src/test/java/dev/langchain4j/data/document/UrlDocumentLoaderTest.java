package dev.langchain4j.data.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlDocumentLoaderTest {

    @Test
    void should_load_text_document() {
        String url = "https://raw.githubusercontent.com/langchain4j/langchain4j/main/langchain4j/src/test/resources/test-file-utf8.txt";

        Document document = UrlDocumentLoader.load(url);

        assertThat(document.text()).isEqualTo("test\ncontent");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("url")).isEqualTo(url);
    }
}