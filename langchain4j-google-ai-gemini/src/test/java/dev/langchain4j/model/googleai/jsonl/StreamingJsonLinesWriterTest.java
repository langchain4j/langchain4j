package dev.langchain4j.model.googleai.jsonl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreamingJsonLinesWriterTest {

    private StreamingJsonLinesWriter subject;

    @Nested
    @DisplayName("Constructor: Path")
    class PathConstructor {

        @Test
        void should_create_writer_and_write_to_file(@TempDir Path tempDir) throws IOException {
            // Given
            var path = tempDir.resolve("output.jsonl");
            subject = new StreamingJsonLinesWriter(path);
            var data = new TestData("file-test", 100);

            // When
            subject.write(data);
            subject.close();

            // Then
            var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            assertThat(lines).hasSize(1).first().isEqualTo("{\"name\":\"file-test\",\"value\":100}");
        }
    }

    @Nested
    @DisplayName("Constructor: OutputStream")
    class OutputStreamConstructor {

        @Test
        void should_wrap_output_stream_correctly() throws IOException {
            // Given
            var outputStream = new ByteArrayOutputStream();
            subject = new StreamingJsonLinesWriter(outputStream);
            var data = new TestData("stream-test", 200);

            // When
            subject.write(data);
            subject.flush();

            // Then
            var output = outputStream.toString(StandardCharsets.UTF_8);
            assertThat(output).isEqualTo("{\"name\":\"stream-test\",\"value\":200}" + System.lineSeparator());
        }
    }

    @Nested
    @DisplayName("write(Object)")
    class WriteObject {

        @Test
        void should_write_single_object_as_json_with_newline() throws IOException {
            // Given
            var outputStream = new ByteArrayOutputStream();
            subject = createSubject(outputStream);
            var data = new TestData("single", 1);

            // When
            subject.write(data);
            subject.flush();

            // Then
            var output = outputStream.toString(StandardCharsets.UTF_8);
            assertThat(output).isEqualTo("{\"name\":\"single\",\"value\":1}" + System.lineSeparator());
        }

        @Test
        void should_not_close_underlying_writer_after_write() throws IOException {
            // Given
            // We use a mock to verify close() is NOT called by Jackson internally
            // due to JsonGenerator.Feature.AUTO_CLOSE_TARGET being disabled
            var writerMock = mock(Writer.class);
            // We need a real BufferedWriter wrapper or the class will wrap it
            // The class allows passing a generic Writer, so passing the mock is fine.
            subject = new StreamingJsonLinesWriter(writerMock, new ObjectMapper());
            var data = new TestData("keep-open", 1);

            // When
            subject.write(data);

            // Then
            // Jackson's writeValue calls write methods, but should NOT call close on the writer
            verify(writerMock)
                    .write(ArgumentMatchers.<char[]>any(), anyInt(), anyInt()); // Verifying some write happened
            // Verify close was specifically NOT called
            verify(writerMock, never()).close();
        }
    }

    @Nested
    @DisplayName("write(Iterable)")
    class WriteIterable {

        @Test
        void should_write_multiple_objects_sequentially() throws IOException {
            // Given
            var outputStream = new ByteArrayOutputStream();
            subject = createSubject(outputStream);
            var list = List.of(new TestData("one", 1), new TestData("two", 2));

            // When
            subject.write(list);
            subject.flush();

            // Then
            var output = outputStream.toString(StandardCharsets.UTF_8);
            var expected =
                    """
                    {"name":"one","value":1}
                    {"name":"two","value":2}
                    """
                            .replace("\n", System.lineSeparator()); // Adjust for platform-specific newline

            assertThat(output).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("flush")
    class Flush {

        @Test
        void should_delegate_flush_to_underlying_writer() throws IOException {
            // Given
            var writerMock = mock(BufferedWriter.class);
            subject = new StreamingJsonLinesWriter(writerMock, new ObjectMapper());

            // When
            subject.flush();

            // Then
            verify(writerMock).flush();
        }
    }

    @Nested
    @DisplayName("close")
    class Close {

        @Test
        void should_delegate_close_to_underlying_writer() throws IOException {
            // Given
            var writerMock = mock(BufferedWriter.class);
            subject = new StreamingJsonLinesWriter(writerMock, new ObjectMapper());

            // When
            subject.close();

            // Then
            verify(writerMock).close();
        }
    }

    private StreamingJsonLinesWriter createSubject(ByteArrayOutputStream outputStream) {
        return new StreamingJsonLinesWriter(outputStream);
    }

    private record TestData(String name, int value) {}
}
