package dev.langchain4j.store.embedding.milvus.v2;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
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
class MilvusV2HybridSearchDemoTest implements WithAssertions {

    @Container
    private static final MilvusContainer milvus = new MilvusContainer("milvusdb/milvus:v2.6.11")
            .withEnv("DEPLOY_MODE", "STANDALONE")
            .withEnv("MILVUS_MODE", "standalone")
            .withEnv("ETCD_USE_EMBED", "true")
            .withEnv("COMMON_STORAGETYPE", "local")
            .withCommand("milvus", "run", "standalone");

    private MilvusV2EmbeddingStore embeddingStore;
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

        // Default store uses HYBRID mode to support all search types in tests
        embeddingStore = MilvusV2EmbeddingStore.builder()
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
                .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.CUSTOM)
                .searchMode(MilvusV2EmbeddingStore.SearchMode.HYBRID)
                .build();
    }

    @Test
    void should_perform_vector_search_like_docs_example() {
        // given - similar to the dense_search function in the docs
        String query = "How to start learning programming?";
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // Add documents with dense embeddings (also need sparse for HYBRID store)
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

        List<SparseEmbedding> sparseEmbeddings = Arrays.asList(
                new SparseEmbedding(new long[] {1L, 3L}, new float[] {0.1f, 0.3f}),
                new SparseEmbedding(new long[] {5L, 7L}, new float[] {0.5f, 0.7f}),
                new SparseEmbedding(new long[] {2L, 4L}, new float[] {0.2f, 0.4f}),
                new SparseEmbedding(new long[] {6L, 8L}, new float[] {0.6f, 0.8f}),
                new SparseEmbedding(new long[] {1L, 5L}, new float[] {0.1f, 0.5f}));

        List<TextSegment> textSegments = Arrays.asList(
                TextSegment.from("What's the best way to start learning robotics?"),
                TextSegment.from("How do I learn a computer language like java?"),
                TextSegment.from("How can I get started to learn information security?"),
                TextSegment.from("What is Java programming? How To Learn Java Programming Language?"),
                TextSegment.from("How can I learn computer security?"));

        embeddingStore.addAllHybrid(
                Arrays.asList("v1", "v2", "v3", "v4", "v5"), denseEmbeddings, sparseEmbeddings, textSegments);

        // Use a VECTOR store for pure vector search
        MilvusV2EmbeddingStore vectorStore = MilvusV2EmbeddingStore.builder()
                .uri(milvus.getEndpoint())
                .collectionName(collectionName)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .dimension(384)
                .retrieveEmbeddingsOnSearch(true)
                .idFieldName("id_field")
                .textFieldName("text_field")
                .metadataFieldName("metadata_field")
                .vectorFieldName("vector_field")
                .sparseVectorFieldName("sparse_vector_field")
                .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.CUSTOM)
                .searchMode(MilvusV2EmbeddingStore.SearchMode.VECTOR)
                .build();

        // when - vector search
        MilvusV2EmbeddingSearchRequest vectorSearchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .build();

        EmbeddingSearchResult<TextSegment> vectorResults = vectorStore.search(vectorSearchRequest);

        // then
        assertThat(vectorResults.matches()).hasSize(5);
        assertThat(vectorResults.matches().get(0).score()).isGreaterThan(0);

        List<String> resultTexts = vectorResults.matches().stream()
                .map(match -> match.embedded().text())
                .toList();

        assertThat(resultTexts)
                .anyMatch(text -> text.toLowerCase().contains("programming")
                        || text.toLowerCase().contains("java")
                        || text.toLowerCase().contains("computer"));
    }

    @Test
    void should_perform_hybrid_search_like_docs_example() {
        // given - similar to the hybrid_search function in the docs
        String query = "How to start learning programming?";
        Embedding queryDenseEmbedding = embeddingModel.embed(query).content();
        SparseEmbedding querySparseEmbedding =
                new SparseEmbedding(new long[] {1L, 3L, 5L, 7L}, new float[] {0.1f, 0.3f, 0.5f, 0.7f});

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
                new SparseEmbedding(new long[] {1L, 3L}, new float[] {0.1f, 0.3f}),
                new SparseEmbedding(new long[] {5L, 7L}, new float[] {0.5f, 0.7f}),
                new SparseEmbedding(new long[] {2L, 4L}, new float[] {0.2f, 0.4f}),
                new SparseEmbedding(new long[] {6L, 8L}, new float[] {0.6f, 0.8f}),
                new SparseEmbedding(new long[] {1L, 5L}, new float[] {0.1f, 0.5f}));

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

        // when - hybrid search (store-level HYBRID)
        MilvusV2EmbeddingSearchRequest hybridSearchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(queryDenseEmbedding)
                .sparseEmbedding(querySparseEmbedding)
                .maxResults(5)
                .build();

        EmbeddingSearchResult<TextSegment> hybridResults = embeddingStore.search(hybridSearchRequest);

        // then
        assertThat(hybridResults.matches()).hasSize(5);
        assertThat(hybridResults.matches().get(0).score()).isGreaterThan(0);
    }

    @Test
    void should_compare_vector_and_hybrid_search_results() {
        // given
        String query = "How to start learning programming?";
        Embedding queryDenseEmbedding = embeddingModel.embed(query).content();
        SparseEmbedding querySparseEmbedding =
                new SparseEmbedding(new long[] {1L, 3L, 5L, 7L}, new float[] {0.1f, 0.3f, 0.5f, 0.7f});

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
                new SparseEmbedding(new long[] {1L, 3L}, new float[] {0.1f, 0.3f}),
                new SparseEmbedding(new long[] {5L, 7L}, new float[] {0.5f, 0.7f}),
                new SparseEmbedding(new long[] {2L, 4L}, new float[] {0.2f, 0.4f}),
                new SparseEmbedding(new long[] {6L, 8L}, new float[] {0.6f, 0.8f}),
                new SparseEmbedding(new long[] {1L, 5L}, new float[] {0.1f, 0.5f}),
                new SparseEmbedding(new long[] {3L, 7L}, new float[] {0.3f, 0.7f}),
                new SparseEmbedding(new long[] {2L, 6L}, new float[] {0.2f, 0.6f}),
                new SparseEmbedding(new long[] {4L, 8L}, new float[] {0.4f, 0.8f}),
                new SparseEmbedding(new long[] {1L, 7L}, new float[] {0.1f, 0.7f}),
                new SparseEmbedding(new long[] {3L, 5L}, new float[] {0.3f, 0.5f}));

        List<TextSegment> textSegments =
                SAMPLE_DOCUMENTS.stream().limit(10).map(TextSegment::from).toList();

        embeddingStore.addAllHybrid(
                Arrays.asList("doc1", "doc2", "doc3", "doc4", "doc5", "doc6", "doc7", "doc8", "doc9", "doc10"),
                denseEmbeddings,
                sparseEmbeddings,
                textSegments);

        // when - vector search (separate VECTOR store)
        MilvusV2EmbeddingStore vectorStore = MilvusV2EmbeddingStore.builder()
                .uri(milvus.getEndpoint())
                .collectionName(collectionName)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .dimension(384)
                .retrieveEmbeddingsOnSearch(true)
                .idFieldName("id_field")
                .textFieldName("text_field")
                .metadataFieldName("metadata_field")
                .vectorFieldName("vector_field")
                .sparseVectorFieldName("sparse_vector_field")
                .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.CUSTOM)
                .searchMode(MilvusV2EmbeddingStore.SearchMode.VECTOR)
                .build();

        MilvusV2EmbeddingSearchRequest vectorSearchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(queryDenseEmbedding)
                .maxResults(10)
                .build();
        EmbeddingSearchResult<TextSegment> vectorResults = vectorStore.search(vectorSearchRequest);

        // when - hybrid search
        MilvusV2EmbeddingSearchRequest hybridSearchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(queryDenseEmbedding)
                .sparseEmbedding(querySparseEmbedding)
                .maxResults(10)
                .build();
        EmbeddingSearchResult<TextSegment> hybridResults = embeddingStore.search(hybridSearchRequest);

        // then
        assertThat(vectorResults.matches()).hasSize(10);
        assertThat(hybridResults.matches()).hasSize(10);
        assertThat(vectorResults.matches().get(0).score()).isGreaterThan(0);
        assertThat(hybridResults.matches().get(0).score()).isGreaterThan(0);

        List<String> vectorResultTexts = vectorResults.matches().stream()
                .map(match -> match.embedded().text())
                .toList();
        assertThat(vectorResultTexts)
                .anyMatch(text -> text.toLowerCase().contains("programming")
                        || text.toLowerCase().contains("java")
                        || text.toLowerCase().contains("computer"));
    }

    @Test
    void should_handle_empty_sparse_embedding_in_hybrid_search() {
        // given
        String query = "How to start learning programming?";
        Embedding queryDenseEmbedding = embeddingModel.embed(query).content();
        SparseEmbedding emptySparseEmbedding = new SparseEmbedding(new long[] {}, new float[] {});

        embeddingStore.addAllHybrid(
                Arrays.asList("test_id"),
                Arrays.asList(embeddingModel.embed("What is Java programming?").content()),
                Arrays.asList(new SparseEmbedding(new long[] {1L, 3L}, new float[] {0.1f, 0.3f})),
                Arrays.asList(TextSegment.from("What is Java programming?")));

        // when - hybrid search with empty sparse embedding
        MilvusV2EmbeddingSearchRequest hybridSearchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(queryDenseEmbedding)
                .sparseEmbedding(emptySparseEmbedding)
                .maxResults(5)
                .build();

        EmbeddingSearchResult<TextSegment> hybridResults = embeddingStore.search(hybridSearchRequest);

        // then
        assertThat(hybridResults.matches()).hasSize(1);
        assertThat(hybridResults.matches().get(0).score()).isGreaterThan(0);
    }

    @Test
    void should_demonstrate_search_result_ranking() {
        // given
        String query = "How to start learning programming?";
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        List<Embedding> denseEmbeddings = Arrays.asList(
                embeddingModel.embed("How to start learning programming?").content(),
                embeddingModel
                        .embed("What is Java programming? How To Learn Java Programming Language?")
                        .content(),
                embeddingModel
                        .embed("How do I learn a computer language like java?")
                        .content(),
                embeddingModel.embed("How can I learn computer security?").content(),
                embeddingModel
                        .embed("How can I learn to speak English fluently?")
                        .content());

        List<SparseEmbedding> sparseEmbeddings = Arrays.asList(
                new SparseEmbedding(new long[] {1L, 3L}, new float[] {0.1f, 0.3f}),
                new SparseEmbedding(new long[] {5L, 7L}, new float[] {0.5f, 0.7f}),
                new SparseEmbedding(new long[] {2L, 4L}, new float[] {0.2f, 0.4f}),
                new SparseEmbedding(new long[] {6L, 8L}, new float[] {0.6f, 0.8f}),
                new SparseEmbedding(new long[] {1L, 5L}, new float[] {0.1f, 0.5f}));

        List<TextSegment> textSegments = Arrays.asList(
                TextSegment.from("How to start learning programming?"),
                TextSegment.from("What is Java programming? How To Learn Java Programming Language?"),
                TextSegment.from("How do I learn a computer language like java?"),
                TextSegment.from("How can I learn computer security?"),
                TextSegment.from("How can I learn to speak English fluently?"));

        embeddingStore.addAllHybrid(
                Arrays.asList("r1", "r2", "r3", "r4", "r5"), denseEmbeddings, sparseEmbeddings, textSegments);

        // Use a VECTOR store for pure vector ranking test
        MilvusV2EmbeddingStore vectorStore = MilvusV2EmbeddingStore.builder()
                .uri(milvus.getEndpoint())
                .collectionName(collectionName)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .dimension(384)
                .retrieveEmbeddingsOnSearch(true)
                .idFieldName("id_field")
                .textFieldName("text_field")
                .metadataFieldName("metadata_field")
                .vectorFieldName("vector_field")
                .sparseVectorFieldName("sparse_vector_field")
                .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.CUSTOM)
                .searchMode(MilvusV2EmbeddingStore.SearchMode.VECTOR)
                .build();

        // when
        MilvusV2EmbeddingSearchRequest searchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .build();

        EmbeddingSearchResult<TextSegment> results = vectorStore.search(searchRequest);

        // then
        assertThat(results.matches()).hasSize(5);
        assertThat(results.matches().get(0).embedded().text()).isEqualTo("How to start learning programming?");

        double firstScore = results.matches().get(0).score();
        double secondScore = results.matches().get(1).score();
        assertThat(firstScore).isGreaterThanOrEqualTo(secondScore);
    }
}
