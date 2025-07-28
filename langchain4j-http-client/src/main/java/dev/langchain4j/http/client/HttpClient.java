package dev.langchain4j.http.client;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

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
     * Executes the given HTTP request synchronously with retry support.
     * <p>
     * This method wraps the {@link #execute(HttpRequest)} method and attempts to re-execute the request
     * if it fails due to a {@link HttpException} (for 4XX/5XX responses) or other {@link RuntimeException}
     * (such as network issues or timeouts).
     * <p>
     * Retries are performed immediately, with no delay between attempts. If all attempts fail, the last
     * encountered exception is thrown.
     * <p>
     * Example usage:
     * <pre>{@code
     * HttpRequest request = ...;
     * SuccessfulHttpResponse response = httpClient.executeWithRetry(request, 3);
     * }</pre>
     *
     * @param request the HTTP request to be executed.
     * @param maxRetries the maximum number of retry attempts (excluding the initial attempt).
     *                   For example, maxRetries = 2 means up to 3 total attempts (1 original + 2 retries).
     * @return a {@link SuccessfulHttpResponse} if the request eventually succeeds.
     * @throws HttpException    if the request fails after all retry attempts due to a 4XX/5XX response.
     * @throws RuntimeException if the request fails after all retry attempts due to unexpected runtime issues.
     * @throws IllegalArgumentException if {@code maxRetries} is negative.
     */
    default SuccessfulHttpResponse executeWithRetry(HttpRequest request, int maxRetries)
            throws HttpException, RuntimeException {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }

        int attempt = 0;
        while (true) {
            try {
                return execute(request);
            } catch (RuntimeException ex) {
                if (attempt++ >= maxRetries) {
                    throw ex; // rethrow last exception after all retries
                }
            }
        }
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
}
