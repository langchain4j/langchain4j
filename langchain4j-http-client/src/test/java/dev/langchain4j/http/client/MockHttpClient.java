package dev.langchain4j.http.client;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import dev.langchain4j.Internal;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

@Internal
public class MockHttpClient implements HttpClient {

    private final List<HttpRequest> requests = Collections.synchronizedList(new ArrayList<>());
    private final SuccessfulHttpResponse response;
    private final List<ServerSentEvent> events;

    public MockHttpClient() {
        this.response = null;
        this.events = List.of();
    }

    public MockHttpClient(SuccessfulHttpResponse response) {
        this.response = ensureNotNull(response, "response");
        this.events = List.of();
    }

    public MockHttpClient(List<ServerSentEvent> events) {
        this.response = null;
        this.events = ensureNotEmpty(events, "events");
    }

    public List<HttpRequest> requests() {
        return requests;
    }

    public HttpRequest request() {
        if (requests.size() != 1) {
            throw new IllegalStateException("Expected 1 request, but got: " + requests.size());
        }
        return requests.get(0);
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        requests.add(request);
        return response;
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        requests.add(request);

        listener.onOpen(response);
        events.forEach(listener::onEvent);
        listener.onClose();
    }

    public static MockHttpClient thatAlwaysResponds(SuccessfulHttpResponse response) {
        return new MockHttpClient(response);
    }

    public static MockHttpClient thatAlwaysResponds(List<ServerSentEvent> events) {
        return new MockHttpClient(events);
    }
}
