package dev.langchain4j.http.client.sse;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * An advisory event exposing a non-2xx response's status and headers before its body is read.
 * A subscriber can cancel from {@code onNext} to avoid reading the error body. Otherwise, the
 * publisher continues its normal error handling and signals an {@link dev.langchain4j.exception.HttpException}
 * containing the error body. This event is not a terminal signal and contains no body.
 * <p>
 * Like other items, this event requires demand. If no demand is available when the response headers
 * arrive, a publisher may omit it and proceed directly to its normal error handling; terminal errors
 * do not require demand. Older HTTP client implementations may omit this event entirely.
 *
 * @since 1.23.0
 */
@Experimental
public final class HttpErrorResponseReceived implements HttpStreamingEvent {

    private final int statusCode;
    private final Map<String, List<String>> headers;

    public HttpErrorResponseReceived(int statusCode, Map<String, List<String>> headers) {
        this.statusCode = ensureBetween(statusCode, 100, 599, "statusCode");
        ensureTrue(statusCode < 200 || statusCode >= 300, "statusCode must not be a successful (2xx) status");
        ensureNotNull(headers, "headers");
        Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, values) -> copy.put(ensureNotNull(name, "header name"), List.copyOf(values)));
        this.headers = Collections.unmodifiableMap(copy);
    }

    public int statusCode() {
        return statusCode;
    }

    /** Returns immutable headers with case-insensitive name lookup. */
    public Map<String, List<String>> headers() {
        return headers;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != getClass()) return false;
        HttpErrorResponseReceived that = (HttpErrorResponseReceived) obj;
        return statusCode == that.statusCode && headers.equals(that.headers);
    }

    @Override
    public int hashCode() {
        // Normalize names because header equality is case-insensitive.
        int headerHash = headers.entrySet().stream()
                .mapToInt(entry -> entry.getKey().toLowerCase(Locale.ROOT).hashCode()
                        ^ entry.getValue().hashCode())
                .sum();
        return Objects.hash(statusCode, headerHash);
    }

    @Override
    public String toString() {
        return "HttpErrorResponseReceived { statusCode = " + statusCode + " }";
    }
}
