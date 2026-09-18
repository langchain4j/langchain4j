package dev.langchain4j.store.embedding.chroma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChromaEmbeddingStoreErrorHandlingTest {

    private static final String TENANT_PATH = "/api/v2/tenants/default";
    private static final String DATABASE_PATH = "/api/v2/tenants/default/databases/default";
    private static final String COLLECTION_PATH = "/api/v2/tenants/default/databases/default/collections/test";

    private static final String CREATE_TENANT_URL = "http://localhost:8000/api/v2/tenants";
    private static final String CREATE_DATABASE_URL = "http://localhost:8000/api/v2/tenants/default/databases";
    private static final String CREATE_COLLECTION_URL =
            "http://localhost:8000/api/v2/tenants/default/databases/default/collections";

    static Stream<Arguments> failing_lookups() {
        return Stream.of(
                Arguments.of(TENANT_PATH, 400),
                Arguments.of(TENANT_PATH, 401),
                Arguments.of(TENANT_PATH, 500),
                Arguments.of(DATABASE_PATH, 400),
                Arguments.of(DATABASE_PATH, 401),
                Arguments.of(DATABASE_PATH, 500),
                Arguments.of(COLLECTION_PATH, 401),
                Arguments.of(COLLECTION_PATH, 500));
    }

    @ParameterizedTest
    @MethodSource("failing_lookups")
    void should_propagate_lookup_failure_instead_of_treating_it_as_absent(String failingPath, int statusCode) {
        CapturingHttpClient httpClient = CapturingHttpClient.failingLookups(statusCode, failingPath);

        assertThatThrownBy(() -> store(httpClient))
                .isInstanceOf(RuntimeException.class)
                .cause()
                .asInstanceOf(type(HttpException.class))
                .satisfies(cause -> assertThat(cause.statusCode()).isEqualTo(statusCode));

        assertThat(httpClient.writeRequests()).isEmpty();
    }

    @Test
    void should_propagate_timeout_from_tenant_lookup() {
        CapturingHttpClient httpClient = CapturingHttpClient.failingLookups(
                () -> new TimeoutException(new SocketTimeoutException("timed out")), TENANT_PATH);

        assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> store(httpClient));

        assertThat(httpClient.writeRequests()).isEmpty();
    }

    /**
     * Chroma 1.0.0 and later report a missing collection as 404, Chroma 0.5.16 to 0.6.3 as 400 InvalidCollection.
     * Both have to be understood as "create it", otherwise the store stops working against the older versions.
     */
    @ParameterizedTest
    @ValueSource(ints = {404, 400})
    void should_create_only_the_collection_when_tenant_and_database_exist(int missingCollectionStatusCode) {
        CapturingHttpClient httpClient =
                CapturingHttpClient.failingLookups(missingCollectionStatusCode, COLLECTION_PATH);

        store(httpClient);

        assertThat(httpClient.writeRequests()).extracting(HttpRequest::url).containsExactly(CREATE_COLLECTION_URL);
    }

    @Test
    void should_create_database_and_collection_when_only_the_tenant_exists() {
        CapturingHttpClient httpClient = CapturingHttpClient.failingLookups(404, DATABASE_PATH, COLLECTION_PATH);

        store(httpClient);

        assertThat(httpClient.writeRequests())
                .extracting(HttpRequest::url)
                .containsExactly(CREATE_DATABASE_URL, CREATE_COLLECTION_URL);
    }

    @Test
    void should_create_tenant_database_and_collection_when_none_of_them_exist() {
        CapturingHttpClient httpClient =
                CapturingHttpClient.failingLookups(404, TENANT_PATH, DATABASE_PATH, COLLECTION_PATH);

        store(httpClient);

        assertThat(httpClient.writeRequests())
                .extracting(HttpRequest::url)
                .containsExactly(CREATE_TENANT_URL, CREATE_DATABASE_URL, CREATE_COLLECTION_URL);
    }

    private static ChromaEmbeddingStore store(HttpClient httpClient) {
        return ChromaEmbeddingStore.builder()
                .baseUrl("http://localhost:8000")
                .apiVersion(ChromaApiVersion.V2)
                .collectionName("test")
                .httpClientBuilder(new TestHttpClientBuilder(httpClient))
                .build();
    }
}
