package dev.langchain4j.data.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static java.nio.file.Files.isDirectory;

/**
 * Loads multiple documents from the specified directory, detecting document types automatically.
 * Does not use recursion.
 */
public class DirectoryLoader {

    private final Path directoryPath;

    public DirectoryLoader(Path directoryPath) {
        if (!isDirectory(directoryPath)) {
            throw illegalArgument("%s is not a directory", directoryPath);
        }
        this.directoryPath = directoryPath;
    }

    public DirectoryLoader(String directoryPath) {
        this(Paths.get(directoryPath));
    }

    public List<Document> load() {
        List<Document> documents = new ArrayList<>();

        try (Stream<Path> paths = Files.list(directoryPath)) {
            paths.filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        DocumentLoader loader = DocumentLoader.from(filePath);
                        Document document = loader.load();
                        documents.add(document);
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return documents;
    }

    public static DirectoryLoader from(Path directoryPath) {
        return new DirectoryLoader(directoryPath);
    }

    public static DirectoryLoader from(String directoryPath) {
        return new DirectoryLoader(directoryPath);
    }
}
