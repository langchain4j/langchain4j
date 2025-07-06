package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.embedding.SparseEmbedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchMode;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import io.milvus.v2.common.ConsistencyLevel;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

/**
 * This test demonstrates the concept of BGE-M3 model integration for hybrid search,
 * similar to the example in the Milvus documentation.
 *
 * Note: This is a conceptual test that simulates the BGE-M3 model behavior
 * using the existing embedding model for dense vectors and manually created sparse vectors.
 * In a real implementation, you would use the actual BGE-M3 model that can generate both
 * dense and sparse embeddings simultaneously.
 *
 * Reference: https://milvus.io/docs/hybrid_search_with_milvus.md
 */
@Testcontainers
class MilvusBGE3ModelIntegrationTest implements WithAssertions {

    @Container
    private static final MilvusContainer milvus = new MilvusContainer("milvusdb/milvus:v2.5.8");

    private MilvusEmbeddingStore embeddingStore;
    private EmbeddingModel embeddingModel;
    private String collectionName;

    // Sample questions similar to the Quora dataset in the docs
    private static final List<String> SAMPLE_QUESTIONS = Arrays.asList(
            "What is the strongest Kevlar cord?",
            "How to start learning programming?",
            "What's the best way to start learning robotics?",
            "How do I learn a computer language like java?",
            "How can I get started to learn information security?",
            "What is Java programming? How To Learn Java Programming Language?",
            "How can I learn computer security?",
            "What is the best way to start robotics? Which is the best development board?",
            "How can I learn to speak English fluently?",
            "What are the best ways to learn French?",
            "How can you make physics easy to learn?",
            "How do we prepare for UPSC?",
            "What is the alternative to machine learning?",
            "How do I create a new Terminal and new shell in Linux using C programming?",
            "Which business is better to start in Hyderabad?",
            "What math does a complete newbie need to understand algorithms for computer programming?");

    @BeforeEach
    void setUp() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        collectionName = "bge3_demo_test_" + System.currentTimeMillis();

        embeddingStore = MilvusEmbeddingStore.builder()
                .uri(milvus.getEndpoint())
                .collectionName(collectionName)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .username(System.getenv("MILVUS_USERNAME"))
                .password(System.getenv("MILVUS_PASSWORD"))
                .dimension(384)
                .retrieveEmbeddingsOnSearch(true)
                .idFieldName("id_field")
                .textFieldName("text_field")
                .metadataFieldName("metadata_field")
                .vectorFieldName("vector_field")
                .sparseVectorFieldName("sparse_vector_field")
                .build();
    }

    @AfterEach
    void tearDown() {
        if (embeddingStore != null && collectionName != null) {
            try {
                embeddingStore.dropCollection(collectionName);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Simulates the BGE-M3 model behavior by generating both dense and sparse embeddings.
     * In a real implementation, this would be done by the actual BGE-M3 model.
     */
    private static class BGE3ModelSimulator {
        private final EmbeddingModel denseModel;

        public BGE3ModelSimulator(EmbeddingModel denseModel) {
            this.denseModel = denseModel;
        }

        /**
         * Simulates generating both dense and sparse embeddings for a text.
         * In reality, BGE-M3 would generate both simultaneously.
         */
        public EmbeddingResult generateEmbeddings(String text) {
            // Generate dense embedding using the existing model
            Embedding denseEmbedding = denseModel.embed(text).content();

            // Simulate sparse embedding generation based on text characteristics
            SparseEmbedding sparseEmbedding = generateSparseEmbedding(text);

            return new EmbeddingResult(denseEmbedding, sparseEmbedding);
        }

        /**
         * Simulates sparse embedding generation based on text content.
         * In reality, BGE-M3 would generate this based on learned sparse representations.
         */
        private SparseEmbedding generateSparseEmbedding(String text) {
            String lowerText = text.toLowerCase();

            // Create sparse indices and values based on text characteristics
            // This is a simplified simulation - real BGE-M3 would use learned sparse representations
            if (lowerText.contains("programming") || lowerText.contains("java") || lowerText.contains("computer")) {
                return new SparseEmbedding(Arrays.asList(1L, 3L, 5L, 7L), Arrays.asList(0.8f, 0.6f, 0.4f, 0.2f));
            } else if (lowerText.contains("robotics") || lowerText.contains("robot")) {
                return new SparseEmbedding(Arrays.asList(2L, 4L, 6L, 8L), Arrays.asList(0.7f, 0.5f, 0.3f, 0.1f));
            } else if (lowerText.contains("security") || lowerText.contains("information")) {
                return new SparseEmbedding(Arrays.asList(3L, 5L, 7L, 9L), Arrays.asList(0.6f, 0.4f, 0.2f, 0.8f));
            } else if (lowerText.contains("language") || lowerText.contains("speak") || lowerText.contains("french")) {
                return new SparseEmbedding(Arrays.asList(4L, 6L, 8L, 10L), Arrays.asList(0.5f, 0.3f, 0.1f, 0.7f));
            } else {
                // Default sparse embedding for other topics
                return new SparseEmbedding(Arrays.asList(1L, 2L, 3L, 4L), Arrays.asList(0.3f, 0.3f, 0.2f, 0.2f));
            }
        }
    }

    private static class EmbeddingResult {
        private final Embedding denseEmbedding;
        private final SparseEmbedding sparseEmbedding;

        public EmbeddingResult(Embedding denseEmbedding, SparseEmbedding sparseEmbedding) {
            this.denseEmbedding = denseEmbedding;
            this.sparseEmbedding = sparseEmbedding;
        }

        public Embedding getDenseEmbedding() {
            return denseEmbedding;
        }

        public SparseEmbedding getSparseEmbedding() {
            return sparseEmbedding;
        }
    }

    @Test
    void should_demonstrate_bge3_model_integration_concept() {
        // given - simulate BGE-M3 model
        BGE3ModelSimulator bge3Model = new BGE3ModelSimulator(embeddingModel);

        // Generate embeddings for sample questions (similar to the docs example)
        List<EmbeddingResult> embeddingResults = SAMPLE_QUESTIONS.stream()
                .limit(10)
                .map(bge3Model::generateEmbeddings)
                .toList();

        // Extract dense and sparse embeddings
        List<Embedding> denseEmbeddings = embeddingResults.stream()
                .map(EmbeddingResult::getDenseEmbedding)
                .toList();

        List<SparseEmbedding> sparseEmbeddings = embeddingResults.stream()
                .map(EmbeddingResult::getSparseEmbedding)
                .toList();

        List<TextSegment> textSegments =
                SAMPLE_QUESTIONS.stream().limit(10).map(TextSegment::from).toList();

        // Insert data into Milvus (similar to the docs example)
        embeddingStore.addAllHybrid(
                Arrays.asList("doc1", "doc2", "doc3", "doc4", "doc5", "doc6", "doc7", "doc8", "doc9", "doc10"),
                denseEmbeddings,
                sparseEmbeddings,
                textSegments);

        // when - perform search with query
        String query = "How to start learning programming?";
        EmbeddingResult queryEmbeddings = bge3Model.generateEmbeddings(query);

        // Perform hybrid search (similar to the docs example)
        EmbeddingSearchRequest hybridSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbeddings.getDenseEmbedding())
                .sparseEmbedding(queryEmbeddings.getSparseEmbedding())
                .searchMode(EmbeddingSearchMode.HYBRID) // hybrid search
                .maxResults(5)
                .build();

        EmbeddingSearchResult<TextSegment> hybridResults = embeddingStore.search(hybridSearchRequest);

        // then
        assertThat(hybridResults.matches()).hasSize(5);
        assertThat(hybridResults.matches().get(0).score()).isGreaterThan(0);

        // Verify that programming-related documents are found
        List<String> resultTexts = hybridResults.matches().stream()
                .map(match -> match.embedded().text())
                .toList();

        assertThat(resultTexts)
                .anyMatch(text -> text.toLowerCase().contains("programming")
                        || text.toLowerCase().contains("java")
                        || text.toLowerCase().contains("computer"));
    }

    @Test
    void should_demonstrate_dense_vs_sparse_vs_hybrid_search_comparison() {
        // given - simulate BGE-M3 model
        BGE3ModelSimulator bge3Model = new BGE3ModelSimulator(embeddingModel);

        // Generate embeddings for a subset of questions
        List<EmbeddingResult> embeddingResults = SAMPLE_QUESTIONS.stream()
                .limit(8)
                .map(bge3Model::generateEmbeddings)
                .toList();

        List<Embedding> denseEmbeddings = embeddingResults.stream()
                .map(EmbeddingResult::getDenseEmbedding)
                .toList();

        List<SparseEmbedding> sparseEmbeddings = embeddingResults.stream()
                .map(EmbeddingResult::getSparseEmbedding)
                .toList();

        List<TextSegment> textSegments =
                SAMPLE_QUESTIONS.stream().limit(8).map(TextSegment::from).toList();

        embeddingStore.addAllHybrid(
                Arrays.asList("doc1", "doc2", "doc3", "doc4", "doc5", "doc6", "doc7", "doc8"),
                denseEmbeddings,
                sparseEmbeddings,
                textSegments);

        // when - perform all three types of searches
        String query = "How to start learning programming?";
        EmbeddingResult queryEmbeddings = bge3Model.generateEmbeddings(query);

        // Dense search
        EmbeddingSearchRequest denseSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbeddings.getDenseEmbedding())
                .searchMode(EmbeddingSearchMode.DENSE) // dense search
                .maxResults(5)
                .build();
        EmbeddingSearchResult<TextSegment> denseResults = embeddingStore.search(denseSearchRequest);

        // Sparse search
        EmbeddingSearchRequest sparseSearchRequest = EmbeddingSearchRequest.builder()
                .sparseEmbedding(queryEmbeddings.getSparseEmbedding())
                .searchMode(EmbeddingSearchMode.SPARSE) // sparse search
                .maxResults(5)
                .build();
        EmbeddingSearchResult<TextSegment> sparseResults = embeddingStore.search(sparseSearchRequest);

        // Hybrid search
        EmbeddingSearchRequest hybridSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbeddings.getDenseEmbedding())
                .sparseEmbedding(queryEmbeddings.getSparseEmbedding())
                .searchMode(EmbeddingSearchMode.HYBRID) // hybrid search
                .maxResults(5)
                .build();
        EmbeddingSearchResult<TextSegment> hybridResults = embeddingStore.search(hybridSearchRequest);

        // then - verify all searches return results
        assertThat(denseResults.matches()).hasSize(5);
        assertThat(sparseResults.matches()).hasSize(5);
        assertThat(hybridResults.matches()).hasSize(5);

        // Verify scores are positive
        assertThat(denseResults.matches().get(0).score()).isGreaterThan(0);
        assertThat(sparseResults.matches().get(0).score()).isGreaterThan(0);
        assertThat(hybridResults.matches().get(0).score()).isGreaterThan(0);

        // Verify that programming-related documents are found in dense search
        List<String> denseResultTexts = denseResults.matches().stream()
                .map(match -> match.embedded().text())
                .toList();
        assertThat(denseResultTexts)
                .anyMatch(text -> text.toLowerCase().contains("programming")
                        || text.toLowerCase().contains("java")
                        || text.toLowerCase().contains("computer"));
    }

    @Test
    void should_demonstrate_batch_embedding_generation() {
        // given - simulate BGE-M3 model for batch processing
        BGE3ModelSimulator bge3Model = new BGE3ModelSimulator(embeddingModel);

        // Generate embeddings for all sample questions in batch
        List<EmbeddingResult> allEmbeddingResults =
                SAMPLE_QUESTIONS.stream().map(bge3Model::generateEmbeddings).toList();

        List<Embedding> allDenseEmbeddings = allEmbeddingResults.stream()
                .map(EmbeddingResult::getDenseEmbedding)
                .toList();

        List<SparseEmbedding> allSparseEmbeddings = allEmbeddingResults.stream()
                .map(EmbeddingResult::getSparseEmbedding)
                .toList();

        List<TextSegment> allTextSegments =
                SAMPLE_QUESTIONS.stream().map(TextSegment::from).toList();

        // Insert all data in batch (similar to the docs example with 50 records per batch)
        embeddingStore.addAllHybrid(
                Arrays.asList(
                        "doc1", "doc2", "doc3", "doc4", "doc5", "doc6", "doc7", "doc8", "doc9", "doc10", "doc11",
                        "doc12", "doc13", "doc14", "doc15", "doc16"),
                allDenseEmbeddings,
                allSparseEmbeddings,
                allTextSegments);

        // when - perform search on the complete dataset
        String query = "How to start learning programming?";
        EmbeddingResult queryEmbeddings = bge3Model.generateEmbeddings(query);

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbeddings.getDenseEmbedding())
                .sparseEmbedding(queryEmbeddings.getSparseEmbedding())
                .searchMode(EmbeddingSearchMode.HYBRID) // hybrid search
                .maxResults(10)
                .build();

        EmbeddingSearchResult<TextSegment> results = embeddingStore.search(searchRequest);

        // then
        assertThat(results.matches()).hasSize(10);
        assertThat(results.matches().get(0).score()).isGreaterThan(0);

        // Verify that programming-related documents are found
        List<String> resultTexts =
                results.matches().stream().map(match -> match.embedded().text()).toList();

        assertThat(resultTexts)
                .anyMatch(text -> text.toLowerCase().contains("programming")
                        || text.toLowerCase().contains("java")
                        || text.toLowerCase().contains("computer"));
    }
}
