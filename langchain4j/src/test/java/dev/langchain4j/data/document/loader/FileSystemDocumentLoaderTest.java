package dev.langchain4j.data.document.loader;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.*;
import static java.util.stream.Collectors.toSet;

class FileSystemDocumentLoaderTest implements WithAssertions {

    @Test
    void load_bad_file() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocument(Paths.get("bad_file"), new TextDocumentParser()))
                .withMessageContaining("bad_file is not a file");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocument(Paths.get("/"), new TextDocumentParser()))
                .withMessageContaining("is not a file");
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
    void should_load_documents_including_unknown_document_types() {

        // given
        Path resourceDirectory = resourceDirectory();

        // when
        List<Document> documents = loadDocuments(resourceDirectory, new TextDocumentParser());

        // then
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

        // when-then
        assertThat(loadDocuments(resourceDirectory.toString(), new TextDocumentParser()))
                .isEqualTo(documents);

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

        // when-then
        assertThat(loadDocuments(resourceDirectory, failFirstParser))
                .hasSize(documents.size() - 1);
    }

    @Test
    void should_load_matching_documents() {

        // given
        Path resourceDirectory = resourceDirectory();
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*.txt");

        // when
        List<Document> documents = loadDocuments(resourceDirectory, pathMatcher, new TextDocumentParser());

        // then
        assertThat(documents).hasSize(3);

        Set<String> fileNames = documents.stream()
                .map(document -> document.metadata("file_name"))
                .collect(toSet());
        assertThat(fileNames).containsExactlyInAnyOrder(
                "miles-of-smiles-terms-of-use.txt",
                "test-file-iso-8859-1.txt",
                "test-file-utf8.txt"
        );

        // when-then
        assertThat(loadDocuments(resourceDirectory.toString(), pathMatcher, new TextDocumentParser()))
                .isEqualTo(documents);
    }

    @Test
    void should_recursively_load_documents() {

        // given
        Path resourceDirectory = resourceDirectory();

        // when
        List<Document> documents = loadDocumentsRecursively(resourceDirectory, new TextDocumentParser());

        // then
        assertThat(documents).hasSize(6);

        Set<String> fileNames = documents.stream()
                .map(document -> document.metadata("file_name"))
                .collect(toSet());
        assertThat(fileNames).containsExactlyInAnyOrder(
                "miles-of-smiles-terms-of-use.txt",
                "test-file.banana",
                "test-file-iso-8859-1.txt",
                "test-file-utf8.txt",
                "chefs-prompt-based-on-ingredients.txt",
                "chefs-prompt-system-message.txt"
        );

        // when-then
        assertThat(loadDocumentsRecursively(resourceDirectory.toString(), new TextDocumentParser()))
                .isEqualTo(documents);
    }

    @Test
    void should_recursively_load_matching_documents() {

        // given
        Path resourceDirectory = resourceDirectory();
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*.txt");

        // when
        List<Document> documents = loadDocumentsRecursively(resourceDirectory, pathMatcher, new TextDocumentParser());

        // then
        assertThat(documents).hasSize(5);

        Set<String> fileNames = documents.stream()
                .map(document -> document.metadata("file_name"))
                .collect(toSet());
        assertThat(fileNames).containsExactlyInAnyOrder(
                "miles-of-smiles-terms-of-use.txt",
                "test-file-iso-8859-1.txt",
                "test-file-utf8.txt",
                "chefs-prompt-based-on-ingredients.txt",
                "chefs-prompt-system-message.txt"
        );

        // when-then
        assertThat(loadDocumentsRecursively(resourceDirectory.toString(), pathMatcher, new TextDocumentParser()))
                .isEqualTo(documents);
    }

    private static Path resourceDirectory() {
        String userDir = System.getProperty("user.dir");
        Path resourceDirectory = Paths.get(userDir, "langchain4j/src/test/resources");
        if (Files.exists(resourceDirectory)) {
            return resourceDirectory;
        }
        return Paths.get(userDir, "src/test/resources");
    }

    private Path toPath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}