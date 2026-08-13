package dev.langchain4j.store.embedding.chroma;

import static java.util.Collections.emptyMap;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link HttpClient} that records every request it receives and answers with a canned Chroma response,
 * so that {@link ChromaEmbeddingStore} can be tested without a running Chroma server.
 */
class CapturingHttpClient implements HttpClient {

    private final List<HttpRequest> requests = new ArrayList<>();

    List<HttpRequest> requests() {
        return requests;
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        requests.add(request);
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(emptyMap())
                .body(bodyFor(request.url()))
                .build();
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        throw new UnsupportedOperationException("SSE is not used by ChromaEmbeddingStore");
    }

    private static String bodyFor(String url) {
        if (url.endsWith("/api/v2/tenants/default")) {
            return "{\"name\":\"default\"}";
        }
        if (url.endsWith("/api/v2/tenants/default/databases/default")) {
            return "{\"name\":\"default\"}";
        }
        if (url.endsWith("/add")) {
            return "true";
        }
        if (url.endsWith("/api/v1/collections")) {
            return "{\"id\":\"collection-id\",\"name\":\"test\",\"metadata\":{}}";
        }
        if (url.contains("/collections/test")) {
            return "{\"id\":\"collection-id\",\"name\":\"test\",\"metadata\":{}}";
        }
        throw new IllegalArgumentException("Unexpected URL: " + url);
    }
}
