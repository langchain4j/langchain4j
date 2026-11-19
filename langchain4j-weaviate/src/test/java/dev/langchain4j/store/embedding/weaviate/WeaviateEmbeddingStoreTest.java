package dev.langchain4j.store.embedding.weaviate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import io.weaviate.client6.v1.api.collections.query.ConsistencyLevel;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class WeaviateEmbeddingStoreTest {

    private static final String TEXT_FIELD = "text";
    private static final String METADATA_FIELD = "_metadata";

    private static Map<String, Object> rootModeProperties() {
        // Mirrors what buildObject() stores in root metadata mode: text, the Boolean index flags,
        // and metadata entries flattened into the root of the object.
        Map<String, Object> properties = new HashMap<>();
        properties.put(TEXT_FIELD, "hello");
        properties.put("indexFilterable", true);
        properties.put("indexSearchable", true);
        properties.put("key", "value");
        return properties;
    }

    private static Map<String, Object> nestedModeProperties() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", "value");
        Map<String, Object> properties = new HashMap<>();
        properties.put(TEXT_FIELD, "hello");
        properties.put("indexFilterable", true);
        properties.put("indexSearchable", true);
        properties.put(METADATA_FIELD, metadata);
        return properties;
    }

    @Test
    void should_match_root_mode_when_filter_matches() {
        Filter filter = new IsEqualTo("key", "value");

        boolean matched = WeaviateEmbeddingStore.matchesFilter(rootModeProperties(), "", TEXT_FIELD, filter);

        assertThat(matched).isTrue();
    }

    @Test
    void should_not_match_root_mode_when_filter_does_not_match() {
        Filter filter = new IsEqualTo("key", "other");

        boolean matched = WeaviateEmbeddingStore.matchesFilter(rootModeProperties(), "", TEXT_FIELD, filter);

        assertThat(matched).isFalse();
    }

    @Test
    void should_match_nested_mode_when_filter_matches() {
        Filter filter = new IsEqualTo("key", "value");

        boolean matched =
                WeaviateEmbeddingStore.matchesFilter(nestedModeProperties(), METADATA_FIELD, TEXT_FIELD, filter);

        assertThat(matched).isTrue();
    }

    @Test
    void should_not_match_nested_mode_when_filter_does_not_match() {
        Filter filter = new IsEqualTo("key", "other");

        boolean matched =
                WeaviateEmbeddingStore.matchesFilter(nestedModeProperties(), METADATA_FIELD, TEXT_FIELD, filter);

        assertThat(matched).isFalse();
    }

    @Test
    void should_not_match_nested_mode_when_metadata_field_is_missing() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(TEXT_FIELD, "hello");
        Filter filter = new IsEqualTo("key", "value");

        boolean matched = WeaviateEmbeddingStore.matchesFilter(properties, METADATA_FIELD, TEXT_FIELD, filter);

        assertThat(matched).isFalse();
    }

    @Test
    void should_not_throw_when_root_properties_contain_boolean_index_flags() {
        Filter filter = new IsEqualTo("key", "value");

        // Boolean index flags are not supported Metadata value types; matchesFilter must strip them
        // before constructing Metadata, otherwise new Metadata(...) throws IllegalArgumentException.
        assertThatCode(() -> WeaviateEmbeddingStore.matchesFilter(rootModeProperties(), "", TEXT_FIELD, filter))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"ONE,ONE", "one,ONE", "QUORUM,QUORUM", "Quorum,QUORUM", "ALL,ALL", "' all ',ALL"})
    void should_parse_consistency_level(String value, ConsistencyLevel expected) {
        assertThat(WeaviateEmbeddingStore.toConsistencyLevel(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void should_default_consistency_level_to_quorum(String value) {
        assertThat(WeaviateEmbeddingStore.toConsistencyLevel(value)).isEqualTo(ConsistencyLevel.QUORUM);
    }

    @Test
    void should_fail_to_build_with_unknown_consistency_level() {
        assertThatThrownBy(() -> builder().consistencyLevel("MOST").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MOST");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "my-cluster.weaviate.cloud",
                "my-cluster.c0.europe-west3.gcp.weaviate.cloud",
                "MY-CLUSTER.WEAVIATE.NETWORK",
                "my-cluster.semi.technology"
            })
    void should_detect_weaviate_cloud_host(String host) {
        assertThat(WeaviateEmbeddingStore.isWeaviateCloud(host)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "weaviate.cloud.example.com", "weaviate", "10.0.0.1"})
    void should_not_detect_other_hosts_as_weaviate_cloud(String host) {
        assertThat(WeaviateEmbeddingStore.isWeaviateCloud(host)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void should_fail_to_build_without_scheme(String scheme) {
        assertThatThrownBy(() -> builder().scheme(scheme).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scheme");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void should_fail_to_build_without_host(String host) {
        assertThatThrownBy(() -> builder().host(host).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void should_fail_to_build_with_invalid_batch_size(int batchSize) {
        assertThatThrownBy(() -> builder().batchSize(batchSize).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void should_fail_to_build_with_invalid_batch_max_retries(int batchMaxRetries) {
        assertThatThrownBy(() -> builder().batchMaxRetries(batchMaxRetries).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchMaxRetries");
    }

    @ParameterizedTest
    @CsvSource({
        "localhost,localhost",
        "localhost:8080,localhost",
        "my-cluster.weaviate.cloud:443,my-cluster.weaviate.cloud",
        "::1,::1",
        "host:,host:",
        "host:abc,host:abc"
    })
    void should_strip_port_from_host(String host, String expected) {
        assertThat(WeaviateEmbeddingStore.hostWithoutPort(host)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "http,localhost,,80",
        "https,localhost,,443",
        "HTTPS,localhost,,443",
        "http,localhost:8080,,8080",
        "https,localhost:8443,,8443",
        "http,localhost:8080,9090,9090",
        "http,localhost,9090,9090",
        "http,::1,,80"
    })
    void should_resolve_http_port(String scheme, String host, Integer port, int expected) {
        assertThat(WeaviateEmbeddingStore.httpPort(scheme, host, port)).isEqualTo(expected);
    }

    @Test
    void should_ignore_values_that_are_not_valid_metadata_in_root_mode() {
        Map<String, Object> properties = rootModeProperties();
        properties.put("nullValue", null);
        properties.put("date", java.time.OffsetDateTime.now());
        properties.put("flag", true);
        properties.put("list", java.util.List.of("a"));
        properties.put("number", 1L);

        assertThat(WeaviateEmbeddingStore.matchesFilter(properties, "", TEXT_FIELD, new IsEqualTo("key", "value")))
                .isTrue();
        assertThat(WeaviateEmbeddingStore.matchesFilter(properties, "", TEXT_FIELD, new IsEqualTo("number", 1L)))
                .isTrue();
    }

    @Test
    void should_identify_the_integration_with_its_version() {
        assertThat(WeaviateEmbeddingStore.INTEGRATION).matches("langchain4j/\\d+\\.\\d+\\.\\d+.*");
    }

    @Test
    void should_send_the_integration_header() throws Exception {
        List<String> integrations = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            integrations.add(exchange.getRequestHeaders().getFirst(WeaviateEmbeddingStore.INTEGRATION_HEADER));
            byte[] body = "{\"version\": \"1.36.0\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            // only the HTTP endpoint is served, so building the store may fail once it reaches gRPC
            try (WeaviateEmbeddingStore ignored =
                    builder().port(server.getAddress().getPort()).build()) {
                // nothing to do
            } catch (RuntimeException e) {
                // expected
            }
        } finally {
            server.stop(0);
        }

        assertThat(integrations).isNotEmpty().allMatch(WeaviateEmbeddingStore.INTEGRATION::equals);
    }

    private static WeaviateEmbeddingStore.WeaviateEmbeddingStoreBuilder builder() {
        // nothing listens on these ports: the settings must be rejected before a connection is attempted
        return WeaviateEmbeddingStore.builder()
                .scheme("http")
                .host("localhost")
                .port(1)
                .grpcPort(2);
    }
}
