package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test upgrade from 029 to latest version
 */
@Testcontainers
public class PgVectorEmbeddingStoreRemoveIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    EmbeddingStore<TextSegment> embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeEach
    void beforeEach() {
        embeddingStore = PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table("test")
                .dimension(384)
                .build();
    }

    @Test
    void remove() {
        Embedding embedding = embeddingModel.embed("hello").content();
        Embedding embedding2 = embeddingModel.embed("hello2").content();
        Embedding embedding3 = embeddingModel.embed("hello3").content();

        String id = embeddingStore.add(embedding);
        String id2 = embeddingStore.add(embedding2);
        String id3 = embeddingStore.add(embedding3);

        assertThat(id).isNotBlank();
        assertThat(id2).isNotBlank();
        assertThat(id3).isNotBlank();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(3);

        embeddingStore.remove(id);
        relevant = embeddingStore.findRelevant(embedding, 10);
        List<String> relevantIds = relevant.stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toList());
        assertThat(relevantIds).hasSize(2);
        assertThat(relevantIds).containsExactly(id2, id3);

        List<String> removedIds = embeddingStore.removeAll(Arrays.asList(id2,id3));
        assertThat(removedIds).hasSize(2);
        assertThat(removedIds).containsExactly(id2, id3);
        relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(0);
    }
}
