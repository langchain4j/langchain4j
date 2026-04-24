package dev.langchain4j.http.client.jdk;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.FormDataFile;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@Experimental
class MultipartBodyPublisher {

    private static final String BOUNDARY = "----LangChain4j";
    private static final String CRLF = "\r\n";

    private final List<byte[]> parts = new ArrayList<>();

    List<byte[]> parts() {
        return parts;
    }

    void addField(String name, String value) {
        String part = "--" + BOUNDARY + CRLF + "Content-Disposition: form-data; name=\""
                + name + "\"" + CRLF + CRLF
                + value
                + CRLF;
        parts.add(part.getBytes(UTF_8));
    }

    void addFile(String name, FormDataFile file) {
        String header = "--" + BOUNDARY + CRLF + "Content-Disposition: form-data; name=\""
                + name + "\"; filename=\"" + file.fileName() + "\"" + CRLF + "Content-Type: "
                + file.contentType() + CRLF + CRLF;

        parts.add(header.getBytes(UTF_8));
        parts.add(file.content());
        parts.add(CRLF.getBytes(UTF_8));
    }

    HttpRequest.BodyPublisher build() {
        String end = "--" + BOUNDARY + "--" + CRLF;
        parts.add(end.getBytes(UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(parts);
    }
}
