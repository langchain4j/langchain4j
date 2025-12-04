package dev.langchain4j.http.client.jdk.payload;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.http.client.MultipartFile;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class MultipartBodyPublisherTest {

    @Test
    void should_build_body_with_single_form_field() {
        MultipartBodyPublisher publisher = new MultipartBodyPublisher();

        publisher.addFormField("field1", "value1");
        publisher.build();

        String body = bodyAsString(publisher.parts());

        String expected =
                """
                ------langChain4j
                Content-Disposition: form-data; name="field1"

                value1
                ------langChain4j--
                """;

        assertEquals(normalize(expected), body);
    }

    @Test
    void should_build_body_with_file() {
        MultipartBodyPublisher publisher = new MultipartBodyPublisher();

        MultipartFile file = new MultipartFile("test.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        publisher.addFile("file", file);
        publisher.build();

        String body = bodyAsString(publisher.parts());

        String expected =
                """
                ------langChain4j
                Content-Disposition: form-data; name="file"; filename="test.txt"
                Content-Type: text/plain

                hello
                ------langChain4j--
                """;

        assertEquals(normalize(expected), body);
    }

    @Test
    void should_build_body_with_field_then_file() {
        MultipartBodyPublisher publisher = new MultipartBodyPublisher();

        MultipartFile file = new MultipartFile("test.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        publisher.addFormField("field1", "value1");
        publisher.addFile("file", file);
        publisher.build();

        String body = bodyAsString(publisher.parts());

        String expected =
                """
                ------langChain4j
                Content-Disposition: form-data; name="field1"

                value1
                ------langChain4j
                Content-Disposition: form-data; name="file"; filename="test.txt"
                Content-Type: text/plain

                hello
                ------langChain4j--
                """;

        assertEquals(normalize(expected), body);
    }

    private static String bodyAsString(List<byte[]> parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String normalize(String s) {
        return s.replace("\n", "\r\n");
    }
}
