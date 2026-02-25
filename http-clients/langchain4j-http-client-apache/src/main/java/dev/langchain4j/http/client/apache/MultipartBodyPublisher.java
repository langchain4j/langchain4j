package dev.langchain4j.http.client.apache;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.FormDataFile;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;

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

    void build() {
        String end = "--" + BOUNDARY + "--" + CRLF;
        parts.add(end.getBytes(UTF_8));
    }

    static HttpEntity buildMultipartEntity(
            Map<String, String> formDataFields, Map<String, FormDataFile> formDataFiles) {
        MultipartBodyPublisher publisher = new MultipartBodyPublisher();

        for (Map.Entry<String, String> entry : formDataFields.entrySet()) {
            publisher.addField(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, FormDataFile> entry : formDataFiles.entrySet()) {
            publisher.addFile(entry.getKey(), entry.getValue());
        }

        publisher.build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : publisher.parts()) {
            out.write(part, 0, part.length);
        }

        ContentType contentType =
                ContentType.MULTIPART_FORM_DATA.withParameters(new BasicNameValuePair("boundary", BOUNDARY));
        return new ByteArrayEntity(out.toByteArray(), contentType);
    }
}
