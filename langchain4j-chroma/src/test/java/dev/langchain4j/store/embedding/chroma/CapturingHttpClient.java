package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * An {@link HttpClient} that records every request it receives and answers with a canned Chroma response,
 * so that {@link ChromaEmbeddingStore} can be tested without a running Chroma server.
 *
 * <p>Created with {@link #failingLookups(int, String...)} or {@link #failingLookups(Supplier, String...)},
 * it fails every GET to one of the given paths instead, which is how a lookup of a missing or unreachable
 * tenant, database or collection is simulated.</p>
 */
class CapturingHttpClient implements HttpClient {

    private static final String DEFAULT_COLLECTION =
            "{\"id\":\"collection-id\",\"name\":\"test\",\"metadata\":{\"hnsw:space\":\"cosine\"}}";

    private final Supplier<RuntimeException> lookupFailure;
    private final List<String> failingPaths;
    private final List<HttpRequest> requests = new ArrayList<>();
    private String collectionBody = DEFAULT_COLLECTION;
    private List<Double> distances = List.of(0.0);

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

    /**
     * Answers the "get collection" call with the given JSON, to simulate a collection that already exists.
     */
    CapturingHttpClient withCollection(String collectionBody) {
        this.collectionBody = collectionBody;
        return this;
    }

    /**
     * Answers the "query collection" call with one match per given distance.
     */
    CapturingHttpClient withDistances(Double... distances) {
        this.distances = List.of(distances);
        return this;
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

    private String bodyFor(String url) {
        if (url.endsWith("/add")) {
            return "true";
        }
        if (url.endsWith("/query")) {
            return queryResponse();
        }
        if (url.endsWith("/collections")) {
            return DEFAULT_COLLECTION;
        }
        if (url.contains("/collections/")) {
            return collectionBody;
        }
        if (url.contains("/tenants") || url.contains("/databases")) {
            return "{\"name\":\"default\"}";
        }
        throw new IllegalArgumentException("Unexpected URL: " + url);
    }

    private String queryResponse() {
        String ids = join(i -> "\"id" + i + "\"");
        String embeddings = join(i -> "[1.0,2.0,3.0]");
        String documents = join(i -> "\"document" + i + "\"");
        String metadatas = join(i -> "{}");
        String distanceValues = join(i -> String.valueOf(distances.get(i)));
        return "{\"ids\":[[" + ids + "]],\"embeddings\":[[" + embeddings + "]],\"documents\":[[" + documents
                + "]],\"metadatas\":[[" + metadatas + "]],\"distances\":[[" + distanceValues + "]]}";
    }

    private String join(IntFunction<String> element) {
        return IntStream.range(0, distances.size()).mapToObj(element).collect(joining(","));
    }
}
