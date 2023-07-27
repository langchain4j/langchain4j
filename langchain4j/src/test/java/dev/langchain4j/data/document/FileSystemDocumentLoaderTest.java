package dev.langchain4j.data.document;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static dev.langchain4j.data.document.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.data.document.FileSystemDocumentLoader.loadDocuments;
import static org.assertj.core.api.Assertions.assertThat;

class FileSystemDocumentLoaderTest {

    @Test
    void should_load_text_document() {

        Document document = loadDocument(toPath("test-file-utf8.txt"));

        assertThat(document.text()).isEqualTo("test\ncontent");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file-utf8.txt");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }


    @Test
    void should_load_pdf_document() {

        Document document = loadDocument(toPath("test-file.pdf"));

        assertThat(document.text()).isEqualToIgnoringWhitespace("test\ncontent");
        Metadata metadata = document.metadata();
        assertThat(metadata.get("file_name")).isEqualTo("test-file.pdf");
        assertThat(Paths.get(metadata.get("absolute_directory_path"))).isAbsolute();
    }

    @Test
    void should_load_documents_ignoring_unsupported_document_types() {

        String userDir = System.getProperty("user.dir");
        Path resourceDirectory = Paths.get(userDir, "langchain4j/src/test/resources");
        if (!Files.exists(resourceDirectory)) {
            resourceDirectory = Paths.get(userDir, "src/test/resources");
        }

        List<Document> documents = loadDocuments(resourceDirectory);

        assertThat(documents).hasSize(3);
    }

    private Path toPath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}