package dev.langchain4j.http.client.sse;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultServerSentEventParserEmptyDataTest {

    private final ServerSentEventParser parser = new DefaultServerSentEventParser();

    static Stream<Arguments> dataCases() {
        return Stream.of(
                Arguments.of("data:\n\n", List.of(new ServerSentEvent(null, ""))),
                Arguments.of("data:\ndata:\n\n", List.of(new ServerSentEvent(null, "\n"))),
                Arguments.of("data:\ndata: text\n\n", List.of(new ServerSentEvent(null, "\ntext"))),
                Arguments.of("data: text\ndata:\n\n", List.of(new ServerSentEvent(null, "text\n"))),
                Arguments.of("data: left\ndata:\ndata: right\n\n", List.of(new ServerSentEvent(null, "left\n\nright"))),
                Arguments.of("data: left\ndata: right\n\n", List.of(new ServerSentEvent(null, "left\nright"))),
                Arguments.of("event: ignored\n\ndata: next\n\n", List.of(new ServerSentEvent(null, "next"))),
                Arguments.of(
                        "event: ignored\ndata\n\ndata: next\n\n",
                        List.of(new ServerSentEvent("ignored", ""), new ServerSentEvent(null, "next"))),
                Arguments.of(": comment\n\n", List.of()),
                Arguments.of(
                        "event: empty\ndata: \n\ndata: next\n\n",
                        List.of(new ServerSentEvent("empty", ""), new ServerSentEvent(null, "next"))));
    }

    @ParameterizedTest
    @MethodSource("dataCases")
    void shouldPreserveEmptyDataLinesInBlockingParser(String input, List<ServerSentEvent> expected) {
        List<ServerSentEvent> actual = new ArrayList<>();
        parser.parse(new ByteArrayInputStream(input.getBytes(UTF_8)), new ServerSentEventListener() {
            @Override
            public void onEvent(ServerSentEvent event) {
                actual.add(event);
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("dataCases")
    void shouldPreserveEmptyDataLinesAcrossIncrementalFeeds(String input, List<ServerSentEvent> expected) {
        byte[] bytes = input.getBytes(UTF_8);
        for (int split = 0; split <= bytes.length; split++) {
            ServerSentEventParser.Incremental incremental = parser.incremental();
            List<ServerSentEvent> actual = new ArrayList<>();
            actual.addAll(incremental.feed(ByteBuffer.wrap(bytes, 0, split)));
            actual.addAll(incremental.feed(ByteBuffer.wrap(bytes, split, bytes.length - split)));
            assertEquals(expected, actual, "split=" + split);
            assertTrue(incremental.flush().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"data: text", "data:\ndata: text"})
    void shouldPreserveDataWhenFlushingPartialLine(String input) {
        ServerSentEventParser.Incremental incremental = parser.incremental();
        assertTrue(incremental.feed(ByteBuffer.wrap(input.getBytes(UTF_8))).isEmpty());
        String expected = input.startsWith("data:\n") ? "\ntext" : "text";
        assertEquals(List.of(new ServerSentEvent(null, expected)), incremental.flush());
        assertTrue(incremental.flush().isEmpty());
    }
}
