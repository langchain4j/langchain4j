package dev.langchain4j.data.document.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.source.FileSystemSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

class TextDocumentParserTest {

    @Test
        // TODO This test fails when running it directly in IDE, but works when running in maven
    void should_parse_with_utf8_charset_by_default() throws IOException {

        FileSystemSource source = FileSystemSource.from(Paths.get("src/test/resources/test-file-utf8.txt"));
        TextDocumentParser parser = new TextDocumentParser();

        Document document = parser.parse(source.inputStream());

        assertThat(document.text()).isEqualTo("test\ncontent");
    }

    @Test
        // TODO This test fails when running it directly in IDE, but works when running in maven
    void should_parse_with_specified_charset() throws IOException {

        FileSystemSource source = FileSystemSource.from(Paths.get("src/test/resources/test-file-iso-8859-1.txt"));
        TextDocumentParser parser = new TextDocumentParser(ISO_8859_1);

        Document document = parser.parse(source.inputStream());

        assertThat(document.text()).isEqualTo("test\ncontent");
    }
}