package dev.langchain4j.data.document.loader;

import static dev.langchain4j.data.document.loader.ClassPathDocumentLoader.loadDocument;
import static dev.langchain4j.data.document.loader.ClassPathDocumentLoader.loadDocuments;
import static dev.langchain4j.data.document.loader.ClassPathDocumentLoader.loadDocumentsRecursively;
import static org.junit.Assert.assertNotNull;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClassPathDocumentLoaderTest implements WithAssertions {
    private static final String CLASSPATH_ROOT = ".";
    private static final String CLASSPATH_CHECK_DIRECTORY = "classPathSourceTests";
    private static final String CLASSPATH_IN_ARCHIVE_CHECK_DIRECTORY = "classPathSourceTestsInJar";

    @Test
    void load_bad_file() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocument("bad_file", new TextDocumentParser()))
                .withMessageContaining("'bad_file' was not found as a classpath resource");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocument("bad_file"))
                .withMessageContaining("'bad_file' was not found as a classpath resource");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocument(CLASSPATH_ROOT, new TextDocumentParser()))
                .withMessageContaining("' is not a file");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocument(CLASSPATH_ROOT))
                .withMessageContaining("' is not a file");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "classPathSourceTests/anotherDir/file2.txt",
                "classPathSourceTestsInJar/folderInsideJar/file4.txt"
            })
    void should_load_text_document(String path) {
        var filename = path.substring(path.lastIndexOf('/') + 1);
        var document = loadDocument(path, new TextDocumentParser());

        assertThat(document.text()).startsWithIgnoringCase("This is %s".formatted(filename));
        assertThat(document.metadata().getString(Document.FILE_NAME)).isEqualTo(filename);

        assertThat(document.metadata().getString(Document.URL))
                .isEqualTo(Thread.currentThread()
                        .getContextClassLoader()
                        .getResource(path)
                        .getFile());

        assertThat(loadDocument(path, new TextDocumentParser())).isEqualTo(document);
        assertThat(loadDocument(path)).isEqualTo(document);
    }

    @Test
    void load_bad_directory() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocuments("bad_directory", new TextDocumentParser()))
                .withMessageContaining("'bad_directory' was not found as a classpath resource");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loadDocuments("bad_directory"))
                .withMessageContaining("'bad_directory' was not found as a classpath resource");
    }

    @Test
    void should_load_documents_from_multiple_subdirs() {
        // given
        var resourceDirectory = "%s/anotherDir/".formatted(CLASSPATH_CHECK_DIRECTORY);

        // when
        var documents = loadDocuments(resourceDirectory, new TextDocumentParser());

        // then
        var fileNames = documents.stream()
                .map(document -> document.metadata().getString(Document.FILE_NAME))
                .toList();

        assertThat(fileNames).singleElement().isEqualTo("file2.txt");

        // when-then
        assertThat(loadDocuments(resourceDirectory, new TextDocumentParser())).isEqualTo(documents);
        assertThat(loadDocuments(resourceDirectory)).isEqualTo(documents);
    }

    @Test
    void should_load_documents_including_unknown_document_types_from_filesystem() {
        // given
        var resourceDirectory = CLASSPATH_ROOT;

        // when
        var documents = loadDocuments(resourceDirectory, new TextDocumentParser());

        // then
        var fileNames = documents.stream()
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
        assertThat(loadDocuments(resourceDirectory, new TextDocumentParser())).isEqualTo(documents);
        assertThat(loadDocuments(resourceDirectory)).isEqualTo(documents);

        // when-then
        assertThat(loadDocuments(resourceDirectory, new FailOnFirstNonBlankDocumentParser()))
                .hasSize(documents.size() - 1); // -1 because first document fails
    }

    @Test
    void should_load_documents_including_unknown_document_types_from_inside_archive() {
        // given
        var resourceDirectory = CLASSPATH_IN_ARCHIVE_CHECK_DIRECTORY;

        // when
        var documents = loadDocuments(resourceDirectory, new TextDocumentParser());

        // then
        var fileNames = documents.stream()
                .map(document -> document.metadata().getString(Document.FILE_NAME))
                .toList();

        assertThat(fileNames).containsExactlyInAnyOrder("file3.txt", "test-file-5.banana");

        // when-then
        assertThat(loadDocuments(resourceDirectory, new TextDocumentParser())).isEqualTo(documents);
        assertThat(loadDocuments(resourceDirectory)).isEqualTo(documents);

        // when-then
        assertThat(loadDocuments(resourceDirectory, new FailOnFirstNonBlankDocumentParser()))
                .hasSize(documents.size() - 1); // -1 because first document fails
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
		glob:*.banana  | classPathSourceTests      | test-file-3.banana
		glob:**.banana | classPathSourceTests      | test-file-3.banana
		glob:*.banana  | classPathSourceTestsInJar | test-file-5.banana
		glob:**.banana | classPathSourceTestsInJar | test-file-5.banana
		""")
    void should_load_matching_documents(String syntaxAndPattern, String path, String expectedFile) {

        // given
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(syntaxAndPattern);

        // when
        List<Document> documents = loadDocuments(path, pathMatcher, new TextDocumentParser());

        // then
        List<String> fileNames = documents.stream()
                .map(document -> document.metadata().getString(Document.FILE_NAME))
                .toList();

        assertThat(fileNames).singleElement().isEqualTo(expectedFile);

        // when-then
        assertThat(loadDocuments(path, pathMatcher, new TextDocumentParser())).isEqualTo(documents);

        assertThat(loadDocuments(path, pathMatcher)).isEqualTo(documents);
    }

    @ParameterizedTest
    @MethodSource("should_recursively_load_documents_arguments")
    void should_recursively_load_documents(String path, List<String> expectedFileNames) {

        // when
        List<Document> documents = loadDocumentsRecursively(path, new TextDocumentParser());

        // then
        List<String> fileNames = documents.stream()
                .map(document -> document.metadata().getString(Document.FILE_NAME))
                .toList();

        assertThat(fileNames).containsExactlyInAnyOrderElementsOf(expectedFileNames);

        // when-then
        assertThat(loadDocumentsRecursively(path, new TextDocumentParser())).isEqualTo(documents);

        assertThat(loadDocumentsRecursively(path)).isEqualTo(documents);
    }

    static Stream<Arguments> should_recursively_load_documents_arguments() {
        return Stream.of(
                Arguments.of(
                        CLASSPATH_CHECK_DIRECTORY,
                        List.of("test-file-3.banana", "test-file-4.banana", "file1.txt", "file2.txt")),
                Arguments.of(
                        CLASSPATH_IN_ARCHIVE_CHECK_DIRECTORY,
                        List.of("test-file-5.banana", "test-file-6.banana", "file3.txt", "file4.txt")));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
		classPathSourceTestsInJar | test-file-5.banana
		classPathSourceTests | test-file-3.banana
		""")
    void should_recursively_load_matching_documents(String path, String expectedFileName) {

        // given
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:*.banana");

        // when
        List<Document> documents = loadDocumentsRecursively(path, pathMatcher, new TextDocumentParser());

        // then
        List<String> fileNames = documents.stream()
                .map(document -> document.metadata().getString(Document.FILE_NAME))
                .toList();

        assertThat(fileNames).singleElement().isEqualTo(expectedFileName);

        // when-then
        assertThat(loadDocumentsRecursively(path, pathMatcher, new TextDocumentParser()))
                .isEqualTo(documents);

        assertThat(loadDocumentsRecursively(path, pathMatcher)).isEqualTo(documents);
    }

    @ParameterizedTest
    @MethodSource("should_recursively_load_matching_documents_with_glob_crossing_directory_boundaries_arguments")
    void should_recursively_load_matching_documents_with_glob_crossing_directory_boundaries(
            String path, List<String> expectedFileNames) {

        // given
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.banana");

        // when
        List<Document> documents = loadDocumentsRecursively(path, pathMatcher, new TextDocumentParser());

        // then
        List<String> fileNames = documents.stream()
                .map(document -> document.metadata().getString(Document.FILE_NAME))
                .toList();

        assertThat(fileNames).containsExactlyInAnyOrderElementsOf(expectedFileNames);

        // when-then
        assertThat(loadDocumentsRecursively(path, pathMatcher, new TextDocumentParser()))
                .isEqualTo(documents);

        assertThat(loadDocumentsRecursively(path, pathMatcher)).isEqualTo(documents);
    }

    static Stream<Arguments>
            should_recursively_load_matching_documents_with_glob_crossing_directory_boundaries_arguments() {
        return Stream.of(
                Arguments.of(CLASSPATH_CHECK_DIRECTORY, List.of("test-file-3.banana", "test-file-4.banana")),
                Arguments.of(
                        CLASSPATH_IN_ARCHIVE_CHECK_DIRECTORY, List.of("test-file-5.banana", "test-file-6.banana")));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
		classPathSourceTests | test-file-4.banana
		classPathSourceTestsInJar | test-file-6.banana
		""")
    void should_recursively_load_matching_documents_with_glob_specifying_concrete_directory(
            String path, String expectedFileName) {

        // given
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:banana/*.banana");

        // when
        List<Document> documents = loadDocumentsRecursively(path, pathMatcher, new TextDocumentParser());

        // then
        List<String> fileNames = documents.stream()
                .map(document -> document.metadata().getString(Document.FILE_NAME))
                .toList();
        assertThat(fileNames).singleElement().isEqualTo(expectedFileName);

        // when-then
        assertThat(loadDocumentsRecursively(path, pathMatcher, new TextDocumentParser()))
                .isEqualTo(documents);

        assertThat(loadDocumentsRecursively(path, pathMatcher)).isEqualTo(documents);
    }

    @Test
    void should_use_provided_class_loader() throws Exception {
        String testResourceName = "test-custom-classloader.txt";
        String testResourceContent = "This is test-custom-classloader.txt";

        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("custom-classloader-test");
        java.nio.file.Path testFile = tempDir.resolve(testResourceName);
        java.nio.file.Files.writeString(testFile, testResourceContent);

        java.net.URLClassLoader customClassLoader = new java.net.URLClassLoader(
                new java.net.URL[] {tempDir.toUri().toURL()}, null // no parent loader — isolates from context classpath
                );

        Document document = loadDocument(testResourceName, customClassLoader);

        assertThat(document.text()).isEqualTo(testResourceContent);
        assertThat(document.metadata().getString(Document.FILE_NAME)).isEqualTo(testResourceName);
    }

    @Test
    void should_throw_when_classloader_does_not_have_resource() {
        String missingResource = "non-existent-resource.txt";

        // Create a classloader with no classpath URLs and no parent classloader.
        // This ensures that it won't find any resources (fully isolated).
        ClassLoader emptyClassLoader = new URLClassLoader(new URL[0], null);

        // Attempt to load the missing resource using the empty classloader
        // and verify that a RuntimeException is thrown with the resource name in the message.
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> ClassPathDocumentLoader.loadDocument(missingResource, emptyClassLoader))
                .withMessageContaining(missingResource);
    }

    @Test
    void should_throw_when_resource_not_visible_to_custom_classloader_but_exists_in_classpath() {
        String resourceFromClassPath = "test-file-utf8.txt";

        // This must be visible from default class loader
        Document document = ClassPathDocumentLoader.loadDocument(resourceFromClassPath);
        assertNotNull(document);
        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");

        // Create a classloader with no classpath URLs and no parent classloader.
        // This ensures that it won't find any resources (fully isolated).
        ClassLoader emptyClassLoader = new URLClassLoader(new URL[0], null);

        // Attempt to load the missing resource using the empty classloader
        // and verify that a RuntimeException is thrown with the resource name in the message.
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> ClassPathDocumentLoader.loadDocument(resourceFromClassPath, emptyClassLoader))
                .withMessageContaining(resourceFromClassPath);
    }

    private class FailOnFirstNonBlankDocumentParser implements DocumentParser {
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
    }
}
