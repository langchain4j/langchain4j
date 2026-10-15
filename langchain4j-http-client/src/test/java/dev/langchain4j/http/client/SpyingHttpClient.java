package dev.langchain4j.http.client;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Internal
public class SpyingHttpClient implements HttpClient {

    private final HttpClientBuilder delegateBuilder;
    private HttpClient delegate;
    private final List<HttpRequest> requests = Collections.synchronizedList(new ArrayList<>());
    private final List<SuccessfulHttpResponse> responses = Collections.synchronizedList(new ArrayList<>());
    private final List<String> sseEvents = Collections.synchronizedList(new ArrayList<>());

    /**
     * Timeouts configured via {@link MockHttpClientBuilder} are not applied to an already built delegate,
     * use {@link #SpyingHttpClient(HttpClientBuilder)} for that.
     */
    public SpyingHttpClient(HttpClient delegate) {
        this.delegateBuilder = null;
        this.delegate = ensureNotNull(delegate, "delegate");
    }

    /**
     * The delegate is built from the given builder on the first request,
     * so that timeouts configured via {@link MockHttpClientBuilder} are applied to it.
     */
    public SpyingHttpClient(HttpClientBuilder delegateBuilder) {
        this.delegateBuilder = ensureNotNull(delegateBuilder, "delegateBuilder");
    }

    HttpClientBuilder delegateBuilder() {
        return delegateBuilder;
    }

    private synchronized HttpClient delegate() {
        if (delegate == null) {
            delegate = delegateBuilder.build();
        }
        return delegate;
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

    public List<SuccessfulHttpResponse> responses() {
        return responses;
    }

    public SuccessfulHttpResponse response() {
        if (responses.size() != 1) {
            throw new IllegalStateException("Expected 1 response, but got: " + responses.size());
        }
        return responses.get(0);
    }

    public List<String> sseEvents() {
        return sseEvents;
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        requests.add(request);
        SuccessfulHttpResponse response = delegate().execute(request);
        responses.add(response);
        return response;
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        requests.add(request);
        delegate().execute(request, parser, new ServerSentEventListener() {
            @Override
            public void onOpen(SuccessfulHttpResponse response) {
                responses.add(response);
                listener.onOpen(response);
            }

            @Override
            public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                sseEvents.add(event.data());
                listener.onEvent(event, context);
            }

            @Override
            public void onError(Throwable throwable) {
                listener.onError(throwable);
            }

            @Override
            public void onClose() {
                listener.onClose();
            }
        });
    }
}
