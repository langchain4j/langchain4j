package dev.langchain4j.data.document.parser;

import java.io.InputStream;


import org.junit.jupiter.api.Test;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;

import static dev.langchain4j.data.document.DocumentType.PPT;
import static dev.langchain4j.data.document.DocumentType.PPTX;
import static dev.langchain4j.data.document.DocumentType.XLS;
import static dev.langchain4j.data.document.DocumentType.XLSX;
import static dev.langchain4j.data.document.DocumentType.DOC;
import static dev.langchain4j.data.document.DocumentType.DOCX;
import static org.assertj.core.api.Assertions.assertThat;

public class MSOfficeDocumentParserTest {
    
    @Test
    void should_parse_ppt_file() {

        DocumentParser parser = new MSOfficeDocumentParser(PPT);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.ppt");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata().get("document_type")).isEqualTo("PPT");
    }

    @Test
    void should_parse_pptx_file() {

        DocumentParser parser = new MSOfficeDocumentParser(PPTX);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.pptx");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata().get("document_type")).isEqualTo("PPTX");
    }

    @Test
    void should_parse_doc_file() {

        DocumentParser parser = new MSOfficeDocumentParser(DOC);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.doc");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata().get("document_type")).isEqualTo("DOC");
    }

    @Test
    void should_parse_docx_file() {

        DocumentParser parser = new MSOfficeDocumentParser(DOCX);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.docx");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata().get("document_type")).isEqualTo("DOCX");
    }

    @Test
    void should_parse_xls_file() {

        DocumentParser parser = new MSOfficeDocumentParser(XLS);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.xls");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("Sheet1\ntest content\nSheet2\ntest content");
        assertThat(document.metadata().get("document_type")).isEqualTo("XLS");
    }

    @Test
    void should_parse_xlsx_file() {

        DocumentParser parser = new MSOfficeDocumentParser(XLSX);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.xlsx");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("Sheet1\ntest content\nSheet2\ntest content");
        assertThat(document.metadata().get("document_type")).isEqualTo("XLSX");
    }
}
