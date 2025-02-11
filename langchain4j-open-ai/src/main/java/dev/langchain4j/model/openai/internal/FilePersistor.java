package dev.langchain4j.model.openai.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.UUID;

class FilePersistor { // TODO

    static Path persistFromUri(URI uri, Path destinationFolder) {
        try {
            Path fileName = Paths.get(uri.getPath()).getFileName();
            Path destinationFilePath = destinationFolder.resolve(fileName);
            try (InputStream inputStream = uri.toURL().openStream()) {
                Files.copy(inputStream, destinationFilePath, StandardCopyOption.REPLACE_EXISTING);
            }

            return destinationFilePath;
        } catch (IOException e) {
            throw new RuntimeException("Error persisting file from URI: " + uri, e);
        }
    }

    public static Path persistFromBase64String(String base64EncodedString, Path destinationFolder) throws IOException {
        byte[] decodedBytes = Base64.getDecoder().decode(base64EncodedString);
        Path destinationFile = destinationFolder.resolve(randomFileName());

        Files.write(destinationFile, decodedBytes, StandardOpenOption.CREATE);

        return destinationFile;
    }

    private static String randomFileName() {
        return UUID.randomUUID().toString().replaceAll("-", "").substring(0, 20);
    }
}
