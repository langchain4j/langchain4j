package dev.langchain4j.store.embedding.weaviate;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.*;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.weaviate.WeaviateContainer;

@Testcontainers
class LocalWeaviateEmbeddingStoreRemoveIT {

    @Container
    static WeaviateContainer weaviate = new WeaviateContainer("semitechnologies/weaviate:latest")
            .withEnv("QUERY_DEFAULTS_LIMIT", "25")
            .withEnv("DEFAULT_VECTORIZER_MODULE", "none")
            .withEnv("CLUSTER_HOSTNAME", "node1");

    private final EmbeddingStore<TextSegment> embeddingStore = WeaviateEmbeddingStore.builder()
            .scheme("http")
            .host(weaviate.getHost())
            .port(weaviate.getFirstMappedPort())
            .objectClass("Test" + randomUUID().replace("-", ""))
            .metadataKeys(Arrays.asList("id"))
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeEach
    void beforeEach() {
        embeddingStore.removeAll();
    }

    // ---------------------------------------------------------
    // remove(String id)
    // ---------------------------------------------------------
    @Test
    void remove_by_id() {

        Embedding embedding = embeddingModel.embed("hello").content();
        String id = embeddingStore.add(embedding);

        embeddingStore.remove(id);

        var result = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build());

        assertThat(result.matches()).noneMatch(m -> id.equals(m.embeddingId()));
    }

    // ---------------------------------------------------------
    // removeAll(Collection<String>)
    // ---------------------------------------------------------
    @Test
    void remove_all_by_ids() {

        String id1 = embeddingStore.add(embeddingModel.embed("one").content());
        String id2 = embeddingStore.add(embeddingModel.embed("two").content());
        String id3 = embeddingStore.add(embeddingModel.embed("three").content());

        embeddingStore.removeAll(Arrays.asList(id2, id3));

        var result = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("one").content())
                .maxResults(10)
                .build());

        List<String> remainingIds =
                result.matches().stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());

        assertThat(remainingIds).containsExactly(id1);
    }

    // ---------------------------------------------------------
    // removeAll(Collection<String>) null validation
    // ---------------------------------------------------------
    @Test
    void remove_all_by_ids_null() {
        assertThatThrownBy(() -> embeddingStore.removeAll((Collection<String>) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids cannot be null or empty");
    }

    // ---------------------------------------------------------
    // removeAll(Filter)
    // ---------------------------------------------------------
    @Test
    void remove_all_by_filter() {

        Metadata metadata = Metadata.metadata("id", "1");
        TextSegment segment = TextSegment.from("matching", metadata);
        Embedding embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);

        String id2 = embeddingStore.add(embeddingModel.embed("keep1").content());
        String id3 = embeddingStore.add(embeddingModel.embed("keep2").content());

        embeddingStore.removeAll(metadataKey("id").isEqualTo("1"));

        var result = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("keep1").content())
                .maxResults(10)
                .build());

        List<String> remainingIds =
                result.matches().stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());

        assertThat(remainingIds).contains(id2, id3);
    }

    // ---------------------------------------------------------
    // removeAll(Filter) non matching
    // ---------------------------------------------------------
    @Test
    void remove_all_by_filter_not_matching() {

        embeddingStore.add(embeddingModel.embed("a").content());
        embeddingStore.add(embeddingModel.embed("b").content());
        embeddingStore.add(embeddingModel.embed("c").content());

        embeddingStore.removeAll(metadataKey("unknown").isEqualTo("1"));

        var result = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("a").content())
                .maxResults(10)
                .build());

        assertThat(result.matches()).hasSize(3);
    }

    // ---------------------------------------------------------
    // removeAll(Filter) null validation
    // ---------------------------------------------------------
    @Test
    void remove_all_by_filter_null() {
        assertThatThrownBy(() -> embeddingStore.removeAll((Filter) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("filter cannot be null");
    }
}
