package dev.langchain4j.http.client.jdk;

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

        String expected = """
                        ------LangChain4j
                        Content-Disposition: form-data; name="field1"

                        value1
                        ------LangChain4j--
                        """;

        assertEquals(normalize(expected, publisher), body);
    }

    @Test
    void should_build_body_with_file() {
        MultipartBodyPublisher publisher = new MultipartBodyPublisher();

        FormDataFile file = new FormDataFile("test.txt", "text/plain", "hello".getBytes(UTF_8));

        publisher.addFile("file", file);
        publisher.build();

        String body = bodyAsString(publisher.parts());

        String expected = """
                        ------LangChain4j
                        Content-Disposition: form-data; name="file"; filename="test.txt"
                        Content-Type: text/plain

                        hello
                        ------LangChain4j--
                        """;

        assertEquals(normalize(expected, publisher), body);
    }

    @Test
    void should_build_body_with_field_then_file() {
        MultipartBodyPublisher publisher = new MultipartBodyPublisher();

        FormDataFile file = new FormDataFile("test.txt", "text/plain", "hello".getBytes(UTF_8));

        publisher.addField("field1", "value1");
        publisher.addFile("file", file);
        publisher.build();

        String body = bodyAsString(publisher.parts());

        String expected = """
                        ------LangChain4j
                        Content-Disposition: form-data; name="field1"

                        value1
                        ------LangChain4j
                        Content-Disposition: form-data; name="file"; filename="test.txt"
                        Content-Type: text/plain

                        hello
                        ------LangChain4j--
                        """;

        assertEquals(normalize(expected, publisher), body);
    }

    @Test
    void should_omit_content_type_header_when_content_type_is_null() {
        MultipartBodyPublisher publisher = new MultipartBodyPublisher();

        FormDataFile file = new FormDataFile("audio.wav", null, "hello".getBytes(UTF_8));

        publisher.addFile("file", file);
        publisher.build();

        String body = bodyAsString(publisher.parts());

        String expected = """
                        ------LangChain4j
                        Content-Disposition: form-data; name="file"; filename="audio.wav"

                        hello
                        ------LangChain4j--
                        """;

        assertEquals(normalize(expected, publisher), body);
    }

    @Test
    void should_omit_content_type_header_when_content_type_is_blank() {
        MultipartBodyPublisher publisher = new MultipartBodyPublisher();

        FormDataFile file = new FormDataFile("audio.wav", "  ", "hello".getBytes(UTF_8));

        publisher.addFile("file", file);
        publisher.build();

        String body = bodyAsString(publisher.parts());

        String expected = """
                        ------LangChain4j
                        Content-Disposition: form-data; name="file"; filename="audio.wav"

                        hello
                        ------LangChain4j--
                        """;

        assertEquals(normalize(expected, publisher), body);
    }

    @Test
    void content_type_should_carry_the_boundary_used_in_the_body() {
        MultipartBodyPublisher publisher = new MultipartBodyPublisher();
        publisher.addField("field1", "value1");
        publisher.build();

        // The Content-Type header must advertise the same boundary the body is delimited with, otherwise the
        // server cannot parse the payload.
        org.junit.jupiter.api.Assertions.assertTrue(
                publisher.contentType().startsWith("multipart/form-data; boundary="));
        org.junit.jupiter.api.Assertions.assertTrue(
                bodyAsString(publisher.parts()).contains("--" + boundary(publisher)));
    }

    private static String bodyAsString(List<byte[]> parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toString(UTF_8);
    }

    private static String boundary(MultipartBodyPublisher publisher) {
        return publisher.contentType().substring("multipart/form-data; boundary=".length());
    }

    private static String normalize(String s, MultipartBodyPublisher publisher) {
        return s.replace("\n", "\r\n").replace("----LangChain4j", boundary(publisher));
    }
}
