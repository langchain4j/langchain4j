package dev.langchain4j.store.embedding.pgvector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for PgVector hybrid search functionality.
 * Tests VECTOR, FULL_TEXT, and HYBRID query types.
 */
@Testcontainers
class PgVectorEmbeddingStoreHybridSearchIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private String tableName;

    @BeforeEach
    void setUp() {
        tableName = "test_hybrid_" + nextInt(1000, 9999);
    }

    @Test
    void should_search_with_default_vector_query_type() {
        // Given
        PgVectorEmbeddingStore store = createStore(PgVectorQueryType.VECTOR);

        TextSegment segment1 = TextSegment.from("The quick brown fox jumps over the lazy dog");
        TextSegment segment2 = TextSegment.from("Machine learning is a subset of artificial intelligence");
        TextSegment segment3 = TextSegment.from("Java is a popular programming language");

        Embedding embedding1 = embeddingModel.embed(segment1.text()).content();
        Embedding embedding2 = embeddingModel.embed(segment2.text()).content();
        Embedding embedding3 = embeddingModel.embed(segment3.text()).content();

        store.add(embedding1, segment1);
        store.add(embedding2, segment2);
        store.add(embedding3, segment3);

        // When - search for something semantically similar to "fox"
        Embedding queryEmbedding = embeddingModel.embed("fox animal").content();
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(0.0)
                .build());

        // Then
        assertThat(result.matches()).isNotEmpty();
        assertThat(result.matches().get(0).embedded().text()).contains("fox");
    }

    @Test
    void should_search_with_hybrid_query_type() {
        // Given
        PgVectorEmbeddingStore store = createStore(PgVectorQueryType.HYBRID);

        TextSegment segment1 = TextSegment.from("The quick brown fox jumps over the lazy dog");
        TextSegment segment2 = TextSegment.from("Machine learning and artificial intelligence are related fields");
        TextSegment segment3 =
                TextSegment.from("Programming in Java requires understanding of object-oriented concepts");

        Embedding embedding1 = embeddingModel.embed(segment1.text()).content();
        Embedding embedding2 = embeddingModel.embed(segment2.text()).content();
        Embedding embedding3 = embeddingModel.embed(segment3.text()).content();

        store.add(embedding1, segment1);
        store.add(embedding2, segment2);
        store.add(embedding3, segment3);

        // When - search for "machine learning"
        String searchQuery = "machine learning AI";
        Embedding queryEmbedding = embeddingModel.embed(searchQuery).content();
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .query(searchQuery) // Required for hybrid search full-text component
                .maxResults(3)
                .minScore(0.0)
                .build());

        // Then - hybrid search should return results
        assertThat(result.matches()).isNotEmpty();
        // The machine learning segment should be highly ranked due to both semantic and keyword match
        assertThat(result.matches().get(0).embedded().text()).containsIgnoringCase("machine learning");
    }

    @Test
    void should_use_custom_weights_for_hybrid_search() {
        // Given - create store with custom weights (higher text weight)
        PgVectorEmbeddingStore store = PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table(tableName + "_custom_weights")
                .dimension(384)
                .dropTableFirst(true)
                .queryType(PgVectorQueryType.HYBRID)
                .vectorWeight(0.3) // Lower vector weight
                .textWeight(0.7) // Higher text weight
                .build();

        TextSegment segment1 = TextSegment.from("PostgreSQL database management system");
        TextSegment segment2 = TextSegment.from("Database systems store and retrieve data efficiently");

        Embedding embedding1 = embeddingModel.embed(segment1.text()).content();
        Embedding embedding2 = embeddingModel.embed(segment2.text()).content();

        store.add(embedding1, segment1);
        store.add(embedding2, segment2);

        // When
        String searchQuery = "PostgreSQL database";
        Embedding queryEmbedding = embeddingModel.embed(searchQuery).content();
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .query(searchQuery) // Required for hybrid search full-text component
                .maxResults(2)
                .minScore(0.0)
                .build());

        // Then
        assertThat(result.matches()).hasSize(2);
        // With higher text weight, exact keyword match "PostgreSQL" should boost first result
        assertThat(result.matches().get(0).embedded().text()).contains("PostgreSQL");
    }

    @Test
    void should_work_with_metadata_in_hybrid_mode() {
        // Given
        PgVectorEmbeddingStore store = PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table(tableName + "_metadata")
                .dimension(384)
                .dropTableFirst(true)
                .queryType(PgVectorQueryType.HYBRID)
                .metadataStorageConfig(DefaultMetadataStorageConfig.defaultConfig())
                .build();

        TextSegment segment1 = TextSegment.from(
                "Spring Boot makes Java development easier",
                Metadata.from(Map.of("category", "java", "level", "beginner")));
        TextSegment segment2 = TextSegment.from(
                "Python is great for data science",
                Metadata.from(Map.of("category", "python", "level", "intermediate")));

        Embedding embedding1 = embeddingModel.embed(segment1.text()).content();
        Embedding embedding2 = embeddingModel.embed(segment2.text()).content();

        store.add(embedding1, segment1);
        store.add(embedding2, segment2);

        // When
        String searchQuery = "Java development";
        Embedding queryEmbedding = embeddingModel.embed(searchQuery).content();
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .query(searchQuery) // Required for hybrid search full-text component
                .maxResults(2)
                .minScore(0.0)
                .build());

        // Then
        assertThat(result.matches()).isNotEmpty();
        EmbeddingMatch<TextSegment> topMatch = result.matches().get(0);
        assertThat(topMatch.embedded().text()).contains("Java");
        assertThat(topMatch.embedded().metadata().getString("category")).isEqualTo("java");
    }

    @Test
    void should_support_different_fts_languages() {
        // Given - create store with German language configuration
        PgVectorEmbeddingStore store = PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table(tableName + "_german")
                .dimension(384)
                .dropTableFirst(true)
                .queryType(PgVectorQueryType.HYBRID)
                .ftsLanguage("german")
                .build();

        // German text
        TextSegment segment1 = TextSegment.from("Die Softwareentwicklung ist ein wichtiger Beruf");
        TextSegment segment2 = TextSegment.from("Programmierung erfordert logisches Denken");

        Embedding embedding1 = embeddingModel.embed(segment1.text()).content();
        Embedding embedding2 = embeddingModel.embed(segment2.text()).content();

        store.add(embedding1, segment1);
        store.add(embedding2, segment2);

        // When
        String searchQuery = "Software Entwicklung";
        Embedding queryEmbedding = embeddingModel.embed(searchQuery).content();
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .query(searchQuery) // Required for hybrid search full-text component
                .maxResults(2)
                .minScore(0.0)
                .build());

        // Then
        assertThat(result.matches()).isNotEmpty();
    }

    @Test
    void should_return_scores_in_expected_range_for_hybrid_search() {
        // Given
        PgVectorEmbeddingStore store = createStore(PgVectorQueryType.HYBRID);

        TextSegment segment = TextSegment.from("LangChain4j is a Java library for building LLM applications");
        Embedding embedding = embeddingModel.embed(segment.text()).content();
        store.add(embedding, segment);

        // When - search with exact same text (should get high score)
        String searchQuery = "LangChain4j Java library LLM applications";
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .query(searchQuery) // Required for hybrid search full-text component
                .maxResults(1)
                .minScore(0.0)
                .build());

        // Then
        assertThat(result.matches()).hasSize(1);
        double score = result.matches().get(0).score();
        // Score should be positive and reasonably high for exact match
        assertThat(score).isGreaterThan(0.5);
    }

    @Test
    void should_handle_empty_results_gracefully() {
        // Given
        PgVectorEmbeddingStore store = createStore(PgVectorQueryType.HYBRID);

        TextSegment segment = TextSegment.from("This is a test document about artificial intelligence");
        Embedding embedding = embeddingModel.embed(segment.text()).content();
        store.add(embedding, segment);

        // When - search with very high minScore that won't match anything
        String searchQuery = "completely unrelated topic xyz123";
        Embedding queryEmbedding = embeddingModel.embed(searchQuery).content();
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .query(searchQuery) // Required for hybrid search full-text component
                .maxResults(10)
                .minScore(0.99) // Very high threshold
                .build());

        // Then
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void should_compare_vector_vs_hybrid_results() {
        // Given - create two stores with different query types
        PgVectorEmbeddingStore vectorStore = createStore(PgVectorQueryType.VECTOR);

        PgVectorEmbeddingStore hybridStore = PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table(tableName + "_hybrid_compare")
                .dimension(384)
                .dropTableFirst(true)
                .queryType(PgVectorQueryType.HYBRID)
                .build();

        // Same data in both stores
        TextSegment segment1 = TextSegment.from("Vector databases store embeddings for similarity search");
        TextSegment segment2 = TextSegment.from("Full-text search uses inverted indexes for keyword matching");
        TextSegment segment3 = TextSegment.from("Hybrid search combines vector and text search approaches");

        List<TextSegment> segments = List.of(segment1, segment2, segment3);

        for (TextSegment segment : segments) {
            Embedding emb = embeddingModel.embed(segment.text()).content();
            vectorStore.add(emb, segment);
            hybridStore.add(emb, segment);
        }

        // When
        String searchQuery = "hybrid vector text search";
        Embedding queryEmbedding = embeddingModel.embed(searchQuery).content();

        EmbeddingSearchResult<TextSegment> vectorResult = vectorStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(0.0)
                .build());

        EmbeddingSearchResult<TextSegment> hybridResult = hybridStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .query(searchQuery) // Required for hybrid search full-text component
                .maxResults(3)
                .minScore(0.0)
                .build());

        // Then - both should return results
        assertThat(vectorResult.matches()).isNotEmpty();
        assertThat(hybridResult.matches()).isNotEmpty();

        // Hybrid search should boost the segment containing "hybrid" keyword
        assertThat(hybridResult.matches().get(0).embedded().text()).containsIgnoringCase("hybrid");
    }

    private PgVectorEmbeddingStore createStore(PgVectorQueryType queryType) {
        return PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table(tableName + "_" + queryType.name().toLowerCase())
                .dimension(384)
                .dropTableFirst(true)
                .queryType(queryType)
                .build();
    }
}
