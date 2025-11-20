package dev.langchain4j.http.client.jdk;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MultipartBodyPublisher {

    private static final String BOUNDARY = "----langChain4j";
    private final List<byte[]> parts = new ArrayList<>();

    public String getBoundary() {
        return BOUNDARY;
    }

    public MultipartBodyPublisher addFormField(String name, String value) {
        String part = "--" + BOUNDARY + "\r\n" + "Content-Disposition: form-data; name=\""
                + name + "\"\r\n" + "\r\n"
                + value
                + "\r\n";
        parts.add(part.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    public MultipartBodyPublisher addFile(String name, Path file, String contentType) throws IOException {
        String header = "--" + BOUNDARY + "\r\n" + "Content-Disposition: form-data; name=\""
                + name + "\"; filename=\"" + file.getFileName() + "\"\r\n" + "Content-Type: "
                + contentType + "\r\n" + "\r\n";

        parts.add(header.getBytes(StandardCharsets.UTF_8));
        parts.add(Files.readAllBytes(file));
        parts.add("\r\n".getBytes(StandardCharsets.UTF_8));

        return this;
    }

    public HttpRequest.BodyPublisher build() {
        String end = "--" + BOUNDARY + "--\r\n";
        parts.add(end.getBytes(StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(parts);
    }
}
