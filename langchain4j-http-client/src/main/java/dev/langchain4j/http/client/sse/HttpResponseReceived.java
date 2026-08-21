package dev.langchain4j.http.client.sse;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.util.Objects;

/**
 * {@link HttpStreamingEvent} signalling that the HTTP response head (status and headers) was received and the
 * stream opened successfully. Emitted once, before any {@link ServerSentEvent}.
 */
public class HttpResponseReceived implements HttpStreamingEvent {

    private final SuccessfulHttpResponse response;

    public HttpResponseReceived(SuccessfulHttpResponse response) {
        this.response = ensureNotNull(response, "response");
    }

    public SuccessfulHttpResponse response() {
        return response;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (HttpResponseReceived) obj;
        return Objects.equals(this.response, that.response);
    }

    @Override
    public int hashCode() {
        return Objects.hash(response);
    }

    @Override
    public String toString() {
        return "HttpResponseReceived { response = " + response + " }";
    }
}
