package dev.langchain4j.data.document.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static dev.langchain4j.data.document.DocumentType.*;
import static org.assertj.core.api.Assertions.assertThat;

public class MsOfficeDocumentParserTest {

    @Test
    void should_parse_ppt_file() {

        DocumentParser parser = new MsOfficeDocumentParser(PPT);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.ppt");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata("document_type")).isEqualTo("PPT");
    }

    @Test
    void should_parse_pptx_file() {

        DocumentParser parser = new MsOfficeDocumentParser(PPT);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.pptx");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata("document_type")).isEqualTo("PPT");
    }

    @Test
    void should_parse_doc_file() {

        DocumentParser parser = new MsOfficeDocumentParser(DOC);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.doc");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata("document_type")).isEqualTo("DOC");
    }

    @Test
    void should_parse_docx_file() {

        DocumentParser parser = new MsOfficeDocumentParser(DOC);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.docx");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata("document_type")).isEqualTo("DOC");
    }

    @Test
    void should_parse_xls_file() {

        DocumentParser parser = new MsOfficeDocumentParser(XLS);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.xls");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("Sheet1\ntest content\nSheet2\ntest content");
        assertThat(document.metadata("document_type")).isEqualTo("XLS");
    }

    @Test
    void should_parse_xlsx_file() {

        DocumentParser parser = new MsOfficeDocumentParser(XLS);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.xlsx");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("Sheet1\ntest content\nSheet2\ntest content");
        assertThat(document.metadata("document_type")).isEqualTo("XLS");
    }
}
