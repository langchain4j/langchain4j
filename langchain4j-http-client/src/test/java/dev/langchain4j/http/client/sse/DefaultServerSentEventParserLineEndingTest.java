package dev.langchain4j.http.client.sse;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultServerSentEventParserLineEndingTest {

    private final ServerSentEventParser parser = new DefaultServerSentEventParser();

    @Test
    void shouldEmitCarriageReturnTerminatedEventBeforeStreamEnds() {
        ServerSentEventParser.Incremental incremental = parser.incremental();
        List<ServerSentEvent> events =
                incremental.feed(ByteBuffer.wrap("event: token\rdata: hello\r\r".getBytes(UTF_8)));

        assertEquals(List.of(new ServerSentEvent("token", "hello")), events);
        assertTrue(incremental.flush().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r", "\r\n"})
    void shouldMatchBlockingParserAtEveryByteSplit(String newline) {
        String input = ": comment" + newline + "event: token" + newline + "data: \u4f60\u597d" + newline
                + "data: second" + newline + newline + "data: final" + newline + newline;
        byte[] bytes = input.getBytes(UTF_8);
        List<ServerSentEvent> expected = new ArrayList<>();
        parser.parse(new ByteArrayInputStream(bytes), new ServerSentEventListener() {
            @Override
            public void onEvent(ServerSentEvent event) {
                expected.add(event);
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });
        assertEquals(
                List.of(new ServerSentEvent("token", "\u4f60\u597d\nsecond"), new ServerSentEvent(null, "final")),
                expected);

        for (int split = 0; split <= bytes.length; split++) {
            ServerSentEventParser.Incremental incremental = parser.incremental();
            List<ServerSentEvent> actual = new ArrayList<>();
            actual.addAll(incremental.feed(ByteBuffer.wrap(bytes, 0, split)));
            actual.addAll(incremental.feed(ByteBuffer.wrap(new byte[0])));
            actual.addAll(incremental.feed(ByteBuffer.wrap(bytes, split, bytes.length - split)));
            assertEquals(expected, actual, "split=" + split);
            assertTrue(incremental.flush().isEmpty(), "split=" + split);
        }

        ServerSentEventParser.Incremental incremental = parser.incremental();
        List<ServerSentEvent> oneByteEvents = new ArrayList<>();
        for (byte value : bytes) {
            oneByteEvents.addAll(incremental.feed(ByteBuffer.wrap(new byte[] {value})));
        }
        assertEquals(expected, oneByteEvents);
        assertTrue(incremental.flush().isEmpty());
    }

    @Test
    void shouldNotIncludeTrailingCarriageReturnInFlushedData() {
        ServerSentEventParser.Incremental incremental = parser.incremental();
        assertTrue(incremental
                .feed(ByteBuffer.wrap("data: hello\r".getBytes(UTF_8)))
                .isEmpty());
        assertEquals(List.of(new ServerSentEvent(null, "hello")), incremental.flush());
        assertTrue(incremental.flush().isEmpty());
    }
}
