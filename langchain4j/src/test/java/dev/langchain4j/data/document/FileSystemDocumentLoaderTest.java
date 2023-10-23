package dev.langchain4j.data.document;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static dev.langchain4j.data.document.Document.DOCUMENT_TYPE;
import static dev.langchain4j.data.document.Document.FILE_NAME;
import static dev.langchain4j.data.document.DocumentType.UNKNOWN;
import static dev.langchain4j.data.document.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.data.document.FileSystemDocumentLoader.loadDocuments;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

class FileSystemDocumentLoaderTest {

    @Test
    void should_load_text_document() {

        Document document = loadDocument(toPath("test-file-utf8.txt"));

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file-utf8.txt");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }

    @Test
    void should_load_pdf_document() {

        Document document = loadDocument(toPath("test-file.pdf"));

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file.pdf");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }

    @Test
    void should_load_ppt_document() {

        Document document = loadDocument(toPath("test-file.ppt"));

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file.ppt");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }

    @Test
    void should_load_pptx_document() {

        Document document = loadDocument(toPath("test-file.pptx"));

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file.pptx");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }

    @Test
    void should_load_doc_document() {

        Document document = loadDocument(toPath("test-file.doc"));

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file.doc");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }

    @Test
    void should_load_docx_document() {

        Document document = loadDocument(toPath("test-file.docx"));

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file.docx");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }

    @Test
    void should_load_xls_document() {

        Document document = loadDocument(toPath("test-file.xls"));

        assertThat(document.text()).isEqualToIgnoringWhitespace("Sheet1\ntest content\nSheet2\ntest content");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file.xls");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }

    @Test
    void should_load_xlsx_document() {

        Document document = loadDocument(toPath("test-file.xlsx"));

        assertThat(document.text()).isEqualToIgnoringWhitespace("Sheet1\ntest content\nSheet2\ntest content");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file.xlsx");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }

    @Test
    void should_load_documents_from_directory_including_unknown_document_types() {

        String userDir = System.getProperty("user.dir");
        Path resourceDirectory = Paths.get(userDir, "langchain4j/src/test/resources");
        if (!Files.exists(resourceDirectory)) {
            resourceDirectory = Paths.get(userDir, "src/test/resources");
        }

        List<Document> documents = loadDocuments(resourceDirectory);
        assertThat(documents).hasSize(10);

        List<Document> documentsWithUnknownType = documents.stream()
                .filter(document -> document.metadata(DOCUMENT_TYPE).equals(UNKNOWN.toString()))
                .collect(toList());
        assertThat(documentsWithUnknownType).hasSize(1);
        assertThat(documentsWithUnknownType.get(0).metadata(FILE_NAME)).isEqualTo("test-file.banana");
    }

    private Path toPath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}