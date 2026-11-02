package dev.langchain4j.mcp.client.transport.http;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.langchain4j.http.client.sse.DefaultServerSentEventParsingHandle;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Passes response lines, including empty lines, through the HTTP streaming API without interpreting their contents.
 * The transport handles JSON responses and retains its SSE field handling, including {@code id} and {@code retry},
 * which would otherwise be lost when parsing with the default SSE parser.
 * Line endings are retained separately so that non-SSE response bodies can be reconstructed without modification.
 * A line longer than {@link #MAX_LINE_FRAGMENT_BYTES} is passed on in several {@linkplain Line#partial() partial}
 * fragments, so that the transport, rather than this parser, decides how much of a line it is willing to buffer.
 */
final class McpResponseParser implements ServerSentEventParser {

    static final int MAX_LINE_FRAGMENT_BYTES = 64 * 1024;

    @Override
    public void parse(InputStream httpResponseBody, ServerSentEventListener listener) {
        var handle = new DefaultServerSentEventParsingHandle(httpResponseBody);
        var context = new ServerSentEventContext(handle);
        Incremental parser = incremental();
        try (httpResponseBody) {
            byte[] buffer = new byte[8192];
            int count;
            while (!handle.isCancelled() && (count = httpResponseBody.read(buffer)) != -1) {
                emit(parser.feed(ByteBuffer.wrap(buffer, 0, count)), listener, context);
            }
            if (!handle.isCancelled()) {
                emit(parser.flush(), listener, context);
            }
        } catch (IOException e) {
            if (!handle.isCancelled()) {
                listener.onError(e);
            }
        }
    }

    @Override
    public Incremental incremental() {
        return new LineParser();
    }

    private static void emit(
            List<ServerSentEvent> lines, ServerSentEventListener listener, ServerSentEventContext context) {
        for (ServerSentEvent line : lines) {
            if (context.parsingHandle().isCancelled()) {
                return;
            }
            listener.onEvent(line, context);
        }
    }

    static final class Line extends ServerSentEvent {

        private final String ending;
        private final boolean partial;
        private final int byteLength;

        private Line(String data, String ending, boolean partial, int byteLength) {
            super(null, data);
            this.ending = ending;
            this.partial = partial;
            this.byteLength = byteLength;
        }

        /**
         * The line terminator, or an empty string for a partial fragment or a final line without a terminator.
         */
        String ending() {
            return ending;
        }

        /**
         * Whether this is a fragment of a longer line that continues in the next {@link Line}.
         */
        boolean partial() {
            return partial;
        }

        /**
         * The length of {@link #data()} in UTF-8 bytes, excluding the line terminator.
         */
        int byteLength() {
            return byteLength;
        }
    }

    private static final class LineParser implements Incremental {

        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        private boolean pendingCarriageReturn;

        @Override
        public List<ServerSentEvent> feed(ByteBuffer bytes) {
            List<ServerSentEvent> lines = new ArrayList<>();
            while (bytes.hasRemaining()) {
                byte next = bytes.get();
                if (pendingCarriageReturn) {
                    pendingCarriageReturn = false;
                    if (next == '\n') {
                        lines.add(line("\r\n"));
                        continue;
                    }
                    lines.add(line("\r"));
                }
                if (next == '\r') {
                    pendingCarriageReturn = true;
                } else if (next == '\n') {
                    lines.add(line("\n"));
                } else {
                    // UTF-8 continuation bytes cannot contain CR or LF. Decode only complete lines so that
                    // multi-byte characters split across chunks remain intact, without rescanning buffered bytes.
                    // A long line is split before a byte that starts a character, which keeps fragments decodable;
                    // a run of continuation bytes longer than any valid character is invalid UTF-8 and split anyway.
                    if (pending.size() >= MAX_LINE_FRAGMENT_BYTES
                            && ((next & 0xC0) != 0x80 || pending.size() >= MAX_LINE_FRAGMENT_BYTES + 3)) {
                        lines.add(fragment());
                    }
                    pending.write(next);
                }
            }
            return lines;
        }

        @Override
        public List<ServerSentEvent> flush() {
            if (pendingCarriageReturn) {
                pendingCarriageReturn = false;
                return List.of(line("\r"));
            }
            return pending.size() == 0 ? List.of() : List.of(line(""));
        }

        private Line line(String ending) {
            var line = new Line(pending.toString(UTF_8), ending, false, pending.size());
            pending.reset();
            return line;
        }

        private Line fragment() {
            var line = new Line(pending.toString(UTF_8), "", true, pending.size());
            pending.reset();
            return line;
        }
    }
}
