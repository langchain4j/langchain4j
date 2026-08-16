package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChromaEmbeddingStoreErrorHandlingTest {

    private static final String TENANT_PATH = "/api/v2/tenants/default";
    private static final String DATABASE_PATH = "/api/v2/tenants/default/databases/default";
    private static final String COLLECTION_PATH = "/api/v2/tenants/default/databases/default/collections/test";

    static Stream<Arguments> failing_lookups() {
        return Stream.of(
                Arguments.of(TENANT_PATH, 401),
                Arguments.of(TENANT_PATH, 500),
                Arguments.of(DATABASE_PATH, 401),
                Arguments.of(DATABASE_PATH, 500),
                Arguments.of(COLLECTION_PATH, 401),
                Arguments.of(COLLECTION_PATH, 500));
    }

    @ParameterizedTest
    @MethodSource("failing_lookups")
    void should_propagate_lookup_failure_instead_of_treating_it_as_absent(String failingPath, int statusCode) {
        FailingHttpClient httpClient = new FailingHttpClient(statusCode, failingPath);

        assertThatThrownBy(() -> store(httpClient))
                .isInstanceOf(RuntimeException.class)
                .cause()
                .asInstanceOf(type(HttpException.class))
                .satisfies(cause -> assertThat(cause.statusCode()).isEqualTo(statusCode));

        assertThat(httpClient.writeRequests()).isEmpty();
    }

    @Test
    void should_propagate_timeout_from_tenant_lookup() {
        FailingHttpClient httpClient =
                new FailingHttpClient(() -> new TimeoutException(new SocketTimeoutException("timed out")), TENANT_PATH);

        assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> store(httpClient));

        assertThat(httpClient.writeRequests()).isEmpty();
    }

    @Test
    void should_create_only_the_collection_when_tenant_and_database_exist() {
        FailingHttpClient httpClient = new FailingHttpClient(404, COLLECTION_PATH);

        store(httpClient);

        assertThat(httpClient.writeRequests())
                .extracting(HttpRequest::url)
                .containsExactly("http://localhost:8000/api/v2/tenants/default/databases/default/collections");
    }

    @Test
    void should_create_database_and_collection_when_only_the_tenant_exists() {
        FailingHttpClient httpClient = new FailingHttpClient(404, DATABASE_PATH, COLLECTION_PATH);

        store(httpClient);

        assertThat(httpClient.writeRequests())
                .extracting(HttpRequest::url)
                .containsExactly(
                        "http://localhost:8000/api/v2/tenants/default/databases",
                        "http://localhost:8000/api/v2/tenants/default/databases/default/collections");
    }

    @Test
    void should_create_tenant_database_and_collection_when_none_of_them_exist() {
        FailingHttpClient httpClient = new FailingHttpClient(404, TENANT_PATH, DATABASE_PATH, COLLECTION_PATH);

        store(httpClient);

        assertThat(httpClient.writeRequests())
                .extracting(HttpRequest::url)
                .containsExactly(
                        "http://localhost:8000/api/v2/tenants",
                        "http://localhost:8000/api/v2/tenants/default/databases",
                        "http://localhost:8000/api/v2/tenants/default/databases/default/collections");
    }

    private static ChromaEmbeddingStore store(HttpClient httpClient) {
        return ChromaEmbeddingStore.builder()
                .baseUrl("http://localhost:8000")
                .apiVersion(ChromaApiVersion.V2)
                .collectionName("test")
                .httpClientBuilder(new TestHttpClientBuilder(httpClient))
                .build();
    }

    private static class FailingHttpClient implements HttpClient {

        private final Supplier<RuntimeException> failure;
        private final List<String> failingPaths;
        private final List<HttpRequest> writeRequests = new ArrayList<>();

        FailingHttpClient(int statusCode, String... failingPaths) {
            this(() -> new HttpException(statusCode, "{\"error\":\"simulated\"}"), failingPaths);
        }

        FailingHttpClient(Supplier<RuntimeException> failure, String... failingPaths) {
            this.failure = failure;
            this.failingPaths = List.of(failingPaths);
        }

        List<HttpRequest> writeRequests() {
            return writeRequests;
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            if (request.method() == POST) {
                writeRequests.add(request);
                return response(bodyFor(request.url()));
            }
            if (failingPaths.stream().anyMatch(path -> request.url().endsWith(path))) {
                throw failure.get();
            }
            return response(bodyFor(request.url()));
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new UnsupportedOperationException("SSE is not used by ChromaEmbeddingStore");
        }

        private static SuccessfulHttpResponse response(String body) {
            return SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .headers(emptyMap())
                    .body(body)
                    .build();
        }

        private static String bodyFor(String url) {
            if (url.endsWith("/collections") || url.contains("/collections/")) {
                return "{\"id\":\"collection-id\",\"name\":\"test\",\"metadata\":{}}";
            }
            return "{\"name\":\"default\"}";
        }
    }
}
