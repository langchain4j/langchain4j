package dev.langchain4j.http.client.jdk;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.nio.charset.StandardCharsets.UTF_8;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.FormDataFile;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Experimental
class MultipartBodyPublisher {

    private static final String CRLF = "\r\n";

    private final String boundary = "----LangChain4j-" + UUID.randomUUID();
    private final List<byte[]> parts = new ArrayList<>();

    List<byte[]> parts() {
        return parts;
    }

    /**
     * The {@code Content-Type} header value (including the boundary) that must accompany the body produced by
     * {@link #build()}. Without this header the server cannot parse the multipart payload.
     */
    String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    void addField(String name, String value) {
        String part = "--" + boundary + CRLF + "Content-Disposition: form-data; name=\""
                + name + "\"" + CRLF + CRLF
                + value
                + CRLF;
        parts.add(part.getBytes(UTF_8));
    }

    void addFile(String name, FormDataFile file) {
        String header = "--" + boundary + CRLF + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\""
                + file.fileName() + "\"" + CRLF;
        if (!isNullOrBlank(file.contentType())) {
            header += "Content-Type: " + file.contentType() + CRLF;
        }
        header += CRLF;

        parts.add(header.getBytes(UTF_8));
        parts.add(file.content());
        parts.add(CRLF.getBytes(UTF_8));
    }

    HttpRequest.BodyPublisher build() {
        String end = "--" + boundary + "--" + CRLF;
        parts.add(end.getBytes(UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(parts);
    }
}
