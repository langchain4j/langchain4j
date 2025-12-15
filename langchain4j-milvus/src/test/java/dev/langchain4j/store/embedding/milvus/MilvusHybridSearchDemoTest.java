package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import io.milvus.v2.common.ConsistencyLevel;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

/**
 * This test class demonstrates hybrid search functionality based on the Milvus documentation.
 * It shows how to perform dense, sparse, and hybrid searches similar to the examples in:
 * https://milvus.io/docs/hybrid_search_with_milvus.md
 */
@Testcontainers
class MilvusHybridSearchDemoTest implements WithAssertions {

    @Container
    private static final MilvusContainer milvus = new MilvusContainer("milvusdb/milvus:v2.5.8");

    private MilvusEmbeddingStore embeddingStore;
    private EmbeddingModel embeddingModel;
    private String collectionName;

    // Sample documents similar to the Quora dataset used in the docs
    private static final List<String> SAMPLE_DOCUMENTS = Arrays.asList(
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
        collectionName = "demo_test_" + System.currentTimeMillis();

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
                .sparseMode(MilvusEmbeddingStore.MilvusSparseMode.CUSTOM)
                .build();
    }

    @Test
    void should_perform_dense_search_like_docs_example() {
        // given - similar to the dense_search function in the docs
        String query = "How to start learning programming?";
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // Add documents with dense embeddings
        List<Embedding> denseEmbeddings = Arrays.asList(
                embeddingModel
                        .embed("What's the best way to start learning robotics?")
                        .content(),
                embeddingModel
                        .embed("How do I learn a computer language like java?")
                        .content(),
                embeddingModel
                        .embed("How can I get started to learn information security?")
                        .content(),
                embeddingModel
                        .embed("What is Java programming? How To Learn Java Programming Language?")
                        .content(),
                embeddingModel.embed("How can I learn computer security?").content());

        List<TextSegment> textSegments = Arrays.asList(
                TextSegment.from("What's the best way to start learning robotics?"),
                TextSegment.from("How do I learn a computer language like java?"),
                TextSegment.from("How can I get started to learn information security?"),
                TextSegment.from("What is Java programming? How To Learn Java Programming Language?"),
                TextSegment.from("How can I learn computer security?"));

        embeddingStore.addAll(denseEmbeddings, textSegments);

        // when - dense search (searchMode = 0)
        MilvusEmbeddingSearchRequest denseSearchRequest = MilvusEmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(queryEmbedding)
                .searchMode(MilvusEmbeddingSearchMode.DENSE) // dense search
                .maxResults(5)
                .build();

        EmbeddingSearchResult<TextSegment> denseResults = embeddingStore.search(denseSearchRequest);

        // then
        assertThat(denseResults.matches()).hasSize(5);
        assertThat(denseResults.matches().get(0).score()).isGreaterThan(0);

        // Verify that programming-related documents are found
        List<String> resultTexts = denseResults.matches().stream()
                .map(match -> match.embedded().text())
                .toList();

        assertThat(resultTexts)
                .anyMatch(text -> text.toLowerCase().contains("programming")
                        || text.toLowerCase().contains("java")
                        || text.toLowerCase().contains("computer"));
    }

    @Test
    void should_perform_sparse_search_like_docs_example() {
        // given - similar to the sparse_search function in the docs
        SparseEmbedding querySparseEmbedding =
                new SparseEmbedding(new long[]{1L, 3L, 5L, 7L, 9L}, new float[]{0.1f, 0.3f, 0.5f, 0.7f, 0.9f});

        // Add documents with sparse embeddings
        List<SparseEmbedding> sparseEmbeddings = Arrays.asList(
                new SparseEmbedding(new long[]{1L, 3L}, new float[]{0.1f, 0.3f}),
                new SparseEmbedding(new long[]{5L, 7L}, new float[]{0.5f, 0.7f}),
                new SparseEmbedding(new long[]{2L, 4L}, new float[]{0.2f, 0.4f}),
                new SparseEmbedding(new long[]{6L, 8L}, new float[]{0.6f, 0.8f}),
                new SparseEmbedding(new long[]{1L, 5L}, new float[]{0.1f, 0.5f}));

        List<TextSegment> textSegments = Arrays.asList(
                TextSegment.from("What is Java programming? How To Learn Java Programming Language?"),
                TextSegment.from("What's the best way to start learning robotics?"),
                TextSegment.from("What is the alternative to machine learning?"),
                TextSegment.from("How do I create a new Terminal and new shell in Linux using C programming?"),
                TextSegment.from("Which business is better to start in Hyderabad?"));

        embeddingStore.addAllSparse(
                Arrays.asList("sparse1", "sparse2", "sparse3", "sparse4", "sparse5"), sparseEmbeddings, textSegments);

        // when - sparse search (searchMode = 1)
        MilvusEmbeddingSearchRequest sparseSearchRequest = MilvusEmbeddingSearchRequest.milvusBuilder()
                .sparseEmbedding(querySparseEmbedding)
                .searchMode(MilvusEmbeddingSearchMode.SPARSE) // sparse search
                .maxResults(5)
                .build();

        EmbeddingSearchResult<TextSegment> sparseResults = embeddingStore.search(sparseSearchRequest);

        // then
        assertThat(sparseResults.matches()).hasSize(3);
        assertThat(sparseResults.matches().get(0).score()).isGreaterThan(0);
    }

    @Test
    void should_perform_hybrid_search_like_docs_example() {
        // given - similar to the hybrid_search function in the docs
        String query = "How to start learning programming?";
        Embedding queryDenseEmbedding = embeddingModel.embed(query).content();
        SparseEmbedding querySparseEmbedding =
                new SparseEmbedding(new long[]{1L, 3L, 5L, 7L}, new float[]{0.1f, 0.3f, 0.5f, 0.7f});

        // Add documents with both dense and sparse embeddings
        List<Embedding> denseEmbeddings = Arrays.asList(
                embeddingModel
                        .embed("What is the best way to start robotics? Which is the best development board?")
                        .content(),
                embeddingModel
                        .embed("What is Java programming? How To Learn Java Programming Language?")
                        .content(),
                embeddingModel
                        .embed("What's the best way to start learning robotics?")
                        .content(),
                embeddingModel.embed("How do we prepare for UPSC?").content(),
                embeddingModel.embed("How can you make physics easy to learn?").content());

        List<SparseEmbedding> sparseEmbeddings = Arrays.asList(
                new SparseEmbedding(new long[]{1L, 3L}, new float[]{0.1f, 0.3f}),
                new SparseEmbedding(new long[]{5L, 7L}, new float[]{0.5f, 0.7f}),
                new SparseEmbedding(new long[]{2L, 4L}, new float[]{0.2f, 0.4f}),
                new SparseEmbedding(new long[]{6L, 8L}, new float[]{0.6f, 0.8f}),
                new SparseEmbedding(new long[]{1L, 5L}, new float[]{0.1f, 0.5f}));

        List<TextSegment> textSegments = Arrays.asList(
                TextSegment.from("What is the best way to start robotics? Which is the best development board?"),
                TextSegment.from("What is Java programming? How To Learn Java Programming Language?"),
                TextSegment.from("What's the best way to start learning robotics?"),
                TextSegment.from("How do we prepare for UPSC?"),
                TextSegment.from("How can you make physics easy to learn?"));

        embeddingStore.addAllHybrid(
                Arrays.asList("hybrid1", "hybrid2", "hybrid3", "hybrid4", "hybrid5"),
                denseEmbeddings,
                sparseEmbeddings,
                textSegments);

        // when - hybrid search (searchMode = 2)
        MilvusEmbeddingSearchRequest hybridSearchRequest = MilvusEmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(queryDenseEmbedding)
                .sparseEmbedding(querySparseEmbedding)
                .searchMode(MilvusEmbeddingSearchMode.HYBRID) // hybrid search
                .maxResults(5)
                .build();

        EmbeddingSearchResult<TextSegment> hybridResults = embeddingStore.search(hybridSearchRequest);

        // then
        assertThat(hybridResults.matches()).hasSize(5);
        assertThat(hybridResults.matches().get(0).score()).isGreaterThan(0);
    }

    @Test
    void should_compare_dense_sparse_and_hybrid_search_results() {
        // given - similar to the comparison in the docs
        String query = "How to start learning programming?";
        Embedding queryDenseEmbedding = embeddingModel.embed(query).content();
        SparseEmbedding querySparseEmbedding =
                new SparseEmbedding(new long[]{1L, 3L, 5L, 7L}, new float[]{0.1f, 0.3f, 0.5f, 0.7f});

        // Add a comprehensive set of documents
        List<Embedding> denseEmbeddings = Arrays.asList(
                embeddingModel
                        .embed("What is Java programming? How To Learn Java Programming Language?")
                        .content(),
                embeddingModel
                        .embed("What's the best way to start learning robotics?")
                        .content(),
                embeddingModel
                        .embed("How can I get started to learn information security?")
                        .content(),
                embeddingModel.embed("How can I learn computer security?").content(),
                embeddingModel
                        .embed("What is the best way to start robotics? Which is the best development board?")
                        .content(),
                embeddingModel
                        .embed("How can I learn to speak English fluently?")
                        .content(),
                embeddingModel.embed("What are the best ways to learn French?").content(),
                embeddingModel.embed("How can you make physics easy to learn?").content(),
                embeddingModel.embed("How do we prepare for UPSC?").content(),
                embeddingModel
                        .embed("What is the alternative to machine learning?")
                        .content());

        List<SparseEmbedding> sparseEmbeddings = Arrays.asList(
                new SparseEmbedding(new long[]{1L, 3L}, new float[]{0.1f, 0.3f}),
                new SparseEmbedding(new long[]{5L, 7L}, new float[]{0.5f, 0.7f}),
                new SparseEmbedding(new long[]{2L, 4L}, new float[]{0.2f, 0.4f}),
                new SparseEmbedding(new long[]{6L, 8L}, new float[]{0.6f, 0.8f}),
                new SparseEmbedding(new long[]{1L, 5L}, new float[]{0.1f, 0.5f}),
                new SparseEmbedding(new long[]{3L, 7L}, new float[]{0.3f, 0.7f}),
                new SparseEmbedding(new long[]{2L, 6L}, new float[]{0.2f, 0.6f}),
                new SparseEmbedding(new long[]{4L, 8L}, new float[]{0.4f, 0.8f}),
                new SparseEmbedding(new long[]{1L, 7L}, new float[]{0.1f, 0.7f}),
                new SparseEmbedding(new long[]{3L, 5L}, new float[]{0.3f, 0.5f}));

        List<TextSegment> textSegments =
                SAMPLE_DOCUMENTS.stream().limit(10).map(TextSegment::from).toList();

        embeddingStore.addAllHybrid(
                Arrays.asList("doc1", "doc2", "doc3", "doc4", "doc5", "doc6", "doc7", "doc8", "doc9", "doc10"),
                denseEmbeddings,
                sparseEmbeddings,
                textSegments);

        // when - perform all three types of searches
        MilvusEmbeddingSearchRequest denseSearchRequest = MilvusEmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(queryDenseEmbedding)
                .searchMode(MilvusEmbeddingSearchMode.DENSE) // dense search
                .maxResults(10)
                .build();
        EmbeddingSearchResult<TextSegment> denseResults = embeddingStore.search(denseSearchRequest);

        MilvusEmbeddingSearchRequest sparseSearchRequest = MilvusEmbeddingSearchRequest.milvusBuilder()
                .sparseEmbedding(querySparseEmbedding)
                .searchMode(MilvusEmbeddingSearchMode.SPARSE) // sparse search
                .maxResults(10)
                .build();
        EmbeddingSearchResult<TextSegment> sparseResults = embeddingStore.search(sparseSearchRequest);

        MilvusEmbeddingSearchRequest hybridSearchRequest = MilvusEmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(queryDenseEmbedding)
                .sparseEmbedding(querySparseEmbedding)
                .searchMode(MilvusEmbeddingSearchMode.HYBRID) // hybrid search
                .maxResults(10)
                .build();
        EmbeddingSearchResult<TextSegment> hybridResults = embeddingStore.search(hybridSearchRequest);

        // then - verify all searches return results
        assertThat(denseResults.matches()).hasSize(10);
        assertThat(sparseResults.matches()).hasSize(6);
        assertThat(hybridResults.matches()).hasSize(10);

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
    void should_handle_empty_sparse_embedding_in_hybrid_search() {
        // given
        String query = "How to start learning programming?";
        Embedding queryDenseEmbedding = embeddingModel.embed(query).content();
        SparseEmbedding emptySparseEmbedding = new SparseEmbedding(new long[]{}, new float[]{});

        // Add a document with both dense and empty sparse embedding
        Embedding denseEmbedding =
                embeddingModel.embed("What is Java programming?").content();
        SparseEmbedding sparseEmbedding = new SparseEmbedding(new long[]{1L, 3L}, new float[]{0.1f, 0.3f});

        embeddingStore.addAllHybrid(
                Arrays.asList("test_id"),
                Arrays.asList(denseEmbedding),
                Arrays.asList(sparseEmbedding),
                Arrays.asList(TextSegment.from("What is Java programming?")));

        // when - hybrid search with empty sparse embedding
        MilvusEmbeddingSearchRequest hybridSearchRequest = MilvusEmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(queryDenseEmbedding)
                .sparseEmbedding(emptySparseEmbedding)
                .searchMode(MilvusEmbeddingSearchMode.HYBRID) // hybrid search
                .maxResults(5)
                .build();

        EmbeddingSearchResult<TextSegment> hybridResults = embeddingStore.search(hybridSearchRequest);

        // then
        assertThat(hybridResults.matches()).hasSize(1);
        assertThat(hybridResults.matches().get(0).score()).isGreaterThan(0);
    }

    @Test
    void should_demonstrate_search_result_ranking() {
        // given - documents with varying relevance to programming
        String query = "How to start learning programming?";
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        List<Embedding> denseEmbeddings = Arrays.asList(
                embeddingModel.embed("How to start learning programming?").content(), // Most relevant
                embeddingModel
                        .embed("What is Java programming? How To Learn Java Programming Language?")
                        .content(), // Very relevant
                embeddingModel
                        .embed("How do I learn a computer language like java?")
                        .content(), // Relevant
                embeddingModel.embed("How can I learn computer security?").content(), // Somewhat relevant
                embeddingModel
                        .embed("How can I learn to speak English fluently?")
                        .content() // Less relevant
                );

        List<TextSegment> textSegments = Arrays.asList(
                TextSegment.from("How to start learning programming?"),
                TextSegment.from("What is Java programming? How To Learn Java Programming Language?"),
                TextSegment.from("How do I learn a computer language like java?"),
                TextSegment.from("How can I learn computer security?"),
                TextSegment.from("How can I learn to speak English fluently?"));

        embeddingStore.addAll(denseEmbeddings, textSegments);

        // when
        MilvusEmbeddingSearchRequest searchRequest = MilvusEmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(queryEmbedding)
                .searchMode(MilvusEmbeddingSearchMode.DENSE) // dense search
                .maxResults(5)
                .build();

        EmbeddingSearchResult<TextSegment> results = embeddingStore.search(searchRequest);

        // then - verify ranking (most relevant should have highest score)
        assertThat(results.matches()).hasSize(5);

        // The first result should be the most relevant
        assertThat(results.matches().get(0).embedded().text()).isEqualTo("How to start learning programming?");

        // Scores should be in descending order
        double firstScore = results.matches().get(0).score();
        double secondScore = results.matches().get(1).score();
        assertThat(firstScore).isGreaterThanOrEqualTo(secondScore);
    }
}
