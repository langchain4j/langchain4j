package dev.langchain4j.data.document.parser.apache.tika;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.apache.tika.parser.AutoDetectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApacheTikaDocumentParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "test-file.doc",
            "test-file.docx",
            "test-file.ppt",
            "test-file.pptx",
            "test-file.pdf"
    })
    void should_parse_doc_ppt_and_pdf_files(String fileName) {

        DocumentParser parser = new ApacheTikaDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata().toMap()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "test-file.xls",
            "test-file.xlsx"
    })
    void should_parse_xls_files(String fileName) {

        DocumentParser parser = new ApacheTikaDocumentParser(AutoDetectParser::new, null, null, null);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        Document document = parser.parse(inputStream);

        assertThat(document.text())
                .isEqualToIgnoringWhitespace("Sheet1\ntest content\nSheet2\ntest content");
        assertThat(document.metadata().toMap()).isEmpty();
    }

    @Test
    void should_parse_files_stateless() {

        DocumentParser parser = new ApacheTikaDocumentParser();
        InputStream inputStream1 = getClass().getClassLoader().getResourceAsStream("test-file.xls");
        InputStream inputStream2 = getClass().getClassLoader().getResourceAsStream("test-file.xls");

        Document document1 = parser.parse(inputStream1);
        Document document2 = parser.parse(inputStream2);

        assertThat(document1.text())
                .isEqualToIgnoringWhitespace("Sheet1\ntest content\nSheet2\ntest content");
        assertThat(document2.text())
                .isEqualToIgnoringWhitespace("Sheet1\ntest content\nSheet2\ntest content");
        assertThat(document1.metadata().toMap()).isEmpty();
        assertThat(document2.metadata().toMap()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "empty-file.txt",
            "blank-file.txt",
            "blank-file.docx",
            "blank-file.pptx"
            // "blank-file.xlsx" TODO
    })
    void should_throw_BlankDocumentException(String fileName) {

        DocumentParser parser = new ApacheTikaDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        assertThatThrownBy(() -> parser.parse(inputStream))
                .isExactlyInstanceOf(BlankDocumentException.class);
    }
}