package dev.langchain4j.data.document.loader;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.source.ClassPathSource;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DocumentLoader} implementation for loading documents using a {@link ClassPathSource}
 * @author Eric Deandrea
 */
public class ClassPathDocumentLoader {
    private static final Logger LOG = LoggerFactory.getLogger(ClassPathDocumentLoader.class);
    private static final DocumentParser DEFAULT_DOCUMENT_PARSER =
            getOrDefault(DocumentParserLoader.loadDocumentParser(), TextDocumentParser::new);

    private ClassPathDocumentLoader() {}

    /**
     * Loads a {@link Document} from the specified file path.
     * <br>
     * The file is parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link dev.langchain4j.spi.data.document.parser.DocumentParserFactory DocumentParserFactoru}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Returned {@code Document} contains all the textual information from the file.
     *
     * @param pathOnClasspath The path on the classpath to the file.
     * @return document
     * @throws IllegalArgumentException If specified path is not a file.
     */
    public static Document loadDocument(String pathOnClasspath) {
        return loadDocument(pathOnClasspath, DEFAULT_DOCUMENT_PARSER);
    }

    /**
     * Loads a {@link Document} from the specified file path.
     * <br>
     * The file is parsed using the specified {@link DocumentParser}.
     * <br>
     * Returned {@code Document} contains all the textual information from the file.
     *
     * @param pathOnClasspath The path on the classpath to the file.
     * @param documentParser The parser to be used for parsing text from the file.
     * @return document
     * @throws IllegalArgumentException If specified path is not a file.
     */
    public static Document loadDocument(String pathOnClasspath, DocumentParser documentParser) {
        var classPathSource = ClassPathSource.from(pathOnClasspath);

        try {
            var uri = classPathSource.url().toURI();

            if (classPathSource.isInsideArchive()) {
                try (var fs = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
                    return loadDocument(classPathSource, fs.getPath(pathOnClasspath), documentParser);
                }
            } else {
                return loadDocument(classPathSource, Path.of(uri), documentParser);
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Document loadDocument(ClassPathSource classPathSource, Path path, DocumentParser documentParser) {
        if (!isRegularFile(path)) {
            throw illegalArgument("'%s' is not a file", path);
        }

        return DocumentLoader.load(classPathSource, documentParser);
    }

    /**
     * Loads {@link Document}s from the specified directory. Does not use recursion.
     * <br>
     * The files are parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link dev.langchain4j.spi.data.document.parser.DocumentParserFactory DocumentParserFactoru}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryOnClasspath The path to the directory on the classpath with files.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocuments(String directoryOnClasspath) {
        return loadDocuments(directoryOnClasspath, DEFAULT_DOCUMENT_PARSER);
    }

    /**
     * Loads {@link Document}s from the specified directory. Does not use recursion.
     * <br>
     * The files are parsed using the specified {@link DocumentParser}.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryOnClasspath  The path to the directory on the classpath with files.
     * @param documentParser The parser to be used for parsing text from each file.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocuments(String directoryOnClasspath, DocumentParser documentParser) {
        return loadDocuments(directoryOnClasspath, path -> true, documentParser);
    }

    /**
     * Loads matching {@link Document}s from the specified directory. Does not use recursion.
     * <br>
     * The files are parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link dev.langchain4j.spi.data.document.parser.DocumentParserFactory DocumentParserFactoru}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryOnClasspath The path to the directory on the classpath with files.
     * @param pathMatcher   Only files whose paths match the provided {@link PathMatcher} will be loaded.
     *                      For example, using {@code FileSystems.getDefault().getPathMatcher("glob:*.txt")}
     *                      will load all files from {@code directoryPath} with a {@code txt} extension.
     *                      When traversing the directory, each file path is converted from absolute to relative
     *                      (relative to {@code directoryPath}) before being matched by a {@code pathMatcher}.
     *                      Thus, {@code pathMatcher} should use relative patterns.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocuments(String directoryOnClasspath, PathMatcher pathMatcher) {
        return loadDocuments(directoryOnClasspath, pathMatcher, DEFAULT_DOCUMENT_PARSER);
    }

    /**
     * Loads matching {@link Document}s from the specified directory. Does not use recursion.
     * <br>
     * The files are parsed using the specified {@link DocumentParser}.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryOnClasspath The path to the directory on the classpath with files.
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
    public static List<Document> loadDocuments(
            String directoryOnClasspath, PathMatcher pathMatcher, DocumentParser documentParser) {
        return loadDocuments(
                directoryOnClasspath, pathMatcher, documentParser, ClassPathDocumentLoader::getFilesInDirectory);
    }

    private static List<Document> loadDocuments(
            String directoryOnClasspath,
            PathMatcher pathMatcher,
            DocumentParser documentParser,
            Function<Path, Stream<Path>> pathStreamFunction) {
        var classPathSource = ClassPathSource.from(directoryOnClasspath);

        try {
            var uri = classPathSource.url().toURI();

            if (classPathSource.isInsideArchive()) {
                try (var fs = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
                    return loadDocuments(
                            classPathSource,
                            fs.getPath(directoryOnClasspath),
                            pathMatcher,
                            documentParser,
                            pathStreamFunction);
                }
            } else {
                return loadDocuments(classPathSource, Path.of(uri), pathMatcher, documentParser, pathStreamFunction);
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Document> loadDocuments(
            ClassPathSource rootDirectoryClassPathSource,
            Path path,
            PathMatcher pathMatcher,
            DocumentParser documentParser,
            Function<Path, Stream<Path>> pathStreamFunction) {
        if (!isDirectory(path)) {
            throw illegalArgument("'%s' is not a directory", path);
        }

        try (var pathStream = pathStreamFunction.apply(path)) {
            return loadDocuments(pathStream, rootDirectoryClassPathSource, path, pathMatcher, documentParser);
        }
    }

    private static List<Document> loadDocuments(
            Stream<Path> pathStream,
            ClassPathSource rootDirectoryClassPathSource,
            Path pathMatcherRoot,
            PathMatcher pathMatcher,
            DocumentParser documentParser) {
        return pathStream
                .filter(Files::isRegularFile)
                // converting absolute pathMatcherRoot into relative before using pathMatcher
                // because patterns defined in pathMatcher are relative to pathMatcherRoot (directoryPath)
                .filter(p -> pathMatcher.matches(
                        Path.of(pathMatcherRoot.relativize(p).toString().replace('/', File.separatorChar))))
                .map(p -> {
                    try {
                        var relativePath = getRelativePath(rootDirectoryClassPathSource, p);

                        return loadDocument(
                                ClassPathSource.from(relativePath, rootDirectoryClassPathSource.classLoader()),
                                p,
                                documentParser);
                    } catch (BlankDocumentException ignored) {
                        // blank/empty documents are ignored
                        return null;
                    } catch (Exception e) {
                        String message = (e.getCause() != null) ? e.getCause().getMessage() : e.getMessage();
                        LOG.warn("Failed to load '{}': {}", p, message);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static String getRelativePath(ClassPathSource rootDirectoryClassPathSource, Path subPath) {
        if (rootDirectoryClassPathSource.isInsideArchive()) {
            return subPath.toString();
        }

        try {
            var rootClasspathURI =
                    rootDirectoryClassPathSource.classLoader().getResource(".").toURI();
            var rootClasspathPath = Path.of(rootClasspathURI);
            var relativeClasspathPath = rootClasspathPath.relativize(subPath);

            return relativeClasspathPath.toString().replace(File.separatorChar, '/');
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Recursively loads {@link Document}s from the specified directory and its subdirectories.
     * <br>
     * The files are parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link dev.langchain4j.spi.data.document.parser.DocumentParserFactory DocumentParserFactoru}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryOnClasspath The path to the directory on the classpath with files.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocumentsRecursively(String directoryOnClasspath) {
        return loadDocumentsRecursively(directoryOnClasspath, DEFAULT_DOCUMENT_PARSER);
    }

    /**
     * Recursively loads {@link Document}s from the specified directory and its subdirectories.
     * <br>
     * The files are parsed using the specified {@link DocumentParser}.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryOnClasspath The path to the directory on the classpath with files.
     * @param documentParser The parser to be used for parsing text from each file.
     * @return list of documents
     * @throws IllegalArgumentException If specified path is not a directory.
     */
    public static List<Document> loadDocumentsRecursively(String directoryOnClasspath, DocumentParser documentParser) {
        return loadDocumentsRecursively(directoryOnClasspath, path -> true, documentParser);
    }

    /**
     * Recursively loads matching {@link Document}s from the specified directory and its subdirectories.
     * <br>
     * The files are parsed using the default {@link DocumentParser}.
     * The default {@code DocumentParser} is loaded through SPI (see {@link dev.langchain4j.spi.data.document.parser.DocumentParserFactory DocumentParserFactoru}).
     * If no {@code DocumentParserFactory} is available in the classpath, a {@link TextDocumentParser} is used.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryOnClasspath The path to the directory on the classpath with files.
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
    public static List<Document> loadDocumentsRecursively(String directoryOnClasspath, PathMatcher pathMatcher) {
        return loadDocumentsRecursively(directoryOnClasspath, pathMatcher, DEFAULT_DOCUMENT_PARSER);
    }

    /**
     * Recursively loads matching {@link Document}s from the specified directory and its subdirectories.
     * <br>
     * The files are parsed using the specified {@link DocumentParser}.
     * <br>
     * Skips any {@code Document}s that fail to load.
     *
     * @param directoryOnClasspath The path to the directory on the classpath with files.
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
    public static List<Document> loadDocumentsRecursively(
            String directoryOnClasspath, PathMatcher pathMatcher, DocumentParser documentParser) {
        return loadDocuments(
                directoryOnClasspath,
                pathMatcher,
                documentParser,
                ClassPathDocumentLoader::getFilesInDirectoryRecursively);
    }

    private static Stream<Path> getFilesInDirectory(Path directoryPath) {
        try {
            return Files.list(directoryPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<Path> getFilesInDirectoryRecursively(Path directoryPath) {
        try {
            return Files.walk(directoryPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
