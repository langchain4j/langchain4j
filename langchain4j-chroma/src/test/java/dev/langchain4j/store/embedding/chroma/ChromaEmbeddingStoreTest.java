package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.HttpRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChromaEmbeddingStoreTest {

    private static final Embedding EMBEDDING_1 = Embedding.from(List.of(1f, 2f, 3f));
    private static final Embedding EMBEDDING_2 = Embedding.from(List.of(4f, 5f, 6f));

    @Test
    void should_add_all_when_sizes_match() throws Exception {
        // given
        CapturingHttpClient httpClient = new CapturingHttpClient();
        ChromaEmbeddingStore store = store(httpClient);
        int requestsAfterInit = httpClient.requests().size();

        // when
        store.addAll(
                List.of("id1", "id2"),
                List.of(EMBEDDING_1, EMBEDDING_2),
                List.of(TextSegment.from("first"), TextSegment.from("second")));

        // then
        assertThat(httpClient.requests()).hasSize(requestsAfterInit + 1);

        HttpRequest addRequest = httpClient.requests().get(requestsAfterInit);
        assertThat(addRequest.method()).isEqualTo(POST);
        assertThat(addRequest.url()).endsWith("/add");

        JsonNode body = new ObjectMapper().readTree(addRequest.body());
        assertThat(body.get("ids")).map(JsonNode::asText).containsExactly("id1", "id2");
        assertThat(body.get("documents")).map(JsonNode::asText).containsExactly("first", "second");
        assertThat(body.get("embeddings")).map(JsonNode::toString).containsExactly("[1.0,2.0,3.0]", "[4.0,5.0,6.0]");
        assertThat(body.get("metadatas")).hasSize(2);
    }

    @Test
    void should_throw_when_ids_and_embeddings_have_different_sizes() {
        // given
        ChromaEmbeddingStore store = store(new CapturingHttpClient());

        // when + then
        assertThatThrownBy(() -> store.addAll(List.of("id1"), List.of(EMBEDDING_1, EMBEDDING_2), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids size (1) is not equal to embeddings size (2)");
    }

    @Test
    void should_throw_when_embeddings_are_provided_without_ids() {
        // given
        ChromaEmbeddingStore store = store(new CapturingHttpClient());

        // when + then
        assertThatThrownBy(() -> store.addAll(List.of(), List.of(EMBEDDING_1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids size (0) is not equal to embeddings size (1)");

        assertThatThrownBy(() -> store.addAll(null, List.of(EMBEDDING_1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids size (0) is not equal to embeddings size (1)");
    }

    @Test
    void should_throw_when_embeddings_and_text_segments_have_different_sizes() {
        // given
        ChromaEmbeddingStore store = store(new CapturingHttpClient());

        // when + then
        assertThatThrownBy(() -> store.addAll(
                        List.of("id1", "id2"),
                        List.of(EMBEDDING_1, EMBEDDING_2),
                        List.of(TextSegment.from("only one segment"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("embeddings size (2) is not equal to embedded size (1)");
    }

    @Test
    void should_not_call_server_when_input_is_empty() {
        // given
        CapturingHttpClient httpClient = new CapturingHttpClient();
        ChromaEmbeddingStore store = store(httpClient);
        int requestsAfterInit = httpClient.requests().size();

        // when
        store.addAll(List.of(), List.of(), null);
        store.addAll(null, null, null);

        // then
        assertThat(httpClient.requests()).hasSize(requestsAfterInit);
    }

    @Test
    void should_throw_when_existing_collection_does_not_use_cosine_distance() {
        // given
        CapturingHttpClient httpClient =
                new CapturingHttpClient(collection("\"configuration_json\":{\"hnsw\":{\"space\":\"l2\"}}"));

        // when + then
        assertThatThrownBy(() -> store(httpClient))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Chroma collection 'test' uses distance metric 'l2', but ChromaEmbeddingStore requires "
                        + "'cosine' to produce valid relevance scores. Use or recreate a collection configured with "
                        + "cosine distance.");
    }

    @Test
    void should_throw_when_existing_collection_declares_a_non_cosine_distance_metric_in_metadata() {
        // given
        CapturingHttpClient httpClient = new CapturingHttpClient(collection("\"metadata\":{\"hnsw:space\":\"ip\"}"));

        // when + then
        assertThatThrownBy(() -> store(httpClient))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Chroma collection 'test' uses distance metric 'ip', but ChromaEmbeddingStore requires "
                        + "'cosine' to produce valid relevance scores. Use or recreate a collection configured with "
                        + "cosine distance.");
    }

    @Test
    void should_accept_existing_collection_that_uses_cosine_distance() {
        // given
        CapturingHttpClient httpClient =
                new CapturingHttpClient(collection("\"configuration_json\":{\"hnsw\":{\"space\":\"cosine\"}}"));

        // when + then
        assertThatCode(() -> store(httpClient)).doesNotThrowAnyException();
    }

    @Test
    void should_throw_when_existing_collection_does_not_report_a_distance_metric() {
        // given
        CapturingHttpClient httpClient = new CapturingHttpClient(collection("\"metadata\":{}"));

        // when + then
        assertThatThrownBy(() -> store(httpClient))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Chroma collection 'test' uses distance metric 'l2', but ChromaEmbeddingStore requires "
                        + "'cosine' to produce valid relevance scores. Use or recreate a collection configured with "
                        + "cosine distance.");
    }

    @Test
    void should_accept_existing_collection_whose_metadata_reports_cosine_while_its_configuration_reports_l2() {
        // given
        CapturingHttpClient httpClient = new CapturingHttpClient(
                collection(
                        "\"configuration_json\":{\"hnsw_configuration\":{\"space\":\"l2\"}},\"metadata\":{\"hnsw:space\":\"cosine\"}"));

        // when + then
        assertThatCode(() -> store(httpClient)).doesNotThrowAnyException();
    }

    private static String collection(String distanceMetricField) {
        return "{\"id\":\"collection-id\",\"name\":\"test\"," + distanceMetricField + "}";
    }

    private static ChromaEmbeddingStore store(CapturingHttpClient httpClient) {
        return ChromaEmbeddingStore.builder()
                .baseUrl("http://localhost:8000")
                .collectionName("test")
                .httpClientBuilder(new TestHttpClientBuilder(httpClient))
                .build();
    }
}
