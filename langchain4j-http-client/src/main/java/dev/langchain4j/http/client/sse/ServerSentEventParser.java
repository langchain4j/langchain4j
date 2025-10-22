package dev.langchain4j.http.client.sse;

import java.io.InputStream;

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
     * For each complete event found in the stream,
     * {@link ServerSentEventListener#onEvent(ServerSentEvent, ServerSentEventContext)} is called.
     * If any parsing or processing error occurs, {@link ServerSentEventListener#onError(Throwable)}
     * is called and parsing may terminate.
     *
     * @param parseRequest The request to parse server-sent events
     * @since 1.8.0
     */
    default void parse(ServerSentEventParseRequest parseRequest) {
        parse(parseRequest.inputStream(), parseRequest.listener());
    }

    /**
     * NOTE: This is an outdated method. If you want to use stream cancellation feature,
     * implement and use {@link #parse(ServerSentEventParseRequest)} instead.
     * <br>
     * Parses an input stream containing server-sent events and notifies the listener of parsed events.
     * This method blocks until the input stream is exhausted or an error occurs.
     * <p>
     * For each complete event found in the stream, {@link ServerSentEventListener#onEvent(ServerSentEvent)}
     * is called. If any parsing or processing error occurs, {@link ServerSentEventListener#onError(Throwable)}
     * is called and parsing may terminate.
     *
     * @param httpResponseBody the input stream containing SSE data to parse
     * @param listener         the listener to receive parsed events or error notifications
     * @see #parse(ServerSentEventParseRequest)
     */
    default void parse(InputStream httpResponseBody, ServerSentEventListener listener) {}
}
