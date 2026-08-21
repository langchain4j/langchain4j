package dev.langchain4j.data.document.loader;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocuments;
import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocumentsRecursively;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileSystemDocumentLoaderTest implements WithAssertions {

    @Test
    void load_bad_file() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocument(Paths.get("bad_file"), new TextDocumentParser()))
                .withMessageContaining("'bad_file' is not a file");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocument(Paths.get("bad_file")))
                .withMessageContaining("'bad_file' is not a file");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocument(Paths.get("/"), new TextDocumentParser()))
                .withMessageContaining("is not a file");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocument(Paths.get("/")))
                .withMessageContaining("is not a file");
    }

    @Test
    void should_load_text_document() {
        Path path = toPath("test-file-utf8.txt");
        Document document = loadDocument(path, new TextDocumentParser());

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata().getString(Document.FILE_NAME)).isEqualTo("test-file-utf8.txt");
        assertThat(Paths.get(document.metadata().getString(Document.ABSOLUTE_DIRECTORY_PATH)))
                .isAbsolute();

        assertThat(loadDocument(path.toString(), new TextDocumentParser())).isEqualTo(document);

        assertThat(loadDocument(path)).isEqualTo(document);
        assertThat(loadDocument(path.toString())).isEqualTo(document);
    }

    @Test
    void load_bad_directory() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocuments(Paths.get("bad_directory"), new TextDocumentParser()))
                .withMessageContaining("'bad_directory' is not a directory");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocuments(Paths.get("bad_directory")))
                .withMessageContaining("'bad_directory' is not a directory");
    }

    @Test
    void should_load_documents_including_unknown_document_types() {

        // given
        Path resourceDirectory = resourceDirectory();

        // when
        List<Document> documents = loadDocuments(resourceDirectory, new TextDocumentParser());

        // then
        List<String> fileNames = documents.stream()
                .map(document -> document.metadata().getString(Document.FILE_NAME))
                .toList();
        assertThat(fileNames)
                .containsExactlyInAnyOrder(
                        "miles-of-smiles-terms-of-use.txt",
                        "test-file.banana",
                        "test-file-iso-8859-1.txt",
                        "test-file-utf8.txt",
                        "chefs-prompt-based-on-ingredients-in-root.txt",
                        "junit-platform.properties");

        // when-then
        assertThat(loadDocuments(resourceDirectory.toString(), new TextDocumentParser()))
                .isEqualTo(documents);

        assertThat(loadDocuments(resourceDirectory)).isEqualTo(documents);
        assertThat(loadDocuments(resourceDirectory.toString())).isEqualTo(documents);

        DocumentParser parserThatFailsOnFirstNonBlankDocument = new DocumentParser() {

            private boolean first = true;
            private final DocumentParser parser = new TextDocumentParser();

            @Override
            public Document parse(InputStream inputStream) {
                if (first && isNotBlank(inputStream)) {
                    first = false;
                    throw new RuntimeException("fail first");
                }
                return parser.parse(inputStream);
            }

            private boolean isNotBlank(InputStream inputStream) {
                try {
                    return inputStream.available() > 10; // rough approximation
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        // when-then
        assertThat(loadDocuments(resourceDirectory, parserThatFailsOnFirstNonBlankDocument))
                .hasSize(documents.size() - 1); // -1 because first document fails
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "glob:*.banana",
                "glob:**.banana",
            })
    void should_load_matching_documents(String syntaxAndPattern) {

        // given
        Path resourceDirectory = resourceDirectory();
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(syntaxAndPattern);

        // when
        List<Document> documents = loadDocuments(resourceDirectory, pathMatcher, new TextDocumentParser());

        // then
        List<String> fileNames = documents.stream()
                .map(document -> document.metadata().getString(Document.FILE_NAME))
                .toList();
        assertThat(fileNames).containsExactlyInAnyOrder("test-file.banana");

        // when-then
        assertThat(loadDocuments(resourceDirectory.toString(), pathMatcher, new TextDocumentParser()))
                .isEqualTo(documents);

        assertThat(loadDocuments(resourceDirectory, pathMatcher)).isEqualTo(documents);
        assertThat(loadDocuments(resourceDirectory.toString(), pathMatcher)).isEqualTo(documents);
    }

    @Test
    void should_recursively_load_documents() {

        // given
        Path resourceDirectory = resourceDirectory();

        // when
        List<Document> documents = loadDocumentsRecursively(resourceDirectory, new TextDocumentParser());

        // then
        List<String> fileNames = documents.stream()
                .map(document -> document.metadata().getString(Document.FILE_NAME))
                .toList();
        assertThat(fileNames)
                .containsExactlyInAnyOrder(
                        "miles-of-smiles-terms-of-use.txt",
                        "test-file.banana",
                        "test-file-iso-8859-1.txt",
                        "test-file-utf8.txt",
                        "chefs-prompt-based-on-ingredients.txt",
                        "chefs-prompt-system-message.txt",
                        "chefs-prompt-based-on-ingredients-in-root.txt",
                        "chefs-prompt-based-on-ingredients-in-subdirectory.txt",
                        "test-file-2.banana",
                        "file1.txt",
                        "file2.txt",
                        "test-file-3.banana",
                        "test-file-4.banana",
                        "junit-platform.properties");

        // when-then
        assertThat(loadDocumentsRecursively(resourceDirectory.toString(), new TextDocumentParser()))
                .isEqualTo(documents);

        assertThat(loadDocumentsRecursively(resourceDirectory)).isEqualTo(documents);
        assertThat(loadDocumentsRecursively(resourceDirectory.toString())).isEqualTo(documents);
    }

    @Test
    void should_recursively_load_matching_documents() {

        // given
        Path resourceDirectory = resourceDirectory();
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:*.banana");

        // when
        List<Document> documents = loadDocumentsRecursively(resourceDirectory, pathMatcher, new TextDocumentParser());

        // then
        List<String> fileNames = documents.stream()
                .map(document -> document.metadata().getString(Document.FILE_NAME))
                .toList();
        assertThat(fileNames).containsExactlyInAnyOrder("test-file.banana");

        // when-then
        assertThat(loadDocumentsRecursively(resourceDirectory.toString(), pathMatcher, new TextDocumentParser()))
                .isEqualTo(documents);

        assertThat(loadDocumentsRecursively(resourceDirectory, pathMatcher)).isEqualTo(documents);
        assertThat(loadDocumentsRecursively(resourceDirectory.toString(), pathMatcher))
                .isEqualTo(documents);
    }

    @Test
    void should_recursively_load_matching_documents_with_glob_crossing_directory_boundaries() {

        // given
        Path resourceDirectory = resourceDirectory();
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.banana");

        // when
        List<Document> documents = loadDocumentsRecursively(resourceDirectory, pathMatcher, new TextDocumentParser());

        // then
        List<String> fileNames = documents.stream()
                .map(document -> document.metadata().getString(Document.FILE_NAME))
                .toList();
        assertThat(fileNames)
                .containsExactlyInAnyOrder(
                        "test-file.banana", "test-file-2.banana", "test-file-3.banana", "test-file-4.banana");

        // when-then
        assertThat(loadDocumentsRecursively(resourceDirectory.toString(), pathMatcher, new TextDocumentParser()))
                .isEqualTo(documents);

        assertThat(loadDocumentsRecursively(resourceDirectory, pathMatcher)).isEqualTo(documents);
        assertThat(loadDocumentsRecursively(resourceDirectory.toString(), pathMatcher))
                .isEqualTo(documents);
    }

    @Test
    void should_recursively_load_matching_documents_with_glob_specifying_concrete_directory() {

        // given
        Path resourceDirectory = resourceDirectory();
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:banana/*.banana");

        // when
        List<Document> documents = loadDocumentsRecursively(resourceDirectory, pathMatcher, new TextDocumentParser());

        // then
        List<String> fileNames = documents.stream()
                .map(document -> document.metadata().getString(Document.FILE_NAME))
                .toList();
        assertThat(fileNames).containsExactlyInAnyOrder("test-file-2.banana");

        // when-then
        assertThat(loadDocumentsRecursively(resourceDirectory.toString(), pathMatcher, new TextDocumentParser()))
                .isEqualTo(documents);

        assertThat(loadDocumentsRecursively(resourceDirectory, pathMatcher)).isEqualTo(documents);
        assertThat(loadDocumentsRecursively(resourceDirectory.toString(), pathMatcher))
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
