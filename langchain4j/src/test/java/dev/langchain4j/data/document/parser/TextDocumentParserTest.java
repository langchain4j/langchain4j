package dev.langchain4j.data.document.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.DocumentParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.*;

class TextDocumentParserTest {

    @Test
    void should_parse_with_utf8_charset_by_default() {

        TextDocumentParser parser = new TextDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file-utf8.txt");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
    }

    @Test
    void should_parse_with_specified_charset() {

        TextDocumentParser parser = new TextDocumentParser(ISO_8859_1);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file-iso-8859-1.txt");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "empty-file.txt",
            "blank-file.txt"
    })
    void should_throw_BlankDocumentException(String fileName) {

        DocumentParser parser = new TextDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        assertThatThrownBy(() -> parser.parse(inputStream))
                .isExactlyInstanceOf(BlankDocumentException.class);
    }

    @Test
    void should_wrap_input_stream_errors() {
        InputStream badStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("test exception");
            }
        };

        TextDocumentParser parser = new TextDocumentParser();

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> parser.parse(badStream))
                .withCauseInstanceOf(IOException.class)
                .withMessageContaining("test exception");
    }
}