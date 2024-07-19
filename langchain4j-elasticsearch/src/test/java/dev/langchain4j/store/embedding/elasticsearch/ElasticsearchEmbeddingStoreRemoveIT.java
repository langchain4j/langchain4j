package dev.langchain4j.store.embedding.elasticsearch;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.*;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.shaded.org.awaitility.core.ThrowingRunnable;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TODO add some methods like "EmbeddingStoreWithRemovalIT#wait_for_ready()"
 * so we can remove the "specialized" implementations
 */
class ElasticsearchEmbeddingStoreRemoveIT extends EmbeddingStoreWithRemovalIT {

    static ElasticsearchClientHelper elasticsearchClientHelper = new ElasticsearchClientHelper();

    EmbeddingStore<TextSegment> embeddingStore = ElasticsearchEmbeddingStore.builder()
            .restClient(elasticsearchClientHelper.restClient)
            .indexName(randomUUID())
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    String indexName;

    @BeforeAll
    static void startServices() throws IOException {
        elasticsearchClientHelper.startServices();
        assertThat(elasticsearchClientHelper.restClient).isNotNull();
        assertThat(elasticsearchClientHelper.client).isNotNull();
    }

    @AfterAll
    static void stopServices() throws IOException {
        elasticsearchClientHelper.stopServices();
    }

    @BeforeEach
    void createEmbeddingStore() throws IOException {
        indexName = randomUUID();
        elasticsearchClientHelper.removeDataStore(indexName);
        embeddingStore = ElasticsearchEmbeddingStore.builder()
                .restClient(elasticsearchClientHelper.restClient)
                .indexName(indexName)
                .build();
    }

    @AfterEach
    void removeDataStore() throws IOException {
        // We remove the indices in case we were running with a local test instance
        // we don't keep dirty things around
        elasticsearchClientHelper.removeDataStore(indexName);
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Test
    void should_not_fail_to_remove_non_existing_datastore() throws IOException {
        // given
        // Nothing

        // when
        embeddingStore.removeAll();

        // then
        assertThat(elasticsearchClientHelper.client.indices().exists(er -> er.index(indexName)).value()).isFalse();
    }

    @Test
    void should_remove_all() throws IOException {
        // given
        Embedding embedding = embeddingModel.embed("hello").content();
        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();
        embeddingStore.add(embedding);
        embeddingStore.add(embedding2);
        embeddingStore.add(embedding3);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();
        awaitAssertion(() -> assertThat(embeddingStore.search(request).matches()).hasSize(3));

        // when
        embeddingStore.removeAll();

        // then
        assertThat(elasticsearchClientHelper.client.indices().exists(er -> er.index(indexName)).value()).isFalse();
    }

    @Test
    void should_remove_by_id() {
        // given
        Embedding embedding = embeddingModel.embed("hello").content();
        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();

        String id = embeddingStore.add(embedding);
        String id2 = embeddingStore.add(embedding2);
        String id3 = embeddingStore.add(embedding3);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();
        awaitAssertion(() -> assertThat(embeddingStore.search(request).matches()).hasSize(3));

        // when
        embeddingStore.remove(id);
        awaitAssertion(() -> assertThat(embeddingStore.search(request).matches()).hasSize(2));

        // then
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        List<String> matchingIds = matches.stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());
        assertThat(matchingIds).containsExactly(id2, id3);
    }

    @Test
    void should_remove_all_by_ids() {
        // given
        Embedding embedding = embeddingModel.embed("hello").content();
        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();

        String id = embeddingStore.add(embedding);
        String id2 = embeddingStore.add(embedding2);
        String id3 = embeddingStore.add(embedding3);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();
        awaitAssertion(() -> assertThat(embeddingStore.search(request).matches()).hasSize(3));

        // when
        embeddingStore.removeAll(Arrays.asList(id2, id3));
        awaitAssertion(() -> assertThat(embeddingStore.search(request).matches()).hasSize(1));

        // then
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        List<String> matchingIds = matches.stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());
        assertThat(matchingIds).containsExactly(id);
    }

    @Test
    void should_remove_all_by_filter() {
        // given
        Metadata metadata = Metadata.metadata("id", "1");
        TextSegment segment = TextSegment.from("matching", metadata);
        Embedding embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();
        awaitAssertion(() -> assertThat(embeddingStore.search(request).matches()).hasSize(1));

        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();

        String id2 = embeddingStore.add(embedding2);
        String id3 = embeddingStore.add(embedding3);

        awaitAssertion(() -> assertThat(embeddingStore.search(request).matches()).hasSize(3));

        // when
        embeddingStore.removeAll(metadataKey("id").isEqualTo("1"));
        awaitAssertion(() -> assertThat(embeddingStore.search(request).matches()).hasSize(2));

        // then
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        List<String> matchingIds = matches.stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());
        assertThat(matchingIds).hasSize(2);
        assertThat(matchingIds).containsExactly(id2, id3);
    }

    @Test
    void should_remove_all_by_filter_not_matching() {
        // given
        Embedding embedding = embeddingModel.embed("hello").content();
        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();

        embeddingStore.add(embedding);
        embeddingStore.add(embedding2);
        embeddingStore.add(embedding3);
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();
        awaitAssertion(() -> assertThat(embeddingStore.search(request).matches()).hasSize(3));

        // when
        embeddingStore.removeAll(metadataKey("unknown").isEqualTo("1"));

        // then
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
        List<String> matchingIds = matches.stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());
        assertThat(matchingIds).hasSize(3);
    }

    private static void awaitAssertion(ThrowingRunnable assertionRunnable) {
        Awaitility.await().pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(assertionRunnable);
    }
}
