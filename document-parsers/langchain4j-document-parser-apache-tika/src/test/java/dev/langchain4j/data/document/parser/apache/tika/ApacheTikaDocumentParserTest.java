package dev.langchain4j.data.document.parser.apache.tika;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.DocumentParser;
import org.apache.tika.parser.AutoDetectParser;
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
        assertThat(document.metadata().asMap()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "test-file.xls",
            "test-file.xlsx"
    })
    void should_parse_xls_files(String fileName) {

        DocumentParser parser = new ApacheTikaDocumentParser(new AutoDetectParser(), null, null, null);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        Document document = parser.parse(inputStream);

        assertThat(document.text())
                .isEqualToIgnoringWhitespace("Sheet1\ntest content\nSheet2\ntest content");
        assertThat(document.metadata().asMap()).isEmpty();
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