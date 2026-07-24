package dev.langchain4j.http.client.sse;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultServerSentEventParserTest {

    private final ServerSentEventParser parser = new DefaultServerSentEventParser();

    @Mock
    private ServerSentEventListener listener;

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractions(listener);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "data: Simple message",
                "data: Simple message\n",
                "\ndata: Simple message",
                "\ndata: Simple message\n",
                "\n\ndata: Simple message",
                "data: Simple message\n\n",
                "\n\ndata: Simple message\n\n",
            })
    void shouldParseSimpleSingleLineEvent(String input) {

        // given
        InputStream stream = new ByteArrayInputStream(input.getBytes(UTF_8));

        // when
        parser.parse(stream, listener);

        // then
        verify(listener).onEvent(eq(new ServerSentEvent(null, "Simple message")), any());
    }

    @Test
    void shouldParseMultiLineDataEvent() {

        // given
        String input = "data: First line\ndata: Second line\ndata: Third line\n\n";
        InputStream stream = new ByteArrayInputStream(input.getBytes(UTF_8));

        // when
        parser.parse(stream, listener);

        // then
        verify(listener).onEvent(eq(new ServerSentEvent(null, "First line\nSecond line\nThird line")), any());
    }

    @Test
    void shouldParseEventWithAllFields() {

        // given
        String input = "id: msg-123\nevent: custom-event\ndata: Message content\nretry: 5000\n\n";
        InputStream stream = new ByteArrayInputStream(input.getBytes(UTF_8));

        // when
        parser.parse(stream, listener);

        // then
        verify(listener).onEvent(eq(new ServerSentEvent("custom-event", "Message content")), any());
    }

    @Test
    void shouldParseMultipleEvents() {

        // given
        String input = "data: First event\n\ndata: Second event\n\ndata: Third event\n\n";
        InputStream stream = new ByteArrayInputStream(input.getBytes(UTF_8));

        // when
        parser.parse(stream, listener);

        // then
        verify(listener).onEvent(eq(new ServerSentEvent(null, "First event")), any());
        verify(listener).onEvent(eq(new ServerSentEvent(null, "Second event")), any());
        verify(listener).onEvent(eq(new ServerSentEvent(null, "Third event")), any());
    }

    @Test
    void shouldIgnoreCommentsAndEmptyLines() {

        // given
        String input = ": this is a comment\n\ndata: actual message\n\n";
        InputStream stream = new ByteArrayInputStream(input.getBytes(UTF_8));

        // when
        parser.parse(stream, listener);

        // then
        verify(listener).onEvent(eq(new ServerSentEvent(null, "actual message")), any());
    }

    @Test
    void shouldHandleStreamWithNoEvents() {

        // given
        String input = "";
        InputStream stream = new ByteArrayInputStream(input.getBytes(UTF_8));

        // when
        parser.parse(stream, listener);

        // then
        verify(listener, never()).onEvent(any());
    }

    @Test
    void shouldHandleIOException() {

        // given
        InputStream mockStream = mock(InputStream.class);
        IOException simulatedIoException = new IOException("Simulated IO exception");
        assertDoesNotThrow(
                () -> {
                    when(mockStream.read(any(byte[].class), anyInt(), anyInt())).thenThrow(simulatedIoException);
                },
                "Mock setup failed");

        // when
        parser.parse(mockStream, listener);

        // then
        verify(listener).onError(simulatedIoException);
    }

    // ---------- parse(InputStream) edge cases ----------

    @Test
    void parse_stops_emitting_after_the_listener_cancels() {
        String input = "data: first\n\ndata: second\n\ndata: third\n\n";
        List<ServerSentEvent> received = new ArrayList<>();
        ServerSentEventListener cancelling = new ServerSentEventListener() {
            @Override
            public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                received.add(event);
                context.parsingHandle().cancel();
            }

            @Override
            public void onError(Throwable throwable) {}
        };

        parser.parse(new ByteArrayInputStream(input.getBytes(UTF_8)), cancelling);

        assertThat(received).containsExactly(new ServerSentEvent(null, "first"));
    }

    @Test
    void parse_handles_cr_and_crlf_line_endings() {
        // \r\n and bare \r are both handled by BufferedReader.readLine()
        String input = "event: e\r\ndata: a\r\n\r\ndata: b\r\rdata: c\r\r";
        parser.parse(new ByteArrayInputStream(input.getBytes(UTF_8)), listener);

        verify(listener).onEvent(eq(new ServerSentEvent("e", "a")), any());
        verify(listener).onEvent(eq(new ServerSentEvent(null, "b")), any());
        verify(listener).onEvent(eq(new ServerSentEvent(null, "c")), any());
    }

    @Test
    void parse_trims_data_and_event_field_values() {
        // Note: the parser trims field values fully (not just a single leading space).
        String input = "event:   spaced-event   \ndata:   spaced value   \n\n";
        parser.parse(new ByteArrayInputStream(input.getBytes(UTF_8)), listener);

        verify(listener).onEvent(eq(new ServerSentEvent("spaced-event", "spaced value")), any());
    }

    // ---------- incremental() ----------

    private static ByteBuffer buf(String s) {
        return ByteBuffer.wrap(s.getBytes(UTF_8));
    }

    @Test
    void incremental_parses_single_event_in_one_chunk() {
        ServerSentEventParser.Incremental incremental = parser.incremental();
        assertThat(incremental.feed(buf("data: hello\n\n")))
                .containsExactly(new ServerSentEvent(null, "hello"));
        assertThat(incremental.flush()).isEmpty();
    }

    @Test
    void incremental_parses_event_split_across_chunks() {
        ServerSentEventParser.Incremental incremental = parser.incremental();
        assertThat(incremental.feed(buf("da"))).isEmpty();
        assertThat(incremental.feed(buf("ta: hel"))).isEmpty();
        assertThat(incremental.feed(buf("lo\n\n"))).containsExactly(new ServerSentEvent(null, "hello"));
    }

    @Test
    void incremental_handles_crlf_line_endings() {
        ServerSentEventParser.Incremental incremental = parser.incremental();
        assertThat(incremental.feed(buf("event: e\r\ndata: a\r\n\r\n")))
                .containsExactly(new ServerSentEvent("e", "a"));
    }

    @Test
    void incremental_parses_multiple_events() {
        ServerSentEventParser.Incremental incremental = parser.incremental();
        assertThat(incremental.feed(buf("data: one\n\ndata: two\n\ndata: three\n\n")))
                .containsExactly(
                        new ServerSentEvent(null, "one"),
                        new ServerSentEvent(null, "two"),
                        new ServerSentEvent(null, "three"));
    }

    @Test
    void incremental_joins_multiline_data() {
        ServerSentEventParser.Incremental incremental = parser.incremental();
        assertThat(incremental.feed(buf("data: a\ndata: b\ndata: c\n\n")))
                .containsExactly(new ServerSentEvent(null, "a\nb\nc"));
    }

    @Test
    void incremental_ignores_comment_id_and_retry_lines() {
        ServerSentEventParser.Incremental incremental = parser.incremental();
        assertThat(incremental.feed(buf("id: 1\nretry: 5000\n: a comment\ndata: x\n\n")))
                .containsExactly(new ServerSentEvent(null, "x"));
    }

    @Test
    void incremental_flush_emits_trailing_event_without_terminating_blank_line() {
        ServerSentEventParser.Incremental incremental = parser.incremental();
        assertThat(incremental.feed(buf("data: a\n\ndata: b\n"))).containsExactly(new ServerSentEvent(null, "a"));
        assertThat(incremental.flush()).containsExactly(new ServerSentEvent(null, "b"));
    }

    @Test
    void incremental_flush_completes_a_pending_partial_line() {
        ServerSentEventParser.Incremental incremental = parser.incremental();
        assertThat(incremental.feed(buf("data: no newline yet"))).isEmpty();
        assertThat(incremental.flush()).containsExactly(new ServerSentEvent(null, "no newline yet"));
    }

    @Test
    void incremental_flush_returns_empty_when_nothing_is_pending() {
        ServerSentEventParser.Incremental incremental = parser.incremental();
        assertThat(incremental.feed(buf("data: x\n\n"))).containsExactly(new ServerSentEvent(null, "x"));
        assertThat(incremental.flush()).isEmpty();
    }

    @Test
    void incremental_decodes_utf8_multibyte_char_split_across_chunks() {
        ServerSentEventParser.Incremental incremental = parser.incremental();
        // "é" is 0xC3 0xA9 in UTF-8; split inside the character.
        byte[] all = "data: café\n\n".getBytes(UTF_8);
        int split = indexOf(all, (byte) 0xC3) + 1;

        assertThat(incremental.feed(ByteBuffer.wrap(all, 0, split))).isEmpty();
        assertThat(incremental.feed(ByteBuffer.wrap(all, split, all.length - split)))
                .containsExactly(new ServerSentEvent(null, "café"));
    }

    private static int indexOf(byte[] arr, byte b) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == b) {
                return i;
            }
        }
        return -1;
    }
}
