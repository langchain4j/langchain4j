package dev.langchain4j.data.document.loader;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.data.document.source.FileSystemSource.from;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;

public class FileSystemDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(FileSystemDocumentLoader.class);

    private FileSystemDocumentLoader() {
    }

    /**
     * Loads a document from the specified file.
     * Returned document contains all the textual information from the file.
     *
     * @param filePath       The path to the file.
     * @param documentParser The parser to be used for parsing text from the file.
     * @return document
     * @throws IllegalArgumentException If specified path is not a file.
     */
    public static Document loadDocument(Path filePath, DocumentParser documentParser) {
        if (!isRegularFile(filePath)) {
            throw illegalArgument("%s is not a file", filePath);
        }

        return DocumentLoader.load(from(filePath), documentParser);
    }

    /**
     * Loads a document from the specified file.
     * Returned document contains all the textual information from the file.
     *
     * @param filePath       The path to the file.
     * @param documentParser The parser to be used for parsing text from the file.
     * @return document
     * @throws IllegalArgumentException If specified path is not a file.
     */
    public static Document loadDocument(String filePath, DocumentParser documentParser) {
        return loadDocument(Paths.get(filePath), documentParser);
    }

    /**
     * Loads documents from the specified directory. Does not use recursion.
     * Skips any documents that fail to load.
     *
     * @param directoryPath  The path to the directory with files.
     * @param documentParser The parser to be used for parsing text from each file.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocuments(Path directoryPath, DocumentParser documentParser) {
        if (!isDirectory(directoryPath)) {
            throw illegalArgument("%s is not a directory", directoryPath);
        }

        try (Stream<Path> pathStream = Files.list(directoryPath)) {
            return loadDocuments(pathStream, (path) -> true, documentParser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads documents from the specified directory. Does not use recursion.
     * Skips any documents that fail to load.
     *
     * @param directoryPath  The path to the directory with files.
     * @param documentParser The parser to be used for parsing text from each file.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocuments(String directoryPath, DocumentParser documentParser) {
        return loadDocuments(Paths.get(directoryPath), documentParser);
    }

    /**
     * Loads matching documents from the specified directory. Does not use recursion.
     * Skips any documents that fail to load.
     *
     * @param directoryPath  The path to the directory with files.
     * @param pathMatcher    Only files matching the criteria set by the provided {@link PathMatcher} will be loaded.
     *                       For example (remove "\" from glob pattern):
     *                       <code>FileSystems.getDefault().getPathMatcher("glob:**\/*.txt")</code>.
     * @param documentParser The parser to be used for parsing text from each file.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocuments(Path directoryPath,
                                               PathMatcher pathMatcher,
                                               DocumentParser documentParser) {
        if (!isDirectory(directoryPath)) {
            throw illegalArgument("%s is not a directory", directoryPath);
        }

        try (Stream<Path> pathStream = Files.list(directoryPath)) {
            return loadDocuments(pathStream, pathMatcher, documentParser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads matching documents from the specified directory. Does not use recursion.
     * Skips any documents that fail to load.
     *
     * @param directoryPath  The path to the directory with files.
     * @param pathMatcher    Only files matching the criteria set by the provided {@link PathMatcher} will be loaded.
     *                       For example (remove "\" from glob pattern):
     *                       <code>FileSystems.getDefault().getPathMatcher("glob:**\/*.txt")</code>
     * @param documentParser The parser to be used for parsing text from each file.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocuments(String directoryPath,
                                               PathMatcher pathMatcher,
                                               DocumentParser documentParser) {
        return loadDocuments(Paths.get(directoryPath), pathMatcher, documentParser);
    }

    /**
     * Recursively loads documents from the specified directory and its subdirectories.
     * Skips any documents that fail to load.
     *
     * @param directoryPath  The path to the directory with files.
     * @param documentParser The parser to be used for parsing text from each file.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocumentsRecursively(Path directoryPath, DocumentParser documentParser) {
        if (!isDirectory(directoryPath)) {
            throw illegalArgument("%s is not a directory", directoryPath);
        }

        try (Stream<Path> pathStream = Files.walk(directoryPath)) {
            return loadDocuments(pathStream, (path) -> true, documentParser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Recursively loads documents from the specified directory and its subdirectories.
     * Skips any documents that fail to load.
     *
     * @param directoryPath  The path to the directory with files.
     * @param documentParser The parser to be used for parsing text from each file.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocumentsRecursively(String directoryPath, DocumentParser documentParser) {
        return loadDocumentsRecursively(Paths.get(directoryPath), documentParser);
    }

    /**
     * Recursively loads matching documents from the specified directory and its subdirectories.
     * Skips any documents that fail to load.
     *
     * @param directoryPath  The path to the directory with files.
     * @param pathMatcher    Only files matching the criteria set by the provided {@link PathMatcher} will be loaded.
     *                       For example (remove "\" from glob pattern):
     *                       <code>FileSystems.getDefault().getPathMatcher("glob:**\/*.txt")</code>
     * @param documentParser The parser to be used for parsing text from each file.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocumentsRecursively(Path directoryPath,
                                                          PathMatcher pathMatcher,
                                                          DocumentParser documentParser) {
        if (!isDirectory(directoryPath)) {
            throw illegalArgument("%s is not a directory", directoryPath);
        }

        try (Stream<Path> pathStream = Files.walk(directoryPath)) {
            return loadDocuments(pathStream, pathMatcher, documentParser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Recursively loads matching documents from the specified directory and its subdirectories.
     * Skips any documents that fail to load.
     *
     * @param directoryPath  The path to the directory with files.
     * @param pathMatcher    Only files matching the criteria set by the provided {@link PathMatcher} will be loaded.
     *                       For example (remove "\" from glob pattern):
     *                       <code>FileSystems.getDefault().getPathMatcher("glob:**\/*.txt")</code>
     * @param documentParser The parser to be used for parsing text from each file.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocumentsRecursively(String directoryPath,
                                                          PathMatcher pathMatcher,
                                                          DocumentParser documentParser) {
        return loadDocumentsRecursively(Paths.get(directoryPath), pathMatcher, documentParser);
    }

    private static List<Document> loadDocuments(Stream<Path> pathStream,
                                                PathMatcher pathMatcher,
                                                DocumentParser documentParser) {
        List<Document> documents = new ArrayList<>();

        pathStream
                .filter(Files::isRegularFile)
                .filter(pathMatcher::matches)
                .forEach(file -> {
                    try {
                        Document document = loadDocument(file, documentParser);
                        documents.add(document);
                    } catch (Exception e) {
                        log.warn("Failed to load document from " + file, e);
                    }
                });

        return documents;
    }
}
