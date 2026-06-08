package dev.langchain4j.store.embedding.milvus.v2;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

@Testcontainers
class MilvusV2EmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    private static final String COLLECTION_NAME = "test_collection";

    @Container
    private static final MilvusContainer milvus = new MilvusContainer("milvusdb/milvus:v2.6.11")
            .withEnv("DEPLOY_MODE", "STANDALONE")
            .withEnv("MILVUS_MODE", "standalone")
            .withEnv("ETCD_USE_EMBED", "true")
            .withEnv("COMMON_STORAGETYPE", "local")
            .withCommand("milvus", "run", "standalone");

    MilvusV2EmbeddingStore embeddingStore = MilvusV2EmbeddingStore.builder()
            .uri(milvus.getEndpoint())
            .collectionName(COLLECTION_NAME)
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
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @AfterEach
    void afterEach() {
        embeddingStore.dropCollection(COLLECTION_NAME);
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected List<EmbeddingMatch<TextSegment>> getAllEmbeddings() {
        MilvusV2EmbeddingSearchRequest searchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(embeddingModel().embed("test").content())
                .maxResults(1000)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        return searchResult.matches();
    }

    @Test
    void should_not_retrieve_embeddings_when_searching() {

        EmbeddingStore<TextSegment> embeddingStore = MilvusV2EmbeddingStore.builder()
                .host(milvus.getHost())
                .port(milvus.getMappedPort(19530))
                .collectionName(COLLECTION_NAME)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .dimension(384)
                .retrieveEmbeddingsOnSearch(false)
                .idFieldName("id_field")
                .textFieldName("text_field")
                .metadataFieldName("metadata_field")
                .vectorFieldName("vector_field")
                .sparseVectorFieldName("sparse_vector_field")
                .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.CUSTOM)
                .build();

        Embedding firstEmbedding = embeddingModel.embed("hello").content();
        Embedding secondEmbedding = embeddingModel.embed("hi").content();
        embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(firstEmbedding)
                        .maxResults(10)
                        .build())
                .matches();
        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).embedding()).isNull();
        assertThat(matches.get(1).embedding()).isNull();
    }

    @Test
    void milvus_with_existing_client() {
        String username = System.getenv("MILVUS_USERNAME");
        String password = System.getenv("MILVUS_PASSWORD");

        ConnectConfig connectConfig;
        if (username != null && !username.isBlank()) {
            connectConfig = ConnectConfig.builder()
                    .uri(milvus.getEndpoint())
                    .username(username)
                    .password(password)
                    .build();
        } else {
            connectConfig = ConnectConfig.builder().uri(milvus.getEndpoint()).build();
        }

        MilvusClientV2 milvusClientV2 = new MilvusClientV2(connectConfig);

        EmbeddingStore<TextSegment> embeddingStore = MilvusV2EmbeddingStore.builder()
                .milvusClient(milvusClientV2)
                .collectionName(COLLECTION_NAME)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .dimension(384)
                .retrieveEmbeddingsOnSearch(false)
                .idFieldName("id_field")
                .textFieldName("text_field")
                .metadataFieldName("metadata_field")
                .vectorFieldName("vector_field")
                .sparseVectorFieldName("sparse_vector_field")
                .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.CUSTOM)
                .build();

        Embedding firstEmbedding = embeddingModel.embed("hello").content();
        Embedding secondEmbedding = embeddingModel.embed("hi").content();
        embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(firstEmbedding)
                        .maxResults(10)
                        .build())
                .matches();
        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).embedding()).isNull();
        assertThat(matches.get(1).embedding()).isNull();
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }

    @Test
    void should_perform_hybrid_search() {
        // given
        MilvusV2EmbeddingStore hybridStore = MilvusV2EmbeddingStore.builder()
                .uri(milvus.getEndpoint())
                .collectionName("hybrid_test_collection")
                .consistencyLevel(ConsistencyLevel.STRONG)
                .dimension(384)
                .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.CUSTOM)
                .searchMode(MilvusV2EmbeddingStore.SearchMode.HYBRID)
                .build();

        Embedding denseEmbedding1 = embeddingModel.embed("technology document").content();
        Embedding denseEmbedding2 = embeddingModel.embed("science document").content();

        SparseEmbedding sparseEmbedding1 = new SparseEmbedding(new long[] {1L, 3L, 5L}, new float[] {0.1f, 0.3f, 0.5f});
        SparseEmbedding sparseEmbedding2 = new SparseEmbedding(new long[] {2L, 4L, 6L}, new float[] {0.2f, 0.4f, 0.6f});

        TextSegment textSegment1 = TextSegment.from("document about technology");
        TextSegment textSegment2 = TextSegment.from("document about science");

        hybridStore.addAllHybrid(
                Arrays.asList("id1", "id2"),
                Arrays.asList(denseEmbedding1, denseEmbedding2),
                Arrays.asList(sparseEmbedding1, sparseEmbedding2),
                Arrays.asList(textSegment1, textSegment2));

        // when
        MilvusV2EmbeddingSearchRequest searchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(denseEmbedding1)
                .sparseEmbedding(sparseEmbedding1)
                .maxResults(10)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = hybridStore.search(searchRequest);

        // then
        assertThat(searchResult.matches()).hasSize(2);
        assertThat(searchResult.matches().get(0).score()).isGreaterThan(0);
        assertThat(searchResult.matches().get(0).embedded().text()).isEqualTo("document about technology");

        hybridStore.dropCollection("hybrid_test_collection");
    }

    @Test
    void should_throw_exception_for_hybrid_search_without_dense_embedding() {
        // given
        SparseEmbedding sparseEmbedding = new SparseEmbedding(new long[] {1L, 3L, 5L}, new float[] {0.1f, 0.3f, 0.5f});

        // when & then
        // MilvusV2EmbeddingSearchRequest extends EmbeddingSearchRequest whose constructor
        // requires at least queryEmbedding OR query.  When only sparseEmbedding is supplied
        // the parent-class validation fires at build() time before search() is even called.
        assertThatThrownBy(() -> MilvusV2EmbeddingSearchRequest.milvusBuilder()
                        .sparseEmbedding(sparseEmbedding)
                        .maxResults(10)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Either queryEmbedding or query must be provided");
    }

    @Test
    void should_throw_exception_for_hybrid_search_without_sparse_embedding() {
        // given
        MilvusV2EmbeddingStore hybridStore = MilvusV2EmbeddingStore.builder()
                .uri(milvus.getEndpoint())
                .collectionName("hybrid_exception2_test_collection")
                .consistencyLevel(ConsistencyLevel.STRONG)
                .dimension(384)
                .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.CUSTOM)
                .searchMode(MilvusV2EmbeddingStore.SearchMode.HYBRID)
                .build();

        Embedding denseEmbedding = embeddingModel.embed("test document").content();

        // when & then
        MilvusV2EmbeddingSearchRequest searchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(denseEmbedding)
                .maxResults(10)
                .build();

        assertThatThrownBy(() -> hybridStore.search(searchRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("HYBRID requires dense queryEmbedding and either sparseEmbedding or query");

        hybridStore.dropCollection("hybrid_exception2_test_collection");
    }

    @Test
    void should_handle_empty_sparse_embedding_in_hybrid_search() {
        // given
        MilvusV2EmbeddingStore hybridStore = MilvusV2EmbeddingStore.builder()
                .uri(milvus.getEndpoint())
                .collectionName("hybrid_empty_sparse_test_collection")
                .consistencyLevel(ConsistencyLevel.STRONG)
                .dimension(384)
                .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.CUSTOM)
                .searchMode(MilvusV2EmbeddingStore.SearchMode.HYBRID)
                .build();

        Embedding denseEmbedding = embeddingModel.embed("test document").content();
        SparseEmbedding emptySparseEmbedding = new SparseEmbedding(new long[] {}, new float[] {});

        TextSegment textSegment = TextSegment.from("test document");

        hybridStore.addAllHybrid(
                Arrays.asList("id1"),
                Arrays.asList(denseEmbedding),
                Arrays.asList(emptySparseEmbedding),
                Arrays.asList(textSegment));

        // when
        MilvusV2EmbeddingSearchRequest searchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(denseEmbedding)
                .sparseEmbedding(emptySparseEmbedding)
                .maxResults(10)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = hybridStore.search(searchRequest);

        // then
        assertThat(searchResult.matches()).hasSize(1);
        assertThat(searchResult.matches().get(0).score()).isGreaterThan(0);

        hybridStore.dropCollection("hybrid_empty_sparse_test_collection");
    }

    @Test
    void should_compare_vector_and_hybrid_search_results() {
        // given
        Embedding denseEmbedding =
                embeddingModel.embed("artificial intelligence").content();
        SparseEmbedding sparseEmbedding =
                new SparseEmbedding(new long[] {1L, 3L, 5L, 7L}, new float[] {0.1f, 0.3f, 0.5f, 0.7f});

        // Both stores point to the same collection - HYBRID creates it with sparse field
        MilvusV2EmbeddingStore hybridStore = MilvusV2EmbeddingStore.builder()
                .uri(milvus.getEndpoint())
                .collectionName("compare_test_collection")
                .consistencyLevel(ConsistencyLevel.STRONG)
                .dimension(384)
                .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.CUSTOM)
                .searchMode(MilvusV2EmbeddingStore.SearchMode.HYBRID)
                .build();

        // VECTOR store connects to the same collection, only does dense search
        MilvusV2EmbeddingStore vectorStore = MilvusV2EmbeddingStore.builder()
                .uri(milvus.getEndpoint())
                .collectionName("compare_test_collection")
                .consistencyLevel(ConsistencyLevel.STRONG)
                .dimension(384)
                .sparseMode(MilvusV2EmbeddingStore.MilvusSparseMode.CUSTOM)
                .searchMode(MilvusV2EmbeddingStore.SearchMode.VECTOR)
                .build();

        TextSegment textSegment1 = TextSegment.from("document about AI and machine learning");
        TextSegment textSegment2 = TextSegment.from("document about neural networks");
        TextSegment textSegment3 = TextSegment.from("document about data science");

        hybridStore.addAllHybrid(
                Arrays.asList("id1", "id2", "id3"),
                Arrays.asList(
                        embeddingModel.embed("AI and machine learning").content(),
                        embeddingModel.embed("neural networks").content(),
                        embeddingModel.embed("data science").content()),
                Arrays.asList(
                        new SparseEmbedding(new long[] {1L, 3L}, new float[] {0.1f, 0.3f}),
                        new SparseEmbedding(new long[] {5L, 7L}, new float[] {0.5f, 0.7f}),
                        new SparseEmbedding(new long[] {2L, 4L}, new float[] {0.2f, 0.4f})),
                Arrays.asList(textSegment1, textSegment2, textSegment3));

        // when - vector search (same collection, dense only)
        MilvusV2EmbeddingSearchRequest vectorSearchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(denseEmbedding)
                .maxResults(3)
                .build();
        EmbeddingSearchResult<TextSegment> vectorResult = vectorStore.search(vectorSearchRequest);

        // when - hybrid search (same collection, dense + sparse)
        MilvusV2EmbeddingSearchRequest hybridSearchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(denseEmbedding)
                .sparseEmbedding(sparseEmbedding)
                .maxResults(3)
                .build();
        EmbeddingSearchResult<TextSegment> hybridResult = hybridStore.search(hybridSearchRequest);

        // then - both search the same 3 documents
        assertThat(vectorResult.matches()).hasSize(3);
        assertThat(hybridResult.matches()).hasSize(3);

        assertThat(vectorResult.matches().get(0).score()).isGreaterThan(0);
        assertThat(hybridResult.matches().get(0).score()).isGreaterThan(0);

        hybridStore.dropCollection("compare_test_collection");
    }
}
