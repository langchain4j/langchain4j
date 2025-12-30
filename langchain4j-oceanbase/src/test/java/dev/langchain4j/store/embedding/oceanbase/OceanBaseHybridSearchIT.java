package dev.langchain4j.store.embedding.oceanbase;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for OceanBase hybrid search functionality.
 * <p>
 * By default, integration tests will run a docker image of OceanBase using TestContainers.
 * Alternatively, the tests can connect to an OceanBase instance if the following environment variables are configured:
 * <ul>
 *   <li>{@code OCEANBASE_url}: JDBC url (e.g., "jdbc:oceanbase://127.0.0.1:2881/test")</li>
 *   <li>{@code OCEANBASE_USER}: Username (e.g., "root@test")</li>
 *   <li>{@code OCEANBASE_PASSWORD}: Password</li>
 * </ul>
 */
class OceanBaseHybridSearchIT {

    private static final String TABLE_NAME = "test_hybrid_search_" + System.currentTimeMillis();
    
    private OceanBaseEmbeddingStore embeddingStore;
    private static final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void beforeAll() {
        OceanBaseContainerTestBase.initContainer();
    }

    @AfterAll
    static void afterAll() {
        OceanBaseContainerTestBase.stopContainer();
    }

    @BeforeEach
    void setUp() {
        embeddingStore = OceanBaseEmbeddingStore.builder()
                .url(OceanBaseContainerTestBase.getJdbcUrl())
                .user(OceanBaseContainerTestBase.getUsername())
                .password(OceanBaseContainerTestBase.getPassword())
                .tableName(TABLE_NAME)
                .dimension(384)
                .metricType("cosine")
                .enableHybridSearch(true)  // Enable hybrid search
                .retrieveEmbeddingsOnSearch(false)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (embeddingStore != null) {
            try {
                embeddingStore.dropCollection(TABLE_NAME);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    void test_hybrid_search_with_text_and_vector() {
        // Add test documents
        String id1 = embeddingStore.add(
                embeddingModel.embed("Java is a programming language").content(),
                TextSegment.from("Java is a programming language", 
                        dev.langchain4j.data.document.Metadata.from("category", "programming"))
        );
        
        embeddingStore.add(
                embeddingModel.embed("Python is great for data science").content(),
                TextSegment.from("Python is great for data science",
                        dev.langchain4j.data.document.Metadata.from("category", "data-science"))
        );
        
        embeddingStore.add(
                embeddingModel.embed("Machine learning algorithms").content(),
                TextSegment.from("Machine learning algorithms",
                        dev.langchain4j.data.document.Metadata.from("category", "ai"))
        );

        // Perform hybrid search with both query text and embedding
        String queryText = "programming language";
        Embedding queryEmbedding = embeddingModel.embed(queryText).content();
        
        EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .query(queryText)  // Text query for fulltext search
                        .maxResults(10)
                        .minScore(0.0)
                        .build()
        );

        List<EmbeddingMatch<TextSegment>> matches = results.matches();

        // Verify results
        assertThat(matches).isNotEmpty();
        // The first result should be most relevant (Java document)
        assertThat(matches.get(0).embeddingId()).isEqualTo(id1);
    }

    @Test
    void test_vector_search_only() {
        // Add test documents
        embeddingStore.add(
                embeddingModel.embed("Java programming").content(),
                TextSegment.from("Java programming")
        );
        
        embeddingStore.add(
                embeddingModel.embed("Python programming").content(),
                TextSegment.from("Python programming")
        );

        // Perform vector search only (no query text)
        Embedding queryEmbedding = embeddingModel.embed("Java").content();
        
        EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        // No .query() - will use vector search only
                        .maxResults(10)
                        .build()
        );

        List<EmbeddingMatch<TextSegment>> matches = results.matches();

        assertThat(matches).isNotEmpty();
    }

    @Test
    void test_fulltext_search_benefits() {
        // Add documents with specific keywords
        embeddingStore.add(
                embeddingModel.embed("Introduction to machine learning").content(),
                TextSegment.from("Introduction to machine learning")
        );
        
        embeddingStore.add(
                embeddingModel.embed("Deep learning neural networks").content(),
                TextSegment.from("Deep learning neural networks")
        );
        
        embeddingStore.add(
                embeddingModel.embed("Natural language processing").content(),
                TextSegment.from("Natural language processing")
        );

        // Search with query that matches exact keywords
        String queryText = "machine learning";
        Embedding queryEmbedding = embeddingModel.embed(queryText).content();
        
        EmbeddingSearchResult<TextSegment> hybridResults = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .query(queryText)  // Hybrid search
                        .maxResults(10)
                        .build()
        );

        assertThat(hybridResults.matches()).isNotEmpty();
    }
}

