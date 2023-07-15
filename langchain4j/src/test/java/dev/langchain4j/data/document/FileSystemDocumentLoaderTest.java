package dev.langchain4j.data.document;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static dev.langchain4j.data.document.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.data.document.FileSystemDocumentLoader.loadDocuments;
import static org.assertj.core.api.Assertions.assertThat;

class FileSystemDocumentLoaderTest {

    @Test
        // TODO This test fails when running it directly in IDE, but works when running in maven
    void should_load_text_document() {

        Document document = loadDocument("src/test/resources/test-file-utf8.txt");

        assertThat(document.text()).isEqualTo("test\ncontent");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file-utf8.txt");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }

    @Test
        // TODO This test fails when running it directly in IDE, but works when running in maven
    void should_load_pdf_document() {

        Document document = loadDocument("src/test/resources/test-file.pdf");

        assertThat(document.text()).isEqualToIgnoringWhitespace("test\ncontent");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file.pdf");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }

    @Test
        // TODO This test fails when running it directly in IDE, but works when running in maven
    void should_load_documents_ignoring_unsupported_document_types() {

        List<Document> documents = loadDocuments("src/test/resources");

        assertThat(documents).hasSize(3);
    }
}