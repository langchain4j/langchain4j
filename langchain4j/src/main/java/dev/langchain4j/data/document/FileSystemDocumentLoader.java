package dev.langchain4j.data.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.data.document.DocumentLoaderUtils.parserFor;
import static dev.langchain4j.data.document.source.FileSystemSource.from;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;

public class FileSystemDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(FileSystemDocumentLoader.class);

    /**
     * Loads a document from the specified file, detecting document type automatically.
     * See {@link DocumentType} for the list of supported document types.
     * If the document type is UNKNOWN, it is treated as TXT.
     *
     * @param filePath path to the file
     * @return document
     * @throws IllegalArgumentException if specified path is not a file
     */
    public static Document loadDocument(Path filePath) {
        return loadDocument(filePath, DocumentType.of(filePath.toString()));
    }

    /**
     * Loads a document from the specified file, detecting document type automatically.
     * See {@link DocumentType} for the list of supported document types.
     * If the document type is UNKNOWN, it is treated as TXT.
     *
     * @param filePath path to the file
     * @return document
     * @throws IllegalArgumentException if specified path is not a file
     */
    public static Document loadDocument(String filePath) {
        return loadDocument(Paths.get(filePath));
    }

    /**
     * Loads a document from the specified file.
     *
     * @param filePath     path to the file
     * @param documentType type of the document
     * @return document
     * @throws IllegalArgumentException if specified path is not a file
     */
    public static Document loadDocument(Path filePath, DocumentType documentType) {
        if (!isRegularFile(filePath)) {
            throw illegalArgument("%s is not a file", filePath);
        }

        return DocumentLoaderUtils.load(from(filePath), parserFor(documentType));
    }

    /**
     * Loads a document from the specified file.
     *
     * @param filePath     path to the file
     * @param documentType type of the document
     * @return document
     * @throws IllegalArgumentException if specified path is not a file
     */
    public static Document loadDocument(String filePath, DocumentType documentType) {
        return loadDocument(Paths.get(filePath), documentType);
    }

    /**
     * Loads documents from the specified directory. Does not use recursion.
     * Detects document types automatically.
     * See {@link DocumentType} for the list of supported document types.
     * If the document type is UNKNOWN, it is treated as TXT.
     *
     * @param directoryPath path to the directory with files
     * @return list of documents
     * @throws IllegalArgumentException if specified path is not a directory
     */
    public static List<Document> loadDocuments(Path directoryPath) {
        if (!isDirectory(directoryPath)) {
            throw illegalArgument("%s is not a directory", directoryPath);
        }

        List<Document> documents = new ArrayList<>();

        try (Stream<Path> paths = Files.list(directoryPath)) {
            paths.filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        try {
                            Document document = loadDocument(filePath);
                            documents.add(document);
                        } catch (Exception e) {
                            log.warn("Failed to load document from " + filePath, e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return documents;
    }

    /**
     * Loads documents from the specified directory. Does not use recursion.
     * Detects document types automatically.
     * See {@link DocumentType} for the list of supported document types.
     * If the document type is UNKNOWN, it is treated as TXT.
     *
     * @param directoryPath path to the directory with files
     * @return list of documents
     * @throws IllegalArgumentException if specified path is not a directory
     */
    public static List<Document> loadDocuments(String directoryPath) {
        return loadDocuments(Paths.get(directoryPath));
    }
}
