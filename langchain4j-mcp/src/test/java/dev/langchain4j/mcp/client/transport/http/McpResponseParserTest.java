package dev.langchain4j.mcp.client.transport.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class McpResponseParserTest {

    private final McpResponseParser parser = new McpResponseParser();

    @Test
    void preserves_sse_fields_and_blank_lines() {
        String body = ": comment\nid: event-1\nretry: 100\nevent: message\ndata: first\ndata: second\n\n";

        assertThat(parse(body))
                .extracting(ServerSentEvent::data)
                .containsExactly(
                        ": comment", "id: event-1", "retry: 100", "event: message", "data: first", "data: second", "");
    }

    @Test
    void preserves_json_response_lines() {
        assertThat(parse("{\r\n  \"result\": {}\r\n}"))
                .extracting(ServerSentEvent::data)
                .containsExactly("{", "  \"result\": {}", "}");
    }

    @Test
    void preserves_each_line_ending_without_creating_a_phantom_final_line() {
        var lines = parse("first\rsecond\nthird\r\nfourth");

        assertThat(lines).extracting(ServerSentEvent::data).containsExactly("first", "second", "third", "fourth");
        assertThat(lines)
                .extracting(line -> ((McpResponseParser.Line) line).ending())
                .containsExactly("\r", "\n", "\r\n", "");
        assertThat(parse("\r\n")).extracting(ServerSentEvent::data).containsExactly("");
        assertThat(parse("\r")).extracting(ServerSentEvent::data).containsExactly("");
        assertThat(parse("\n")).extracting(ServerSentEvent::data).containsExactly("");
        assertThat(parse("")).isEmpty();
    }

    @Test
    void incremental_waits_for_a_possible_line_feed_after_carriage_return() {
        var incremental = parser.incremental();

        assertThat(incremental.feed(ByteBuffer.wrap("first\r".getBytes(UTF_8)))).isEmpty();
        assertThat(reconstruct(incremental.feed(ByteBuffer.wrap("\nsecond\r".getBytes(UTF_8)))))
                .isEqualTo("first\r\n");
        assertThat(reconstruct(incremental.feed(ByteBuffer.wrap("third\r".getBytes(UTF_8)))))
                .isEqualTo("second\r");
        assertThat(reconstruct(incremental.flush())).isEqualTo("third\r");
        assertThat(incremental.flush()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "\n",
                "\r",
                "\r\n",
                "\n\n",
                "\r\r",
                "first",
                "first\n",
                "first\r",
                "first\r\n",
                "first\rsecond\nthird\r\nfourth",
                "data: žluťoučký 🦋\r\n\r\nid: résumé\n\ndata: 終"
            })
    void incremental_matches_blocking_at_every_chunk_boundary(String body) {
        byte[] bytes = body.getBytes(UTF_8);
        List<ServerSentEvent> expected = parse(body);
        assertThat(reconstruct(expected)).isEqualTo(body);

        for (int boundary = 0; boundary <= bytes.length; boundary++) {
            var incremental = parser.incremental();
            List<ServerSentEvent> actual = new ArrayList<>();
            actual.addAll(incremental.feed(ByteBuffer.wrap(bytes, 0, boundary)));
            actual.addAll(incremental.feed(ByteBuffer.wrap(bytes, boundary, bytes.length - boundary)));
            actual.addAll(incremental.flush());
            assertThat(actual).as("split at byte %s", boundary).isEqualTo(expected);
            assertThat(reconstruct(actual))
                    .as("body split at byte %s", boundary)
                    .isEqualTo(body);
            assertThat(incremental.flush()).isEmpty();
        }
    }

    @Test
    void incremental_preserves_utf8_across_single_byte_chunks() {
        String body = "data: 🦋 žluťoučký 終\r\n\r\n";
        var incremental = parser.incremental();
        List<ServerSentEvent> actual = new ArrayList<>();

        for (byte next : body.getBytes(UTF_8)) {
            actual.addAll(incremental.feed(ByteBuffer.wrap(new byte[] {next})));
        }
        actual.addAll(incremental.flush());

        assertThat(actual).extracting(ServerSentEvent::data).containsExactly("data: 🦋 žluťoučký 終", "");
        assertThat(reconstruct(actual)).isEqualTo(body);
    }

    @Test
    void incremental_handles_direct_buffers_and_large_unterminated_lines() {
        String body = "x".repeat(100_000) + "🦋";
        byte[] bytes = body.getBytes(UTF_8);
        var incremental = parser.incremental();
        for (int offset = 0; offset < bytes.length; offset += 31) {
            var chunk = ByteBuffer.allocateDirect(Math.min(31, bytes.length - offset));
            chunk.put(bytes, offset, chunk.capacity()).flip();
            assertThat(incremental.feed(chunk)).isEmpty();
            assertThat(chunk.hasRemaining()).isFalse();
        }

        assertThat(incremental.flush()).extracting(ServerSentEvent::data).containsExactly(body);
    }

    @Test
    void blocking_parser_stops_after_cancellation() {
        List<ServerSentEvent> actual = new ArrayList<>();
        parser.parse(new ByteArrayInputStream("first\nsecond\n".getBytes(UTF_8)), new ServerSentEventListener() {
            @Override
            public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                actual.add(event);
                context.parsingHandle().cancel();
            }

            @Override
            public void onError(Throwable throwable) {
                throw new AssertionError(throwable);
            }
        });

        assertThat(actual).extracting(ServerSentEvent::data).containsExactly("first");
    }

    @Test
    void blocking_parser_reports_read_failures() {
        IOException failure = new IOException("broken response");
        List<Throwable> errors = new ArrayList<>();
        parser.parse(
                new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw failure;
                    }
                },
                new ServerSentEventListener() {
                    @Override
                    public void onError(Throwable throwable) {
                        errors.add(throwable);
                    }
                });

        assertThat(errors).containsExactly(failure);
    }

    private List<ServerSentEvent> parse(String body) {
        List<ServerSentEvent> events = new ArrayList<>();
        parser.parse(new ByteArrayInputStream(body.getBytes(UTF_8)), new ServerSentEventListener() {
            @Override
            public void onEvent(ServerSentEvent event) {
                events.add(event);
            }

            @Override
            public void onError(Throwable throwable) {
                throw new AssertionError(throwable);
            }
        });
        return events;
    }

    private static String reconstruct(List<ServerSentEvent> lines) {
        StringBuilder body = new StringBuilder();
        for (ServerSentEvent line : lines) {
            assertThat(line.event()).isNull();
            body.append(line.data()).append(((McpResponseParser.Line) line).ending());
        }
        return body.toString();
    }
}
