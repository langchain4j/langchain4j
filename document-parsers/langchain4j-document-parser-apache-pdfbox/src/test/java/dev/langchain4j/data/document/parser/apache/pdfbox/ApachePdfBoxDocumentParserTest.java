package dev.langchain4j.data.document.parser.apache.pdfbox;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ApachePdfBoxDocumentParserTest {

    @Test
    void should_parse_pdf_file() {

        DocumentParser parser = new ApachePdfBoxDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.pdf");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata().asMap()).isEmpty();
    }
}