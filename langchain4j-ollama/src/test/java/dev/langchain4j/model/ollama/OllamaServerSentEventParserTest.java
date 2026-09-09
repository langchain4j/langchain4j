package dev.langchain4j.model.ollama;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

class OllamaServerSentEventParserTest {

    private final OllamaServerSentEventParser parser = new OllamaServerSentEventParser();

    private static final String LINE_1 = "{\"message\":{\"content\":\" The\"},\"done\":false}";
    private static final String LINE_2 = "{\"message\":{\"content\":\" capital\"},\"done\":false}";
    private static final String LINE_3 = "{\"message\":{\"content\":\"\"},\"done\":true}";

    private static ByteBuffer bytes(String s) {
        return ByteBuffer.wrap(s.getBytes(UTF_8));
    }

    private static ServerSentEvent event(String data) {
        return new ServerSentEvent(null, data);
    }

    // ---------- parse(InputStream, listener) ----------

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Parse {

        @Mock
        private ServerSentEventListener listener;

        @AfterEach
        void afterEach() {
            verifyNoMoreInteractions(listener);
        }

        private void parse(String input) {
            parser.parse(new ByteArrayInputStream(input.getBytes(UTF_8)), listener);
        }

        @Test
        void emits_one_event_per_ndjson_line_with_null_event_and_raw_line_as_data() {
            parse(LINE_1 + "\n" + LINE_2 + "\n" + LINE_3 + "\n");

            verify(listener).onEvent(eq(event(LINE_1)), any());
            verify(listener).onEvent(eq(event(LINE_2)), any());
            verify(listener).onEvent(eq(event(LINE_3)), any());
        }

        @Test
        void emits_last_line_even_without_trailing_newline() {
            parse(LINE_1 + "\n" + LINE_2);

            verify(listener).onEvent(eq(event(LINE_1)), any());
            verify(listener).onEvent(eq(event(LINE_2)), any());
        }

        @Test
        void handles_crlf_line_endings() {
            parse(LINE_1 + "\r\n" + LINE_2 + "\r\n");

            verify(listener).onEvent(eq(event(LINE_1)), any());
            verify(listener).onEvent(eq(event(LINE_2)), any());
        }

        @Test
        void empty_input_emits_no_events() {
            parse("");

            verify(listener, never()).onEvent(any(), any());
        }

        @Test
        void skips_blank_lines() {
            // A blank line must NOT produce an empty-data event (consistent with the incremental parser).
            // @AfterEach's verifyNoMoreInteractions ensures no extra event was emitted.
            parse(LINE_1 + "\n\n" + LINE_2 + "\n");

            verify(listener).onEvent(eq(event(LINE_1)), any());
            verify(listener).onEvent(eq(event(LINE_2)), any());
        }

        @Test
        void reports_io_exception_via_on_error() throws Exception {
            InputStream failing = mock(InputStream.class);
            IOException failure = new IOException("boom");
            when(failing.read(any(byte[].class), anyInt(), anyInt())).thenThrow(failure);

            assertThatCode(() -> parser.parse(failing, listener)).doesNotThrowAnyException();

            verify(listener).onError(failure);
        }

        @Test
        void stops_emitting_after_the_listener_cancels() {
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

            parser.parse(new ByteArrayInputStream((LINE_1 + "\n" + LINE_2 + "\n").getBytes(UTF_8)), cancelling);

            assertThat(received).containsExactly(event(LINE_1));
        }
    }

    // ---------- incremental() ----------

    @Nested
    class Incremental {

        private final ServerSentEventParser.Incremental incremental = parser.incremental();

        @Test
        void parses_single_line_in_one_chunk() {
            assertThat(incremental.feed(bytes(LINE_1 + "\n"))).containsExactly(event(LINE_1));
            assertThat(incremental.flush()).isEmpty();
        }

        @Test
        void parses_a_line_split_across_several_chunks() {
            assertThat(incremental.feed(bytes("{\"content\""))).isEmpty();
            assertThat(incremental.feed(bytes(":\" The\"}"))).isEmpty();
            assertThat(incremental.feed(bytes("\n"))).containsExactly(event("{\"content\":\" The\"}"));
        }

        @Test
        void parses_multiple_lines_in_one_chunk() {
            assertThat(incremental.feed(bytes(LINE_1 + "\n" + LINE_2 + "\n" + LINE_3 + "\n")))
                    .containsExactly(event(LINE_1), event(LINE_2), event(LINE_3));
        }

        @Test
        void handles_crlf_line_endings() {
            assertThat(incremental.feed(bytes(LINE_1 + "\r\n" + LINE_2 + "\r\n")))
                    .containsExactly(event(LINE_1), event(LINE_2));
        }

        @Test
        void skips_blank_lines() {
            assertThat(incremental.feed(bytes(LINE_1 + "\n\n" + LINE_2 + "\n")))
                    .containsExactly(event(LINE_1), event(LINE_2));
        }

        @Test
        void flush_emits_a_trailing_line_that_had_no_newline() {
            assertThat(incremental.feed(bytes(LINE_1 + "\n" + LINE_2))).containsExactly(event(LINE_1));
            assertThat(incremental.flush()).containsExactly(event(LINE_2));
        }

        @Test
        void flush_returns_empty_when_nothing_is_pending() {
            assertThat(incremental.feed(bytes(LINE_1 + "\n"))).containsExactly(event(LINE_1));
            assertThat(incremental.flush()).isEmpty();
        }

        @Test
        void decodes_utf8_multibyte_char_split_across_chunks() {
            // "é" is 0xC3 0xA9 in UTF-8; split it across two feeds inside a line.
            byte[] line = ("{\"content\":\"café\"}\n").getBytes(UTF_8);
            int splitInsideChar = indexOf(line, (byte) 0xC3) + 1; // between the two bytes of 'é'

            assertThat(incremental.feed(ByteBuffer.wrap(line, 0, splitInsideChar))).isEmpty();
            assertThat(incremental.feed(ByteBuffer.wrap(line, splitInsideChar, line.length - splitInsideChar)))
                    .containsExactly(event("{\"content\":\"café\"}"));
        }

        private int indexOf(byte[] arr, byte b) {
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] == b) {
                    return i;
                }
            }
            return -1;
        }
    }
}
