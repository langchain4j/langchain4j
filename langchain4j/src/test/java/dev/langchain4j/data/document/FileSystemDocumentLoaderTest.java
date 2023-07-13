package dev.langchain4j.data.document;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemDocumentLoaderTest {

    @Test
    void should_load_text_document() {

        Document document = FileSystemDocumentLoader.load("src/test/resources/test-file-utf8.txt");

        assertThat(document.text()).isEqualTo("test\ncontent");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file-utf8.txt");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }

    @Test
    void should_load_pdf_document() {

        Document document = FileSystemDocumentLoader.load("src/test/resources/test-file.pdf");

        assertThat(document.text()).isEqualToIgnoringWhitespace("test\ncontent");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file.pdf");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }

    @Test
    void should_load_all_documents_ignoring_unsupported_document_types() {

        Iterable<Document> documents = FileSystemDocumentLoader.loadAll("src/test/resources");

        assertThat(documents).hasSize(3);
    }
}