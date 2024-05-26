package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static io.milvus.common.clientenum.ConsistencyLevelEnum.STRONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
public class MilvusEmbeddingStoreRemoveIT {

    private static final String COLLECTION_NAME = "test_collection";

    @Container
    private static final MilvusContainer milvus = new MilvusContainer("milvusdb/milvus:v2.3.16");

    MilvusEmbeddingStore embeddingStore = MilvusEmbeddingStore.builder()
            .uri(milvus.getEndpoint())
            .collectionName(COLLECTION_NAME)
            .consistencyLevel(STRONG)
            .username("")
            .password("")
            .dimension(384)
            .retrieveEmbeddingsOnSearch(true)
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeEach
    void beforeEach() {
        embeddingStore.removeAll();
    }

    @Test
    void remove_by_id() {
        Embedding embedding = embeddingModel.embed("hello").content();
        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();

        String id = embeddingStore.add(embedding);
        String id2 = embeddingStore.add(embedding2);
        String id3 = embeddingStore.add(embedding3);

        assertThat(id).isNotBlank();
        assertThat(id2).isNotBlank();
        assertThat(id3).isNotBlank();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build()).matches();
        assertThat(relevant).hasSize(3);

        embeddingStore.remove(id);

        relevant = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build()).matches();
        List<String> relevantIds = relevant.stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());
        assertThat(relevantIds).hasSize(2);
        assertThat(relevantIds).containsExactly(id2, id3);
    }

    @Test
    void remove_all_by_ids() {
        Embedding embedding = embeddingModel.embed("hello").content();
        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();

        String id = embeddingStore.add(embedding);
        String id2 = embeddingStore.add(embedding2);
        String id3 = embeddingStore.add(embedding3);

        embeddingStore.removeAll(Arrays.asList(id2, id3));

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build()).matches();
        List<String> relevantIds = relevant.stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());
        assertThat(relevant).hasSize(1);
        assertThat(relevantIds).containsExactly(id);
    }

    @Test
    void remove_all_by_ids_null() {
        assertThatThrownBy(() -> embeddingStore.removeAll((Collection<String>) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids cannot be null or empty");
    }

    @Test
    void remove_all_by_filter() {
        Metadata metadata = Metadata.metadata("id", "1");
        TextSegment segment = TextSegment.from("matching", metadata);
        Embedding embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);

        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();

        String id2 = embeddingStore.add(embedding2);
        String id3 = embeddingStore.add(embedding3);

        embeddingStore.removeAll(metadataKey("id").isEqualTo("1"));

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build()).matches();
        List<String> relevantIds = relevant.stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());
        assertThat(relevantIds).hasSize(2);
        assertThat(relevantIds).containsExactly(id2, id3);
    }

    @Test
    void remove_all_by_filter_not_matching() {
        Embedding embedding = embeddingModel.embed("hello").content();
        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();

        embeddingStore.add(embedding);
        embeddingStore.add(embedding2);
        embeddingStore.add(embedding3);

        embeddingStore.removeAll(metadataKey("unknown").isEqualTo("1"));

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build()).matches();
        List<String> relevantIds = relevant.stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());
        assertThat(relevantIds).hasSize(3);
    }

    @Test
    void remove_all_by_filter_null() {
        assertThatThrownBy(() -> embeddingStore.removeAll((Filter) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("filter cannot be null");
    }
}