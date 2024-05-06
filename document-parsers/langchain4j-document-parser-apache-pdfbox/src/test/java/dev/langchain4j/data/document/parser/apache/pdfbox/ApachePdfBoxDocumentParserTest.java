package dev.langchain4j.data.document.parser.apache.pdfbox;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.DocumentParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApachePdfBoxDocumentParserTest {

    @Test
    void should_parse_pdf_file() {

        DocumentParser parser = new ApachePdfBoxDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.pdf");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata().asMap()).isEmpty();
    }

    @Test
    void should_throw_BlankDocumentException() {

        DocumentParser parser = new ApachePdfBoxDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("blank-file.pdf");

        assertThatThrownBy(() -> parser.parse(inputStream))
                .isExactlyInstanceOf(BlankDocumentException.class);
    }
}