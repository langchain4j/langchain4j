package dev.langchain4j.data.document.source;

import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileSystemSource implements DocumentSource {

    private static final String FILE_NAME = "file_name";
    private static final String ABSOLUTE_DIRECTORY_PATH = "absolute_directory_path";

    public final Path path;

    public FileSystemSource(Path path) {
        this.path = path;
    }

    @Override
    public InputStream inputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public Metadata sourceMetadata() {
        return new Metadata()
                .add(FILE_NAME, path.getFileName().toString())
                .add(ABSOLUTE_DIRECTORY_PATH, path.getParent().toAbsolutePath().toString());
    }

    public static FileSystemSource from(Path filePath) {
        return new FileSystemSource(filePath);
    }

    public static FileSystemSource from(String filePath) {
        return new FileSystemSource(Paths.get(filePath));
    }

    public static FileSystemSource from(URI fileUri) {
        return new FileSystemSource(Paths.get(fileUri));
    }

    public static FileSystemSource from(File file) {
        return new FileSystemSource(file.toPath());
    }
}
