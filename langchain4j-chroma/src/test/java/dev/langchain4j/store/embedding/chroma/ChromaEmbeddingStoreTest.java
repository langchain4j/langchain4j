package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
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
    void should_convert_cosine_distance_to_score() {
        // given
        CapturingHttpClient httpClient = new CapturingHttpClient()
                .withCollection(collection("\"configuration_json\":{\"hnsw\":{\"space\":\"cosine\"}}"))
                .withDistances(0.0, 1.0, 2.0);

        // when
        List<EmbeddingMatch<TextSegment>> matches = search(httpClient);

        // then
        assertThat(matches).extracting(EmbeddingMatch::score).containsExactly(1.0, 0.5, 0.0);
    }

    @Test
    void should_convert_l2_distance_to_score() {
        // given
        CapturingHttpClient httpClient = new CapturingHttpClient()
                .withCollection(collection("\"configuration_json\":{\"hnsw\":{\"space\":\"l2\"}}"))
                .withDistances(0.0, 1.0, 101.0);

        // when
        List<EmbeddingMatch<TextSegment>> matches = search(httpClient);

        // then
        // "l2" is unbounded, so a distance greater than 2 used to result in a negative score
        assertThat(matches).extracting(EmbeddingMatch::score).containsExactly(1.0, 0.5, 1.0 / 102);
    }

    @Test
    void should_convert_ip_distance_to_score() {
        // given
        CapturingHttpClient httpClient = new CapturingHttpClient()
                .withCollection(collection("\"metadata\":{\"hnsw:space\":\"ip\"}"))
                .withDistances(0.0, 1.0, 2.0);

        // when
        List<EmbeddingMatch<TextSegment>> matches = search(httpClient);

        // then
        assertThat(matches).extracting(EmbeddingMatch::score).containsExactly(1.0, 0.5, 0.0);
    }

    @Test
    void should_prefer_the_distance_metric_reported_in_metadata_over_the_configured_one() {
        // given: this is what Chroma 0.5.x reports for a collection created by this store
        CapturingHttpClient httpClient = new CapturingHttpClient()
                .withCollection(
                        collection(
                                "\"configuration_json\":{\"hnsw_configuration\":{\"space\":\"l2\"}},\"metadata\":{\"hnsw:space\":\"cosine\"}"))
                .withDistances(0.5);

        // when
        List<EmbeddingMatch<TextSegment>> matches = search(httpClient);

        // then
        assertThat(matches).extracting(EmbeddingMatch::score).containsExactly(0.75);
    }

    @Test
    void should_fall_back_to_l2_when_the_existing_collection_does_not_report_a_distance_metric() {
        // given
        CapturingHttpClient httpClient = new CapturingHttpClient()
                .withCollection(collection("\"metadata\":{}"))
                .withDistances(0.5);

        // when
        List<EmbeddingMatch<TextSegment>> matches = search(httpClient);

        // then
        assertThat(matches).extracting(EmbeddingMatch::score).containsExactly(1.0 / 1.5);
    }

    private static List<EmbeddingMatch<TextSegment>> search(CapturingHttpClient httpClient) {
        return store(httpClient)
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(EMBEDDING_1)
                        .build())
                .matches();
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
