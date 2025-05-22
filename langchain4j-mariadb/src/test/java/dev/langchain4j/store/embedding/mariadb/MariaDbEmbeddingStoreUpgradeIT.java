package dev.langchain4j.store.embedding.mariadb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;

/**
 * Test upgrade from 029 to latest version
 */
class MariaDbEmbeddingStoreUpgradeIT {
    static MariaDBContainer<?> mariadbContainer = MariaDbTestUtils.defaultContainer;

    EmbeddingStore<TextSegment> embeddingStore029;

    EmbeddingStore<TextSegment> embeddingStore;

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void beforeAll() {
        mariadbContainer.start();
    }

    @BeforeEach
    void beforeEach() {
        final var tableName = "test" + nextInt(3000, 4000);
        embeddingStore029 = MariaDbEmbeddingStore.builder()
                .url(mariadbContainer.getJdbcUrl())
                .user(mariadbContainer.getUsername())
                .password(mariadbContainer.getPassword())
                .table(tableName)
                .dimension(384)
                .createTable(true)
                .dropTableFirst(true)
                .build();

        embeddingStore = MariaDbEmbeddingStore.builder()
                .url(mariadbContainer.getJdbcUrl())
                .user(mariadbContainer.getUsername())
                .password(mariadbContainer.getPassword())
                .table(tableName)
                .createTable(true)
                .dimension(384)
                .build();
    }

    @Test
    void upgrade() {
        var embedding = embeddingModel.embed("hello").content();

        var id = embeddingStore029.add(embedding);
        assertThat(id).isNotBlank();

        // Check 029 results
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .minScore(0.0)
                .build();
        EmbeddingSearchResult<?> embeddingSearchResult = embeddingStore029.search(embeddingSearchRequest);
        var relevant = embeddingSearchResult.matches();
        assertThat(relevant).hasSize(1);

        var match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();

        // new API
        assertThat(embeddingStore029
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(10)
                                .build())
                        .matches())
                .isEqualTo(relevant);

        // Check Latest Store results
        EmbeddingSearchRequest embeddingSearchRequest2 = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .minScore(0.0)
                .build();
        EmbeddingSearchResult<?> embeddingSearchResult2 = embeddingStore.search(embeddingSearchRequest2);
        var relevant2 = embeddingSearchResult2.matches();
        assertThat(relevant2).hasSize(1);

        match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();

        // new API
        assertThat(embeddingStore
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(10)
                                .build())
                        .matches())
                .isEqualTo(relevant);
    }
}
