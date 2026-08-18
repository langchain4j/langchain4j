package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static java.util.Collections.emptyMap;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * An {@link HttpClient} that records every request it receives and answers with a canned Chroma response,
 * so that {@link ChromaEmbeddingStore} can be tested without a running Chroma server.
 *
 * <p>Created with {@link #failingLookups(int, String...)} or {@link #failingLookups(Supplier, String...)},
 * it fails every GET to one of the given paths instead, which is how a lookup of a missing or unreachable
 * tenant, database or collection is simulated.</p>
 */
class CapturingHttpClient implements HttpClient {

    private final Supplier<RuntimeException> lookupFailure;
    private final List<String> failingPaths;
    private final List<HttpRequest> requests = new ArrayList<>();

    CapturingHttpClient() {
        this(null, List.of());
    }

    private CapturingHttpClient(Supplier<RuntimeException> lookupFailure, List<String> failingPaths) {
        this.lookupFailure = lookupFailure;
        this.failingPaths = failingPaths;
    }

    static CapturingHttpClient failingLookups(int statusCode, String... failingPaths) {
        return failingLookups(() -> new HttpException(statusCode, "{\"error\":\"simulated\"}"), failingPaths);
    }

    static CapturingHttpClient failingLookups(Supplier<RuntimeException> lookupFailure, String... failingPaths) {
        return new CapturingHttpClient(lookupFailure, List.of(failingPaths));
    }

    List<HttpRequest> requests() {
        return requests;
    }

    List<HttpRequest> writeRequests() {
        return requests.stream().filter(request -> request.method() == POST).toList();
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        requests.add(request);
        if (request.method() == GET && isFailingPath(request.url())) {
            throw lookupFailure.get();
        }
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

    private boolean isFailingPath(String url) {
        return failingPaths.stream().anyMatch(url::endsWith);
    }

    private static String bodyFor(String url) {
        if (url.endsWith("/add")) {
            return "true";
        }
        if (url.endsWith("/collections") || url.contains("/collections/")) {
            return "{\"id\":\"collection-id\",\"name\":\"test\",\"metadata\":{}}";
        }
        if (url.contains("/tenants") || url.contains("/databases")) {
            return "{\"name\":\"default\"}";
        }
        throw new IllegalArgumentException("Unexpected URL: " + url);
    }
}
