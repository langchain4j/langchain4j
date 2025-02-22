package dev.langchain4j.http.client.sse;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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
        verify(listener).onEvent(new ServerSentEvent(null, "Simple message"));
    }

    @Test
    void shouldParseMultiLineDataEvent() {

        // given
        String input = "data: First line\ndata: Second line\ndata: Third line\n\n";
        InputStream stream = new ByteArrayInputStream(input.getBytes(UTF_8));

        // when
        parser.parse(stream, listener);

        // then
        verify(listener).onEvent(new ServerSentEvent(null, "First line\nSecond line\nThird line"));
    }

    @Test
    void shouldParseEventWithAllFields() {

        // given
        String input = "id: msg-123\nevent: custom-event\ndata: Message content\nretry: 5000\n\n";
        InputStream stream = new ByteArrayInputStream(input.getBytes(UTF_8));

        // when
        parser.parse(stream, listener);

        // then
        verify(listener).onEvent(new ServerSentEvent("custom-event", "Message content"));
    }

    @Test
    void shouldParseMultipleEvents() {

        // given
        String input = "data: First event\n\ndata: Second event\n\ndata: Third event\n\n";
        InputStream stream = new ByteArrayInputStream(input.getBytes(UTF_8));

        // when
        parser.parse(stream, listener);

        // then
        verify(listener).onEvent(new ServerSentEvent(null, "First event"));
        verify(listener).onEvent(new ServerSentEvent(null, "Second event"));
        verify(listener).onEvent(new ServerSentEvent(null, "Third event"));
    }

    @Test
    void shouldIgnoreCommentsAndEmptyLines() {

        // given
        String input = ": this is a comment\n\ndata: actual message\n\n";
        InputStream stream = new ByteArrayInputStream(input.getBytes(UTF_8));

        // when
        parser.parse(stream, listener);

        // then
        verify(listener).onEvent(new ServerSentEvent(null, "actual message"));
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
        Assertions.assertDoesNotThrow(
                () -> {
                    when(mockStream.read(any(byte[].class), anyInt(), anyInt())).thenThrow(simulatedIoException);
                },
                "Mock setup failed");

        // when
        parser.parse(mockStream, listener);

        // then
        verify(listener).onError(simulatedIoException);
    }
}
