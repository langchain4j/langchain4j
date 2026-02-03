package dev.langchain4j.http.client.apache;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.http.client.FormDataFile;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class MultipartBodyPublisherTest {

    @Test
    void should_build_body_with_single_form_field() {
        MultipartBodyPublisher publisher = new MultipartBodyPublisher();

        publisher.addField("field1", "value1");
        publisher.build();

        String body = bodyAsString(publisher.parts());

        String expected =
                """
                        ------LangChain4j
                        Content-Disposition: form-data; name="field1"

                        value1
                        ------LangChain4j--
                        """;

        assertEquals(normalize(expected), body);
    }

    @Test
    void should_build_body_with_file() {
        MultipartBodyPublisher publisher = new MultipartBodyPublisher();

        FormDataFile file = new FormDataFile("test.txt", "text/plain", "hello".getBytes(UTF_8));

        publisher.addFile("file", file);
        publisher.build();

        String body = bodyAsString(publisher.parts());

        String expected =
                """
                        ------LangChain4j
                        Content-Disposition: form-data; name="file"; filename="test.txt"
                        Content-Type: text/plain

                        hello
                        ------LangChain4j--
                        """;

        assertEquals(normalize(expected), body);
    }

    @Test
    void should_build_body_with_field_then_file() {
        MultipartBodyPublisher publisher = new MultipartBodyPublisher();

        FormDataFile file = new FormDataFile("test.txt", "text/plain", "hello".getBytes(UTF_8));

        publisher.addField("field1", "value1");
        publisher.addFile("file", file);
        publisher.build();

        String body = bodyAsString(publisher.parts());

        String expected =
                """
                        ------LangChain4j
                        Content-Disposition: form-data; name="field1"

                        value1
                        ------LangChain4j
                        Content-Disposition: form-data; name="file"; filename="test.txt"
                        Content-Type: text/plain

                        hello
                        ------LangChain4j--
                        """;

        assertEquals(normalize(expected), body);
    }

    private static String bodyAsString(List<byte[]> parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toString(UTF_8);
    }

    private static String normalize(String s) {
        return s.replace("\n", "\r\n");
    }
}
