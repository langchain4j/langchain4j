package dev.langchain4j.http.client;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Internal
public class SpyingHttpClient implements HttpClient {

    private final HttpClient delegate;
    private final List<HttpRequest> requests = Collections.synchronizedList(new ArrayList<>());

    public SpyingHttpClient(HttpClient delegate) {
        this.delegate = ensureNotNull(delegate, "delegate");
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
        return delegate.execute(request);
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        requests.add(request);
        delegate.execute(request, parser, listener);
    }
}
