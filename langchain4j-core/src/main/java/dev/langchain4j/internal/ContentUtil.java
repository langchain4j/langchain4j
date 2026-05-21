package dev.langchain4j.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class ContentUtil {

    private ContentUtil() { }

    public static String extractBase64Content(Path filePath) {
        try {
            byte[] fileBytes = Files.readAllBytes(filePath);
            return Base64.getEncoder().encodeToString(fileBytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
