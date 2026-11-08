package dev.langchain4j.http.client.sse;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.unmodifiableMap;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@link HttpStreamingEvent} signalling that the server answered with an error status (not 2xx).
 * It is emitted as soon as the response headers arrive, before the response body is read,
 * so it carries the status code and the headers, but not the body.
 * <p>
 * A subscriber that only needs the status code can cancel its subscription when it receives this event.
 * The HTTP client then stops reading the response body. Otherwise, the stream fails as usual
 * with an {@link HttpException} that contains the status code and the response body.
 * <p>
 * Not every {@link HttpClient} implementation emits this event, and it is emitted only when the subscriber
 * has requested at least one item. Without it, the stream only fails with the {@link HttpException}.
 *
 * @since 1.23.0
 */
@Experimental
public final class HttpErrorResponseReceived implements HttpStreamingEvent {

    private final int statusCode;
    private final Map<String, List<String>> headers;

    public HttpErrorResponseReceived(int statusCode, Map<String, List<String>> headers) {
        ensureTrue(statusCode < 200 || statusCode > 299, "statusCode must not be a successful (2xx) status");
        this.statusCode = statusCode;
        Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        copy.putAll(ensureNotNull(headers, "headers"));
        this.headers = unmodifiableMap(copy);
    }

    public int statusCode() {
        return statusCode;
    }

    /**
     * @return the response headers. Header names are case-insensitive.
     */
    public Map<String, List<String>> headers() {
        return headers;
    }

    @Override
    public String toString() {
        return "HttpErrorResponseReceived { statusCode = " + statusCode + " }";
    }
}
