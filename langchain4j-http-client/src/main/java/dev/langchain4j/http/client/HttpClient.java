package dev.langchain4j.http.client;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Publisher;

/**
 * A client for executing HTTP requests both synchronously and asynchronously.
 * This interface is currently experimental and subject to change.
 */
public interface HttpClient {

    /**
     * Executes a given HTTP request synchronously and returns the response.
     * This method blocks until the entire response is received.
     *
     * @param request the HTTP request to be executed.
     * @return a {@link SuccessfulHttpResponse} containing the response data for successful HTTP requests (2XX status codes)
     * @throws HttpException    if the server returns a client (4XX) or server (5XX) error response
     * @throws RuntimeException if an unexpected error occurs during request execution (e.g., network issues, timeouts)
     */
    SuccessfulHttpResponse execute(HttpRequest request) throws HttpException, RuntimeException;

    /**
     * Non-blocking counterpart of {@link #execute(HttpRequest)}.
     * Returns immediately with a {@link CompletableFuture} that completes with the
     * {@link SuccessfulHttpResponse} once the full response has been received, without blocking the
     * calling thread. The future completes exceptionally with an {@link HttpException} for non-2XX
     * responses, or with the underlying error (e.g. a timeout or network failure) otherwise.
     *
     * @param request the HTTP request to be executed.
     * @return a {@link CompletableFuture} of the {@link SuccessfulHttpResponse}.
     * @since 1.18.0
     */
    default CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
        throw new AsyncNotSupportedException("executeAsync() is not implemented by " + getClass().getName());
    }

    /**
     * Executes a given HTTP request asynchronously with server-sent events (SSE) handling.
     * This method returns immediately while processing continues on a separate thread.
     * Events are processed through the provided {@link ServerSentEventListener}.
     * <p>
     * The execution flow is as follows:
     * <ol>
     *   <li>The request is initiated asynchronously</li>
     *   <li>Received SSE data is parsed using the {@link DefaultServerSentEventParser}</li>
     *   <li>Parsed events are delivered to the listener's appropriate methods</li>
     *   <li>If an error occurs, {@link ServerSentEventListener#onError(Throwable)} is called</li>
     * </ol>
     * <p>
     * If any exception is thrown from the listener's methods, the stream processing
     * will be terminated and no further events will be processed.
     *
     * @param request  the HTTP request to be executed.
     * @param listener the listener to receive parsed events and error notifications.
     */
    default void execute(HttpRequest request, ServerSentEventListener listener) {
        execute(request, new DefaultServerSentEventParser(), listener);
    }

    /**
     * Executes a given HTTP request asynchronously with server-sent events (SSE) handling.
     * This method returns immediately while processing continues on a separate thread.
     * Events are processed through the provided {@link ServerSentEventListener}.
     * <p>
     * The execution flow is as follows:
     * <ol>
     *   <li>The request is initiated asynchronously</li>
     *   <li>Received SSE data is parsed using the provided parser</li>
     *   <li>Parsed events are delivered to the listener's appropriate methods</li>
     *   <li>If an error occurs, {@link ServerSentEventListener#onError(Throwable)} is called</li>
     * </ol>
     * <p>
     * If any exception is thrown from the listener's methods, the stream processing
     * will be terminated and no further events will be processed.
     *
     * @param request  the HTTP request to be executed.
     * @param parser   the parser to process incoming server-sent events.
     * @param listener the listener to receive parsed events and error notifications.
     */
    void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener);

    /**
     * Executes a streaming HTTP request and exposes the parsed events as a cold
     * {@link Publisher} of {@link HttpStreamingEvent}s. Each {@code subscribe()} initiates a new request.
     * <p>
     * This interface gives no guarantee about thread-pinning or whether events are delivered incrementally;
     * such guarantees depend on the implementation. Consult the chosen implementation's javadoc.
     * <p>
     * Uses {@link DefaultServerSentEventParser} for SSE parsing.
     *
     * @since 1.18.0
     */
    default Publisher<HttpStreamingEvent> stream(HttpRequest request) {
        return stream(request, new DefaultServerSentEventParser());
    }

    /**
     * Like {@link #stream(HttpRequest)}, but with a caller-supplied {@link ServerSentEventParser}.
     *
     * @since 1.18.0
     */
    default Publisher<HttpStreamingEvent> stream(HttpRequest request, ServerSentEventParser parser) {
        throw new AsyncNotSupportedException("stream() is not implemented by " + getClass().getName());
    }
}
