package dev.langchain4j.store.embedding.weaviate;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore.WeaviateEmbeddingStoreBuilder;
import io.weaviate.client6.v1.api.WeaviateClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.weaviate.WeaviateContainer;

/**
 * Covers the behavior of {@link WeaviateEmbeddingStore} that is specific to Weaviate
 * and is not exercised by the common embedding store tests.
 */
@Testcontainers
class LocalWeaviateEmbeddingStoreBehaviorIT {

    private static final int DIMENSION = 8;
    private static final int METRICS_PORT = 2112;

    @Container
    static WeaviateContainer weaviate = new WeaviateContainer("semitechnologies/weaviate:latest")
            .withEnv("QUERY_DEFAULTS_LIMIT", "25")
            .withEnv("DEFAULT_VECTORIZER_MODULE", "none")
            .withEnv("CLUSTER_HOSTNAME", "node1")
            .withEnv("PROMETHEUS_MONITORING_ENABLED", "true")
            .withExposedPorts(8080, 50051, METRICS_PORT);

    static WeaviateClient client;

    private final Random random = new Random(42);
    private final String objectClass = "Test" + randomUUID().replace("-", "");
    private final List<WeaviateEmbeddingStore> stores = new ArrayList<>();

    @BeforeAll
    static void beforeAll() {
        client = WeaviateClient.connectToLocal(local -> local.host(weaviate.getHost())
                .port(weaviate.getFirstMappedPort())
                .grpcPort(weaviate.getMappedPort(50051)));
    }

    @AfterAll
    static void afterAll() throws Exception {
        client.close();
    }

    @AfterEach
    void afterEach() {
        stores.forEach(WeaviateEmbeddingStore::close);
    }

    private WeaviateEmbeddingStoreBuilder builder() {
        return WeaviateEmbeddingStore.builder()
                .scheme("http")
                .host(weaviate.getHost())
                .port(weaviate.getFirstMappedPort())
                .grpcPort(weaviate.getMappedPort(50051))
                .objectClass(objectClass);
    }

    private WeaviateEmbeddingStore store(WeaviateEmbeddingStoreBuilder builder) {
        WeaviateEmbeddingStore store = builder.build();
        stores.add(store);
        return store;
    }

    private Embedding randomEmbedding(int dimension) {
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = random.nextFloat() + 0.01f;
        }
        return Embedding.from(vector);
    }

    private List<Embedding> randomEmbeddings(int count, int dimension) {
        List<Embedding> embeddings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            embeddings.add(randomEmbedding(dimension));
        }
        return embeddings;
    }

    private long count() {
        return client.collections.use(objectClass).size();
    }

    private List<EmbeddingMatch<TextSegment>> search(WeaviateEmbeddingStore store, Embedding embedding) {
        return store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(10)
                        .build())
                .matches();
    }

    private void createCollection(String vectorSettings) throws IOException, InterruptedException {
        String schema = """
                {"class": "%s", %s, "properties": [{"name": "text", "dataType": ["text"]}]}
                """.formatted(objectClass, vectorSettings);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + weaviate.getHost() + ":" + weaviate.getFirstMappedPort() + "/v1/schema"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(schema))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    // collection does not exist yet

    @Test
    void should_return_no_matches_when_collection_does_not_exist() {
        WeaviateEmbeddingStore store = store(builder());

        assertThat(search(store, randomEmbedding(DIMENSION))).isEmpty();
    }

    @Test
    void should_do_nothing_when_removing_from_collection_that_does_not_exist() {
        WeaviateEmbeddingStore store = store(builder());

        assertThatCode(() -> {
                    store.remove(randomUUID());
                    store.removeAll(List.of(randomUUID()));
                    store.removeAll();
                    store.removeAll(metadataKey("type").isEqualTo("a"));
                })
                .doesNotThrowAnyException();
    }

    // removal beyond one page of objects

    @Test
    void should_remove_all_objects_beyond_one_page() {
        WeaviateEmbeddingStore store = store(builder());
        store.addAll(randomEmbeddings(1200, DIMENSION));
        assertThat(count()).isEqualTo(1200);

        store.removeAll();

        assertThat(count()).isZero();
    }

    @Test
    void should_remove_all_by_filter_beyond_one_page() {
        WeaviateEmbeddingStore store = store(builder().metadataKeys(List.of("type")));
        List<Embedding> embeddings = randomEmbeddings(1200, DIMENSION);
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            segments.add(TextSegment.from("text " + i, Metadata.from("type", i % 3 == 0 ? "a" : "b")));
        }
        store.addAll(embeddings, segments);
        assertThat(count()).isEqualTo(1200);

        store.removeAll(metadataKey("type").isEqualTo("a"));

        assertThat(count()).isEqualTo(800);
        assertThat(search(store, embeddings.get(0)))
                .isNotEmpty()
                .allSatisfy(match -> assertThat(match.embedded().metadata().getString("type"))
                        .isEqualTo("b"));
    }

    @Test
    void should_remove_all_by_ids_beyond_one_page() {
        WeaviateEmbeddingStore store = store(builder());
        List<String> ids = store.addAll(randomEmbeddings(1200, DIMENSION));

        store.removeAll(ids.subList(0, 1100));

        assertThat(count()).isEqualTo(100);
    }

    // batching

    @ParameterizedTest
    @ValueSource(ints = {1, 7, 100, 5000})
    void should_add_embeddings_with_batch_size(int batchSize) {
        WeaviateEmbeddingStore store = store(builder().batchSize(batchSize));
        List<Embedding> embeddings = randomEmbeddings(250, DIMENSION);

        List<String> ids = store.addAll(embeddings);

        assertThat(ids).hasSize(250).doesNotHaveDuplicates();
        assertThat(count()).isEqualTo(250);
        EmbeddingMatch<TextSegment> match = search(store, embeddings.get(249)).get(0);
        assertThat(match.embeddingId()).isEqualTo(ids.get(249));
        assertThat(match.embedding()).isEqualTo(embeddings.get(249));
    }

    @Test
    void should_add_embeddings_with_server_side_batching() {
        WeaviateEmbeddingStore store = store(builder()
                .serverSideBatching(true)
                .batchSize(50)
                .batchMaxRetries(2)
                .metadataKeys(List.of("type")));
        List<Embedding> embeddings = randomEmbeddings(1200, DIMENSION);
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            segments.add(TextSegment.from("text " + i, Metadata.from("type", "a")));
        }

        List<String> ids = store.addAll(embeddings, segments);

        assertThat(ids).hasSize(1200);
        assertThat(count()).isEqualTo(1200);
        EmbeddingMatch<TextSegment> match = search(store, embeddings.get(1199)).get(0);
        assertThat(match.embeddingId()).isEqualTo(ids.get(1199));
        assertThat(match.embedding()).isEqualTo(embeddings.get(1199));
        assertThat(match.embedded().text()).isEqualTo("text 1199");
        assertThat(match.embedded().metadata().getString("type")).isEqualTo("a");
    }

    @Test
    void should_add_embeddings_with_server_side_batching_and_default_settings() {
        WeaviateEmbeddingStore store = store(builder().serverSideBatching(true));

        store.addAll(randomEmbeddings(300, DIMENSION));

        assertThat(count()).isEqualTo(300);
    }

    @Test
    void should_add_large_batch_of_high_dimensional_embeddings() {
        // about 25 MB of vectors, more than one gRPC message can carry
        WeaviateEmbeddingStore store = store(builder());

        store.addAll(randomEmbeddings(4000, 1536));

        assertThat(count()).isEqualTo(4000);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_fail_when_weaviate_rejects_an_object(boolean serverSideBatching) {
        WeaviateEmbeddingStore store = store(builder().serverSideBatching(serverSideBatching));
        store.add(randomEmbedding(DIMENSION));

        // the dimension differs from the one of the collection
        assertThatThrownBy(() -> store.add(randomEmbedding(DIMENSION + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to add embeddings to Weaviate");
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void should_fail_when_id_is_not_a_uuid() {
        WeaviateEmbeddingStore store = store(builder());

        assertThatThrownBy(() -> store.add("not-a-uuid", randomEmbedding(DIMENSION)))
                .isInstanceOf(RuntimeException.class);
    }

    // ids

    @Test
    void should_replace_embedding_added_with_the_same_id() {
        WeaviateEmbeddingStore store = store(builder());
        String id = randomUUID();
        Embedding first = randomEmbedding(DIMENSION);
        Embedding second = randomEmbedding(DIMENSION);

        store.add(id, first);
        store.add(id, second);

        assertThat(count()).isEqualTo(1);
        assertThat(search(store, second).get(0).embedding()).isEqualTo(second);
    }

    // metadata

    @ParameterizedTest
    @ValueSource(strings = {"_metadata", "custom_metadata", ""})
    void should_return_only_configured_metadata_keys(String metadataFieldName) {
        WeaviateEmbeddingStore store =
                store(builder().metadataFieldName(metadataFieldName).metadataKeys(List.of("kept", "absent")));
        Embedding embedding = randomEmbedding(DIMENSION);
        Metadata metadata = new Metadata().put("kept", "value").put("dropped", "value");

        store.add(embedding, TextSegment.from("text", metadata));

        TextSegment embedded = search(store, embedding).get(0).embedded();
        assertThat(embedded.text()).isEqualTo("text");
        assertThat(embedded.metadata().toMap()).containsOnlyKeys("kept").containsEntry("kept", "value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"_metadata", ""})
    void should_return_no_metadata_when_no_metadata_keys_are_configured(String metadataFieldName) {
        WeaviateEmbeddingStore store = store(builder().metadataFieldName(metadataFieldName));
        Embedding embedding = randomEmbedding(DIMENSION);

        store.add(embedding, TextSegment.from("text", Metadata.from("dropped", "value")));

        TextSegment embedded = search(store, embedding).get(0).embedded();
        assertThat(embedded.text()).isEqualTo("text");
        assertThat(embedded.metadata().toMap()).isEmpty();
    }

    @Test
    void should_use_custom_text_field_name() {
        WeaviateEmbeddingStore store = store(builder().textFieldName("content"));
        Embedding embedding = randomEmbedding(DIMENSION);

        store.add(embedding, TextSegment.from("text"));

        assertThat(search(store, embedding).get(0).embedded().text()).isEqualTo("text");
        assertThat(client.collections
                        .use(objectClass)
                        .query
                        .fetchObjects()
                        .objects()
                        .get(0)
                        .properties())
                .containsEntry("content", "text");
    }

    // vector layouts of collections that were not created by the store

    @Test
    void should_work_with_collection_that_has_a_custom_named_vector() throws Exception {
        createCollection("""
                "vectorConfig": {"custom": {"vectorizer": {"none": {}}, "vectorIndexType": "hnsw"}}
                """);
        WeaviateEmbeddingStore store = store(builder());
        Embedding embedding = randomEmbedding(DIMENSION);

        String id = store.add(embedding, TextSegment.from("text"));

        EmbeddingMatch<TextSegment> match = search(store, embedding).get(0);
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void should_work_with_collection_that_is_created_after_the_store() throws Exception {
        WeaviateEmbeddingStore store = store(builder());
        assertThat(search(store, randomEmbedding(DIMENSION))).isEmpty();
        createCollection("""
                "vectorConfig": {"custom": {"vectorizer": {"none": {}}, "vectorIndexType": "hnsw"}}
                """);
        Embedding embedding = randomEmbedding(DIMENSION);

        store.add(embedding);

        assertThat(search(store, embedding).get(0).embedding()).isEqualTo(embedding);
    }

    // search

    @Test
    void should_respect_max_results_and_min_score() {
        WeaviateEmbeddingStore store = store(builder());
        Embedding embedding = Embedding.from(new float[] {1, 0, 0, 0, 0, 0, 0, 0});
        Embedding opposite = Embedding.from(new float[] {-1, 0, 0, 0, 0, 0, 0, 0});
        store.addAll(List.of(embedding, opposite));
        store.addAll(randomEmbeddings(40, DIMENSION));

        List<EmbeddingMatch<TextSegment>> limited = store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(30)
                        .build())
                .matches();
        List<EmbeddingMatch<TextSegment>> exact = store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(30)
                        .minScore(0.999)
                        .build())
                .matches();

        // more than QUERY_DEFAULTS_LIMIT, which is 25
        assertThat(limited).hasSize(30);
        assertThat(limited).isSortedAccordingTo((a, b) -> Double.compare(b.score(), a.score()));
        assertThat(exact).hasSize(1);
        assertThat(exact.get(0).embedding()).isEqualTo(embedding);
    }

    // protocol

    @Test
    void should_not_use_graphql() throws Exception {
        WeaviateEmbeddingStore store = store(builder().metadataKeys(List.of("type")));
        Embedding embedding = randomEmbedding(DIMENSION);
        String id = store.add(embedding, TextSegment.from("text", Metadata.from("type", "a")));
        store.add(randomEmbedding(DIMENSION), TextSegment.from("other text", Metadata.from("type", "b")));
        assertThat(search(store, embedding)).hasSize(2);
        store.remove(id);
        store.removeAll(metadataKey("type").isEqualTo("b"));
        store.removeAll();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://" + weaviate.getHost() + ":" + weaviate.getMappedPort(METRICS_PORT) + "/metrics"))
                .build();
        String metrics = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString())
                .body();

        // Weaviate counts requests per HTTP route and per API. The label query_type="get_graphql" is not
        // checked, since Weaviate uses it for the queries received over gRPC as well.
        assertThat(metrics).contains("weaviate_http_request_duration_seconds_count{method=\"GET\",route=\"/v1/schema");
        assertThat(metrics).doesNotContain("route=\"/v1/graphql\"").doesNotContain("api=\"graphql\"");
    }

    // configuration

    @ParameterizedTest
    @ValueSource(strings = {"ONE", "quorum", " All "})
    void should_accept_consistency_level(String consistencyLevel) {
        WeaviateEmbeddingStore store = store(builder().consistencyLevel(consistencyLevel));

        store.add(randomEmbedding(DIMENSION));

        assertThat(count()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("deprecation")
    void should_ignore_deprecated_grpc_settings() {
        WeaviateEmbeddingStore store = store(builder().useGrpcForInserts(false).securedGrpc(true));

        store.add(randomEmbedding(DIMENSION));

        assertThat(count()).isEqualTo(1);
    }

    @Test
    void should_accept_host_that_carries_the_port() {
        WeaviateEmbeddingStore store = store(WeaviateEmbeddingStore.builder()
                .scheme("http")
                .host(weaviate.getHttpHostAddress())
                .grpcPort(weaviate.getMappedPort(50051))
                .objectClass(objectClass));

        store.add(randomEmbedding(DIMENSION));

        assertThat(count()).isEqualTo(1);
    }

    @Test
    void should_fail_with_clear_message_when_distance_metric_has_no_certainty() throws Exception {
        createCollection("""
                "vectorConfig": {"default": {"vectorizer": {"none": {}}, "vectorIndexType": "hnsw",
                "vectorIndexConfig": {"distance": "dot"}}}
                """);
        WeaviateEmbeddingStore store = store(builder());
        Embedding embedding = randomEmbedding(DIMENSION);
        store.add(embedding);

        assertThatThrownBy(() -> search(store, embedding))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ertainty");
    }

    @Test
    void should_fail_to_build_when_weaviate_is_not_reachable() {
        assertThatThrownBy(() -> WeaviateEmbeddingStore.builder()
                        .scheme("http")
                        .host("localhost")
                        .port(1)
                        .grpcPort(2)
                        .build())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void should_fail_after_close() {
        WeaviateEmbeddingStore store = builder().build();
        store.add(randomEmbedding(DIMENSION));

        store.close();

        assertThatThrownBy(() -> store.add(randomEmbedding(DIMENSION))).isInstanceOf(RuntimeException.class);
        assertThatCode(store::close).doesNotThrowAnyException();
    }
}
