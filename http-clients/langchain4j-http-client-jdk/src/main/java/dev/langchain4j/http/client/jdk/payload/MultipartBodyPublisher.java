package dev.langchain4j.http.client.jdk.payload;

import dev.langchain4j.http.client.MultipartFile;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MultipartBodyPublisher {

    private static final String BOUNDARY = "----langChain4j";
    private static final String CRLF = "\r\n";

    private final List<byte[]> parts = new ArrayList<>();

    public MultipartBodyPublisher addFormField(String name, String value) {
        String part = "--" + BOUNDARY + CRLF + "Content-Disposition: form-data; name=\""
                + name + "\"" + CRLF + CRLF
                + value
                + CRLF;
        parts.add(part.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    public MultipartBodyPublisher addFile(String name, MultipartFile multipartFile) {
        String header = "--" + BOUNDARY + CRLF + "Content-Disposition: form-data; name=\""
                + name + "\"; filename=\"" + multipartFile.filename() + "\"" + CRLF + "Content-Type: "
                + multipartFile.contentType() + CRLF + CRLF;

        parts.add(header.getBytes(StandardCharsets.UTF_8));
        parts.add(multipartFile.content());
        parts.add(CRLF.getBytes(StandardCharsets.UTF_8));

        return this;
    }

    public HttpRequest.BodyPublisher build() {
        String end = "--" + BOUNDARY + "--" + CRLF;
        parts.add(end.getBytes(StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(parts);
    }
}
