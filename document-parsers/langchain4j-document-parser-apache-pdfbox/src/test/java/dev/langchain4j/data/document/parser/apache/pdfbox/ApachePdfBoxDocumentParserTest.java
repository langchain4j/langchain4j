package dev.langchain4j.data.document.parser.apache.pdfbox;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApachePdfBoxDocumentParserTest {

    @Test
    void should_parse_pdf_file() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.pdf")) {
            DocumentParser parser = new ApachePdfBoxDocumentParser();
            Document document = parser.parse(inputStream);

            assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
            assertThat(document.metadata().toMap()).isEmpty();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void should_throw_BlankDocumentException() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("blank-file.pdf")) {
            DocumentParser parser = new ApachePdfBoxDocumentParser();
            assertThatThrownBy(() -> parser.parse(inputStream))
                    .isExactlyInstanceOf(BlankDocumentException.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void should_parse_pdf_file_include_metadata() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.pdf")) {
            DocumentParser parser = new ApachePdfBoxDocumentParser(true);
            Document document = parser.parse(inputStream);

            assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
            assertThat(document.metadata().toMap())
                    .containsEntry("Author", "ljuba")
                    .containsEntry("Creator", "WPS Writer")
                    .containsEntry("CreationDate", "D:20230608171011+15'10'")
                    .containsEntry("SourceModified", "D:20230608171011+15'10'");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}