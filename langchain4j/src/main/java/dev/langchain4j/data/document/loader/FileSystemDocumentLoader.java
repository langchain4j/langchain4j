package dev.langchain4j.data.document.loader;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.spi.data.document.parser.DocumentParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.data.document.source.FileSystemSource.from;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;

public class FileSystemDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(FileSystemDocumentLoader.class);

    private static final DocumentParser DEFAULT_DOCUMENT_PARSER = getOrDefault(loadDocumentParser(), TextDocumentParser::new);

    private FileSystemDocumentLoader() {
    }

    /**
     * Loads a {@link Document} from the specified file {@link Path}.
     * <br>
     * The file is parsed using the specified {@link DocumentParser}.
     * <br>
     * Returned {@code Document} contains all the textual information from the file.
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
     * Loads a {@link Document} from the specified file {@link Path}.
     * <br>
     * The file is parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link DocumentParserFactory}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Returned {@code Document} contains all the textual information from the file.
     *
     * @param filePath The path to the file.
     * @return document
     * @throws IllegalArgumentException If specified path is not a file.
     */
    public static Document loadDocument(Path filePath) {
        return loadDocument(filePath, DEFAULT_DOCUMENT_PARSER);
    }

    /**
     * Loads a {@link Document} from the specified file path.
     * <br>
     * The file is parsed using the specified {@link DocumentParser}.
     * <br>
     * Returned {@code Document} contains all the textual information from the file.
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
     * Loads a {@link Document} from the specified file path.
     * <br>
     * The file is parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link DocumentParserFactory}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Returned {@code Document} contains all the textual information from the file.
     *
     * @param filePath The path to the file.
     * @return document
     * @throws IllegalArgumentException If specified path is not a file.
     */
    public static Document loadDocument(String filePath) {
        return loadDocument(filePath, DEFAULT_DOCUMENT_PARSER);
    }

    /**
     * Loads {@link Document}s from the specified directory. Does not use recursion.
     * <br>
     * The files are parsed using the specified {@link DocumentParser}.
     * <br>
     * Skips any {@code Document}s that fail to load.
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
            return loadDocuments(pathStream, (path) -> true, directoryPath, documentParser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads {@link Document}s from the specified directory. Does not use recursion.
     * <br>
     * The files are parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link DocumentParserFactory}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryPath The path to the directory with files.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocuments(Path directoryPath) {
        return loadDocuments(directoryPath, DEFAULT_DOCUMENT_PARSER);
    }

    /**
     * Loads {@link Document}s from the specified directory. Does not use recursion.
     * <br>
     * The files are parsed using the specified {@link DocumentParser}.
     * <br>
     * Skips any {@code Document}s that fail to load.
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
     * Loads {@link Document}s from the specified directory. Does not use recursion.
     * <br>
     * The files are parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link DocumentParserFactory}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryPath The path to the directory with files.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocuments(String directoryPath) {
        return loadDocuments(directoryPath, DEFAULT_DOCUMENT_PARSER);
    }

    /**
     * Loads matching {@link Document}s from the specified directory. Does not use recursion.
     * <br>
     * The files are parsed using the specified {@link DocumentParser}.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryPath  The path to the directory with files.
     * @param pathMatcher    Only files whose paths match the provided {@link PathMatcher} will be loaded.
     *                       For example, using {@code FileSystems.getDefault().getPathMatcher("glob:*.txt")}
     *                       will load all files from {@code directoryPath} with a {@code txt} extension.
     *                       When traversing the directory, each file path is converted from absolute to relative
     *                       (relative to {@code directoryPath}) before being matched by a {@code pathMatcher}.
     *                       Thus, {@code pathMatcher} should use relative patterns.
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
            return loadDocuments(pathStream, pathMatcher, directoryPath, documentParser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads matching {@link Document}s from the specified directory. Does not use recursion.
     * <br>
     * The files are parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link DocumentParserFactory}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryPath The path to the directory with files.
     * @param pathMatcher   Only files whose paths match the provided {@link PathMatcher} will be loaded.
     *                      For example, using {@code FileSystems.getDefault().getPathMatcher("glob:*.txt")}
     *                      will load all files from {@code directoryPath} with a {@code txt} extension.
     *                      When traversing the directory, each file path is converted from absolute to relative
     *                      (relative to {@code directoryPath}) before being matched by a {@code pathMatcher}.
     *                      Thus, {@code pathMatcher} should use relative patterns.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocuments(Path directoryPath, PathMatcher pathMatcher) {
        return loadDocuments(directoryPath, pathMatcher, DEFAULT_DOCUMENT_PARSER);
    }

    /**
     * Loads matching {@link Document}s from the specified directory. Does not use recursion.
     * <br>
     * The files are parsed using the specified {@link DocumentParser}.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryPath  The path to the directory with files.
     * @param pathMatcher    Only files whose paths match the provided {@link PathMatcher} will be loaded.
     *                       For example, using {@code FileSystems.getDefault().getPathMatcher("glob:*.txt")}
     *                       will load all files from {@code directoryPath} with a {@code txt} extension.
     *                       When traversing the directory, each file path is converted from absolute to relative
     *                       (relative to {@code directoryPath}) before being matched by a {@code pathMatcher}.
     *                       Thus, {@code pathMatcher} should use relative patterns.
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
     * Loads matching {@link Document}s from the specified directory. Does not use recursion.
     * <br>
     * The files are parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link DocumentParserFactory}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryPath The path to the directory with files.
     * @param pathMatcher   Only files whose paths match the provided {@link PathMatcher} will be loaded.
     *                      For example, using {@code FileSystems.getDefault().getPathMatcher("glob:*.txt")}
     *                      will load all files from {@code directoryPath} with a {@code txt} extension.
     *                      When traversing the directory, each file path is converted from absolute to relative
     *                      (relative to {@code directoryPath}) before being matched by a {@code pathMatcher}.
     *                      Thus, {@code pathMatcher} should use relative patterns.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocuments(String directoryPath, PathMatcher pathMatcher) {
        return loadDocuments(directoryPath, pathMatcher, DEFAULT_DOCUMENT_PARSER);
    }

    /**
     * Recursively loads {@link Document}s from the specified directory and its subdirectories.
     * <br>
     * The files are parsed using the specified {@link DocumentParser}.
     * <br>
     * Skips any {@code Document}s that fail to load.
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
            return loadDocuments(pathStream, (path) -> true, directoryPath, documentParser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Recursively loads {@link Document}s from the specified directory and its subdirectories.
     * <br>
     * The files are parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link DocumentParserFactory}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryPath The path to the directory with files.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocumentsRecursively(Path directoryPath) {
        return loadDocumentsRecursively(directoryPath, DEFAULT_DOCUMENT_PARSER);
    }

    /**
     * Recursively loads {@link Document}s from the specified directory and its subdirectories.
     * <br>
     * The files are parsed using the specified {@link DocumentParser}.
     * <br>
     * Skips any {@code Document}s that fail to load.
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
     * Recursively loads {@link Document}s from the specified directory and its subdirectories.
     * <br>
     * The files are parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link DocumentParserFactory}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryPath The path to the directory with files.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocumentsRecursively(String directoryPath) {
        return loadDocumentsRecursively(directoryPath, DEFAULT_DOCUMENT_PARSER);
    }

    /**
     * Recursively loads matching {@link Document}s from the specified directory and its subdirectories.
     * <br>
     * The files are parsed using the specified {@link DocumentParser}.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryPath  The path to the directory with files.
     * @param pathMatcher    Only files whose paths match the provided {@link PathMatcher} will be loaded.
     *                       For example, using {@code FileSystems.getDefault().getPathMatcher("glob:**.txt")} will
     *                       load all files from {@code directoryPath} and its subdirectories with a {@code txt} extension.
     *                       When traversing the directory tree, each file path is converted from absolute to relative
     *                       (relative to {@code directoryPath}) before being matched by a {@code pathMatcher}.
     *                       Thus, {@code pathMatcher} should use relative patterns.
     *                       Please be aware that {@code *.txt} pattern (with a single asterisk) will match files
     *                       only in the {@code directoryPath}, but it will not match files from the subdirectories
     *                       of {@code directoryPath}.
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
            return loadDocuments(pathStream, pathMatcher, directoryPath, documentParser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Recursively loads matching {@link Document}s from the specified directory and its subdirectories.
     * <br>
     * The files are parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link DocumentParserFactory}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryPath The path to the directory with files.
     * @param pathMatcher   Only files whose paths match the provided {@link PathMatcher} will be loaded.
     *                      For example, using {@code FileSystems.getDefault().getPathMatcher("glob:**.txt")} will
     *                      load all files from {@code directoryPath} and its subdirectories with a {@code txt} extension.
     *                      When traversing the directory tree, each file path is converted from absolute to relative
     *                      (relative to {@code directoryPath}) before being matched by a {@code pathMatcher}.
     *                      Thus, {@code pathMatcher} should use relative patterns.
     *                      Please be aware that {@code *.txt} pattern (with a single asterisk) will match files
     *                      only in the {@code directoryPath}, but it will not match files from the subdirectories
     *                      of {@code directoryPath}.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocumentsRecursively(Path directoryPath, PathMatcher pathMatcher) {
        return loadDocumentsRecursively(directoryPath, pathMatcher, DEFAULT_DOCUMENT_PARSER);
    }

    /**
     * Recursively loads matching {@link Document}s from the specified directory and its subdirectories.
     * <br>
     * The files are parsed using the specified {@link DocumentParser}.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryPath  The path to the directory with files.
     * @param pathMatcher    Only files whose paths match the provided {@link PathMatcher} will be loaded.
     *                       For example, using {@code FileSystems.getDefault().getPathMatcher("glob:**.txt")} will
     *                       load all files from {@code directoryPath} and its subdirectories with a {@code txt} extension.
     *                       When traversing the directory tree, each file path is converted from absolute to relative
     *                       (relative to {@code directoryPath}) before being matched by a {@code pathMatcher}.
     *                       Thus, {@code pathMatcher} should use relative patterns.
     *                       Please be aware that {@code *.txt} pattern (with a single asterisk) will match files
     *                       only in the {@code directoryPath}, but it will not match files from the subdirectories
     *                       of {@code directoryPath}.
     * @param documentParser The parser to be used for parsing text from each file.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocumentsRecursively(String directoryPath,
                                                          PathMatcher pathMatcher,
                                                          DocumentParser documentParser) {
        return loadDocumentsRecursively(Paths.get(directoryPath), pathMatcher, documentParser);
    }

    /**
     * Recursively loads matching {@link Document}s from the specified directory and its subdirectories.
     * <br>
     * The files are parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link DocumentParserFactory}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryPath The path to the directory with files.
     * @param pathMatcher   Only files whose paths match the provided {@link PathMatcher} will be loaded.
     *                      For example, using {@code FileSystems.getDefault().getPathMatcher("glob:**.txt")} will
     *                      load all files from {@code directoryPath} and its subdirectories with a {@code txt} extension.
     *                      When traversing the directory tree, each file path is converted from absolute to relative
     *                      (relative to {@code directoryPath}) before being matched by a {@code pathMatcher}.
     *                      Thus, {@code pathMatcher} should use relative patterns.
     *                      Please be aware that {@code *.txt} pattern (with a single asterisk) will match files
     *                      only in the {@code directoryPath}, but it will not match files from the subdirectories
     *                      of {@code directoryPath}.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocumentsRecursively(String directoryPath, PathMatcher pathMatcher) {
        return loadDocumentsRecursively(directoryPath, pathMatcher, DEFAULT_DOCUMENT_PARSER);
    }

    private static List<Document> loadDocuments(Stream<Path> pathStream,
                                                PathMatcher pathMatcher,
                                                Path pathMatcherRoot,
                                                DocumentParser documentParser) {
        List<Document> documents = new ArrayList<>();

        pathStream
                .filter(Files::isRegularFile)
                // converting absolute path into relative before using pathMatcher
                // because patterns defined in pathMatcher are relative to pathMatcherRoot (directoryPath)
                .map(pathMatcherRoot::relativize)
                .filter(pathMatcher::matches)
                // converting relative path back into absolute before loading document
                .map(pathMatcherRoot::resolve)
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

    private static DocumentParser loadDocumentParser() {

        Collection<DocumentParserFactory> factories = loadFactories(DocumentParserFactory.class);

        if (factories.size() > 1) {
            throw new RuntimeException("Conflict: multiple document parsers have been found in the classpath. " +
                    "Please explicitly specify the one you wish to use.");
        }

        for (DocumentParserFactory factory : factories) {
            return factory.create();
        }

        return null;
    }
}
