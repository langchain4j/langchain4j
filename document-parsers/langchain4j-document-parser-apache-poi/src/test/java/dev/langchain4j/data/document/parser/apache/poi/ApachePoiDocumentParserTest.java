package dev.langchain4j.data.document.parser.apache.poi;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ApachePoiDocumentParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "test-file.doc",
            "test-file.docx",
            "test-file.ppt",
            "test-file.pptx"
    })
    void should_parse_doc_and_ppt_files(String fileName) {

        DocumentParser parser = new ApachePoiDocumentParser();
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

        DocumentParser parser = new ApachePoiDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        Document document = parser.parse(inputStream);

        assertThat(document.text())
                .isEqualToIgnoringWhitespace("Sheet1\ntest content\nSheet2\ntest content");
        assertThat(document.metadata().asMap()).isEmpty();
    }
}
