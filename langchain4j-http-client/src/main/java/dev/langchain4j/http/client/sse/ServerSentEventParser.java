package dev.langchain4j.http.client.sse;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Parses server-sent events (SSE) from an {@link InputStream},
 * constructs {@link ServerSentEvent} objects,
 * and delivers them to the provided {@link ServerSentEventListener}.
 */
public interface ServerSentEventParser {

    /**
     * Parses an input stream containing server-sent events and notifies the listener of parsed events.
     * This method blocks until the input stream is exhausted or an error occurs.
     * <p>
     * For each complete event found in the stream, {@link ServerSentEventListener#onEvent(ServerSentEvent)}
     * is called. If any parsing or processing error occurs, {@link ServerSentEventListener#onError(Throwable)}
     * is called and parsing may terminate.
     *
     * @param httpResponseBody the input stream containing SSE data to parse
     * @param listener         the listener to receive parsed events or error notifications
     */
    void parse(InputStream httpResponseBody, ServerSentEventListener listener);

    /**
     * Returns a fresh {@link Incremental} parser instance backed by the same parsing logic
     * as {@link #parse(InputStream, ServerSentEventListener)}, but driven by byte chunks
     * instead of a blocking {@link InputStream}. Used by the publisher-based HTTP client API
     * to avoid pinning a thread per active stream.
     * <p>
     * Default implementation throws {@link UnsupportedOperationException}; subclasses that want
     * to support the publisher path must override this method. Existing parsers that only
     * support {@link #parse(InputStream, ServerSentEventListener)} continue to work unchanged.
     *
     * @since 1.15.0
     */
    default Incremental incremental() {
        throw new UnsupportedOperationException(
                "Incremental parsing not supported by " + getClass().getName()
                        + ". Override ServerSentEventParser.incremental() to enable the publisher-based HTTP API.");
    }

    /**
     * Stateful, non-blocking SSE parser. Bytes are pushed in via {@link #feed(ByteBuffer)} as
     * they arrive on the wire; complete events are returned synchronously. Any partial trailing
     * line/event is buffered internally and joined with subsequent feeds.
     * <p>
     * Each call to {@link ServerSentEventParser#incremental()} returns a fresh instance — do not
     * share between concurrent streams.
     */
    interface Incremental {

        /**
         * Feeds a chunk of bytes; returns any events that became complete with this chunk.
         * Bytes belonging to an incomplete trailing line/event are retained internally for the
         * next call. Implementations must handle UTF-8 multi-byte sequences split across chunks.
         */
        List<ServerSentEvent> feed(ByteBuffer bytes);

        /**
         * Called when the upstream stream completes normally. Returns any final event still
         * buffered (e.g. an event whose terminating blank line was never received because the
         * stream ended). Subsequent calls to {@link #feed} after {@code flush()} are undefined.
         */
        List<ServerSentEvent> flush();
    }
}
