package dev.langchain4j.data.document.loader;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocuments;
import static java.util.stream.Collectors.toSet;

class FileSystemDocumentLoaderTest implements WithAssertions {
    @Test
    void load_bad_file() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> loadDocument(Paths.get("bad_file"), new TextDocumentParser()))
            .withMessageContaining("bad_file is not a file");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocument(Paths.get("/"), new TextDocumentParser()))
                .withMessageContaining("/ is not a file");
    }

    @Test
    void should_load_text_document() {
        Path path = toPath("test-file-utf8.txt");
        Document document = loadDocument(path, new TextDocumentParser());

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata("file_name")).isEqualTo("test-file-utf8.txt");
        assertThat(Paths.get(document.metadata("absolute_directory_path"))).isAbsolute();

        assertThat(loadDocument(path.toString(), new TextDocumentParser())).isEqualTo(document);
    }

    @Test
    void load_bad_directory() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocuments(
                        Paths.get("bad_directory"), new TextDocumentParser()))
                .withMessageContaining("bad_directory is not a directory");
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

        assertThat(loadDocuments(resourceDirectory.toString(), new TextDocumentParser())).isEqualTo(documents);

        // Silently skips documents that fail to load.
        DocumentParser failFirstParser = new DocumentParser() {
            private boolean first = true;
            private final DocumentParser parser = new TextDocumentParser();
            @Override
            public Document parse(InputStream inputStream) {
                if (first) {
                    first = false;
                    throw new RuntimeException("fail first");
                }
                return parser.parse(inputStream);
            }
        };

        assertThat(loadDocuments(resourceDirectory, failFirstParser)).hasSize(2);
    }

    private Path toPath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}