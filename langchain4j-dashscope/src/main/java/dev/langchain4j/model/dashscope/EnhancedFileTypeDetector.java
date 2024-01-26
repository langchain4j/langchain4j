package dev.langchain4j.model.dashscope;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.spi.FileTypeDetector;

/*
 * This dashscope sdk does not support directly passing in the image's mimeType. Instead, it utilizes
 * the FileTypeDetector to detect the mimeType. The default FileTypeDetector provided by the JDK
 * (which might be sun.nio.fs.DefaultFileTypeDetector) performs poorly.
 * Here, we are using java.net.URLConnection.guessContentTypeFromStream() to identify the mimeType,
 * which yields very accurate results for image-type files.
 */
public class EnhancedFileTypeDetector extends FileTypeDetector {
    @Override
    public String probeContentType(Path path) {
        try (InputStream in = new BufferedInputStream(
                Files.newInputStream(path, StandardOpenOption.READ))) {
            return URLConnection.guessContentTypeFromStream(in);
        } catch (IOException e) {
            return URLConnection.guessContentTypeFromName(path.getFileName().toString());
        }
    }
}
