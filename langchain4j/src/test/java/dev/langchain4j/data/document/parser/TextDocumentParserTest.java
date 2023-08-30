package dev.langchain4j.data.document.parser;

import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static dev.langchain4j.data.document.DocumentType.TXT;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

class TextDocumentParserTest {

    @Test
    void should_parse_with_utf8_charset_by_default() {

        TextDocumentParser parser = new TextDocumentParser(TXT);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file-utf8.txt");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata("document_type")).isEqualTo("TXT");
    }

    @Test
    void should_parse_with_specified_charset() {

        TextDocumentParser parser = new TextDocumentParser(TXT, ISO_8859_1);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file-iso-8859-1.txt");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata("document_type")).isEqualTo("TXT");
    }
}