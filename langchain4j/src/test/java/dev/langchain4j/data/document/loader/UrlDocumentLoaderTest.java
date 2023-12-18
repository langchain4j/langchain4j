package dev.langchain4j.data.document.loader;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlDocumentLoaderTest {

    @Test
    void should_load_text_document() {
        String url = "https://raw.githubusercontent.com/langchain4j/langchain4j/main/langchain4j/src/test/resources/test-file-utf8.txt";

        Document document = UrlDocumentLoader.load(url, new TextDocumentParser());

        assertThat(document.text()).isEqualTo("test\ncontent");
        assertThat(document.metadata("url")).isEqualTo(url);
    }
}