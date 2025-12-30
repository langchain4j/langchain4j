package dev.langchain4j.store.embedding.oceanbase;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for OceanBase filter functionality.
 * <p>
 * This test verifies that filtering works correctly for both metadata fields and table columns.
 * <p>
 * By default, integration tests will run a docker image of OceanBase using TestContainers.
 * Alternatively, the tests can connect to an OceanBase instance if the following environment variables are configured:
 * <ul>
 *   <li>{@code OCEANBASE_url}: JDBC url (e.g., "jdbc:oceanbase://127.0.0.1:2881/test")</li>
 *   <li>{@code OCEANBASE_USER}: Username (e.g., "root@test")</li>
 *   <li>{@code OCEANBASE_PASSWORD}: Password</li>
 * </ul>
 */
class OceanBaseFilterIT {

    private static final String TABLE_NAME = "test_filter_" + System.currentTimeMillis();
    
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
                .enableHybridSearch(false)  // Disable hybrid search for filter tests
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
    void test_filter_by_metadata_field() {
        // Add test documents with different metadata
        embeddingStore.add(
                embeddingModel.embed("Java programming").content(),
                TextSegment.from("Java programming", 
                        new Metadata().put("category", "programming").put("language", "Java"))
        );
        
        embeddingStore.add(
                embeddingModel.embed("Python programming").content(),
                TextSegment.from("Python programming",
                        new Metadata().put("category", "programming").put("language", "Python"))
        );
        
        embeddingStore.add(
                embeddingModel.embed("Machine learning").content(),
                TextSegment.from("Machine learning",
                        new Metadata().put("category", "ai").put("language", "Python"))
        );
        
        // Search with filter: category = "programming"
        Filter filter = metadataKey("category").isEqualTo("programming");
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("programming").content())
                .filter(filter)
                .maxResults(10)
                .build();
        
        EmbeddingSearchResult<TextSegment> results = embeddingStore.search(request);
        
        // Should only return documents with category = "programming"
        assertThat(results.matches()).hasSize(2);
        assertThat(results.matches()).extracting(m -> m.embedded().metadata().toMap().get("category"))
                .containsOnly("programming");
    }

    @Test
    void test_filter_by_multiple_metadata_fields() {
        // Add test documents
        embeddingStore.add(
                embeddingModel.embed("Java programming").content(),
                TextSegment.from("Java programming", 
                        new Metadata().put("category", "programming").put("language", "Java").put("year", 2023))
        );
        
        embeddingStore.add(
                embeddingModel.embed("Python programming").content(),
                TextSegment.from("Python programming",
                        new Metadata().put("category", "programming").put("language", "Python").put("year", 2024))
        );
        
        embeddingStore.add(
                embeddingModel.embed("Machine learning").content(),
                TextSegment.from("Machine learning",
                        new Metadata().put("category", "ai").put("language", "Python").put("year", 2024))
        );
        
        // Search with filter: category = "programming" AND language = "Java"
        Filter filter = new And(
                metadataKey("category").isEqualTo("programming"),
                metadataKey("language").isEqualTo("Java")
        );
        
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("programming").content())
                .filter(filter)
                .maxResults(10)
                .build();
        
        EmbeddingSearchResult<TextSegment> results = embeddingStore.search(request);
        
        // Should only return documents with category = "programming" AND language = "Java"
        assertThat(results.matches()).hasSize(1);
        assertThat(results.matches().get(0).embedded().metadata().toMap().get("category")).isEqualTo("programming");
        assertThat(results.matches().get(0).embedded().metadata().toMap().get("language")).isEqualTo("Java");
    }

    @Test
    void test_filter_by_id_field() {
        // Add test documents
        String id1 = embeddingStore.add(
                embeddingModel.embed("Java programming").content(),
                TextSegment.from("Java programming", Metadata.from("category", "programming"))
        );
        
        embeddingStore.add(
                embeddingModel.embed("Python programming").content(),
                TextSegment.from("Python programming", Metadata.from("category", "programming"))
        );
        
        // Search with filter: id = id1
        // Note: In OceanBase, we need to use the actual field name from FieldDefinition
        // For now, we'll test with metadata field filtering, as direct id filtering
        // would require knowing the exact field name used in the table
        
        // Test with IsIn filter for multiple IDs
        Filter filter = new IsIn("id", List.of(id1));
        
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("programming").content())
                .filter(filter)
                .maxResults(10)
                .build();
        
        EmbeddingSearchResult<TextSegment> results = embeddingStore.search(request);
        
        // Should only return the document with id1
        assertThat(results.matches()).hasSize(1);
        assertThat(results.matches().get(0).embeddingId()).isEqualTo(id1);
    }

    @Test
    void test_filter_with_is_in() {
        // Add test documents
        embeddingStore.add(
                embeddingModel.embed("Java programming").content(),
                TextSegment.from("Java programming", 
                        new Metadata().put("category", "programming").put("language", "Java"))
        );
        
        embeddingStore.add(
                embeddingModel.embed("Python programming").content(),
                TextSegment.from("Python programming",
                        new Metadata().put("category", "programming").put("language", "Python"))
        );
        
        embeddingStore.add(
                embeddingModel.embed("Machine learning").content(),
                TextSegment.from("Machine learning",
                        new Metadata().put("category", "ai").put("language", "Python"))
        );
        
        // Search with filter: language IN ("Java", "Python")
        Filter filter = metadataKey("language").isIn("Java", "Python");
        
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("programming").content())
                .filter(filter)
                .maxResults(10)
                .build();
        
        EmbeddingSearchResult<TextSegment> results = embeddingStore.search(request);
        
        // Should return all documents with language = "Java" or "Python"
        // Filter is applied at SQL level, so all matching documents are returned
        assertThat(results.matches()).hasSize(3);
        assertThat(results.matches()).extracting(m -> m.embedded().metadata().toMap().get("language"))
                .containsExactlyInAnyOrder("Java", "Python", "Python");
    }

    @Test
    void test_filter_with_hybrid_search() {
        // Add test documents
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
        
        embeddingStore.add(
                embeddingModel.embed("Java programming language").content(),
                TextSegment.from("Java programming language", 
                        Metadata.from("category", "programming"))
        );
        
        embeddingStore.add(
                embeddingModel.embed("Python programming language").content(),
                TextSegment.from("Python programming language",
                        Metadata.from("category", "programming"))
        );
        
        embeddingStore.add(
                embeddingModel.embed("Machine learning algorithms").content(),
                TextSegment.from("Machine learning algorithms",
                        Metadata.from("category", "ai"))
        );
        
        // Hybrid search with filter: category = "programming"
        Filter filter = metadataKey("category").isEqualTo("programming");
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("programming language").content())
                .query("programming language")  // Text query for hybrid search
                .filter(filter)
                .maxResults(10)
                .build();
        
        EmbeddingSearchResult<TextSegment> results = embeddingStore.search(request);
        
        // Should only return documents with category = "programming"
        assertThat(results.matches()).hasSize(2);
        assertThat(results.matches()).extracting(m -> m.embedded().metadata().toMap().get("category"))
                .containsOnly("programming");
    }
}

