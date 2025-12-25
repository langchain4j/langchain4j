package dev.langchain4j.store.embedding.pgvector;

import static dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore.SearchMode.EMBEDDING_ONLY;
import static dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore.SearchMode.FULL_TEXT_ONLY;
import static dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore.SearchMode.HYBRID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PgVectorEmbeddingStoreSearchModeIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    EmbeddingStore<TextSegment> storeEmbeddingOnly;
    EmbeddingStore<TextSegment> storeFullTextOnly;
    EmbeddingStore<TextSegment> storeHybrid;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeEach
    void setUp() {
        String tableBase = "search_mode_test_" + nextInt(1000, 2000);

        storeEmbeddingOnly = PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table(tableBase + "_emb")
                .dimension(384)
                .dropTableFirst(true)
                .searchMode(EMBEDDING_ONLY)
                .build();

        storeFullTextOnly = PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table(tableBase + "_fts")
                .dimension(384)
                .dropTableFirst(true)
                .searchMode(FULL_TEXT_ONLY)
                .textSearchConfig("english")
                .build();

        storeHybrid = PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table(tableBase + "_hybrid")
                .dimension(384)
                .dropTableFirst(true)
                .searchMode(HYBRID)
                .textSearchConfig("english")
                .build();

        List<TextSegment> segments = List.of(
                TextSegment.from("Java is a popular programming language."),
                TextSegment.from("PostgreSQL supports pgvector for vector similarity search."),
                TextSegment.from("Full text search in PostgreSQL uses tsvector and tsquery."));

        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment).content();
            storeEmbeddingOnly.add(embedding, segment);
            storeFullTextOnly.add(embedding, segment);
            storeHybrid.add(embedding, segment);
        }
    }

    @Test
    void embeddingOnly_shouldReturnMostSimilarByVector() {
        TextSegment query = TextSegment.from("vector similarity search in databases");
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .build();

        EmbeddingSearchResult<TextSegment> result = storeEmbeddingOnly.search(request);

        assertThat(result.matches()).isNotEmpty();
        String topText = result.matches().get(0).embedded().text();

        assertThat(topText).containsIgnoringCase("pgvector").containsIgnoringCase("vector");
    }

    @Test
    void fullTextOnly_shouldUseKeywordMatching() {
        TextSegment query = TextSegment.from("vector similarity search in databases");
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .query("tsvector tsquery")
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .build();

        EmbeddingSearchResult<TextSegment> result = storeFullTextOnly.search(request);

        assertThat(result.matches()).isNotEmpty();
        String topText = result.matches().get(0).embedded().text();

        assertThat(topText).containsIgnoringCase("Full text search").containsIgnoringCase("tsvector");
    }

    @Test
    void fullTextOnly_withEmptyQuery_shouldReturnEmpty() {
        TextSegment query = TextSegment.from("vector similarity search in databases");
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .query("   ")
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .build();

        EmbeddingSearchResult<TextSegment> result = storeFullTextOnly.search(request);
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void hybrid_shouldFuseVectorAndKeywordRanks_withRRF() {
        TextSegment querySegment = TextSegment.from("PostgreSQL vector search and full text search");
        Embedding queryEmbedding = embeddingModel.embed(querySegment).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .query("tsvector tsquery")
                .maxResults(3)
                .build();

        EmbeddingSearchResult<TextSegment> hybridResult = storeHybrid.search(request);

        assertThat(hybridResult.matches()).isNotEmpty();
        List<String> topTexts =
                hybridResult.matches().stream().map(m -> m.embedded().text()).toList();

        assertThat(topTexts.stream().anyMatch(t -> t.contains("pgvector"))).isTrue();
        assertThat(topTexts.stream().anyMatch(t -> t.contains("tsvector") || t.contains("tsquery")))
                .isTrue();
    }

    @Test
    void hybrid_withEmptyQuery_shouldReturnEmpty() {
        TextSegment query = TextSegment.from("vector similarity search in databases");
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .query("   ")
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .build();

        EmbeddingSearchResult<TextSegment> result = storeHybrid.search(request);
        assertThat(result.matches()).isEmpty();
    }
}
