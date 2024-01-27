package dev.langchain4j.data.document.loader;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocuments;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

class FileSystemDocumentLoaderTest {

    @Test
    void should_load_text_document() {

        Document document = loadDocument(toPath("test-file-utf8.txt"), new TextDocumentParser());

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata("file_name")).isEqualTo("test-file-utf8.txt");
        assertThat(Paths.get(document.metadata("absolute_directory_path"))).isAbsolute();
    }

    @Test
    void should_load_documents_from_directory_including_unknown_document_types() {

        String userDir = System.getProperty("user.dir");
        Path resourceDirectory = Paths.get(userDir, "langchain4j/src/test/resources");
        if (!Files.exists(resourceDirectory)) {
            resourceDirectory = Paths.get(userDir, "src/test/resources");
        }

        List<Document> documents = loadDocuments(resourceDirectory, new TextDocumentParser());
        assertThat(documents).hasSize(4);

        Set<String> fileNames = documents.stream()
                .map(document -> document.metadata("file_name"))
                .collect(toSet());
        assertThat(fileNames).containsExactlyInAnyOrder(
                "miles-of-smiles-terms-of-use.txt",
                "test-file.banana",
                "test-file-iso-8859-1.txt",
                "test-file-utf8.txt"
        );
    }

    private Path toPath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}