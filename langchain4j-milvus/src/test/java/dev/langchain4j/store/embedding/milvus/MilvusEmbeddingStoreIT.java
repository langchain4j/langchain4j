package dev.langchain4j.store.embedding.milvus;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.embedding.SparseEmbedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchMode;
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
class MilvusEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    private static final String COLLECTION_NAME = "test_collection";

    @Container
    private static final MilvusContainer milvus = new MilvusContainer("milvusdb/milvus:v2.5.8");

    MilvusEmbeddingStore embeddingStore = MilvusEmbeddingStore.builder()
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

    @Test
    void should_not_retrieve_embeddings_when_searching() {

        EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
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
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(milvus.getEndpoint())
                .username("")
                .password("")
                .build();

        MilvusClientV2 milvusClientV2 = new MilvusClientV2(connectConfig);

        EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
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
    void should_perform_sparse_search() {
        // given
        SparseEmbedding sparseEmbedding1 =
                new SparseEmbedding(Arrays.asList(1L, 3L, 5L), Arrays.asList(0.1f, 0.3f, 0.5f));
        SparseEmbedding sparseEmbedding2 =
                new SparseEmbedding(Arrays.asList(2L, 4L, 6L), Arrays.asList(0.2f, 0.4f, 0.6f));

        TextSegment textSegment1 = TextSegment.from("document about technology");
        TextSegment textSegment2 = TextSegment.from("document about science");

        embeddingStore.addAllSparse(
                Arrays.asList("id1", "id2"),
                Arrays.asList(sparseEmbedding1, sparseEmbedding2),
                Arrays.asList(textSegment1, textSegment2));

        // when
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .sparseEmbedding(sparseEmbedding1)
                .searchMode(EmbeddingSearchMode.SPARSE) // sparse search
                .maxResults(10)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        // then
        assertThat(searchResult.matches()).hasSize(1);
        assertThat(searchResult.matches().get(0).score()).isGreaterThan(0);
        assertThat(searchResult.matches().get(0).embedded().text()).isEqualTo("document about technology");
    }

    @Test
    void should_perform_hybrid_search() {
        // given
        Embedding denseEmbedding1 = embeddingModel.embed("technology document").content();
        Embedding denseEmbedding2 = embeddingModel.embed("science document").content();

        SparseEmbedding sparseEmbedding1 =
                new SparseEmbedding(Arrays.asList(1L, 3L, 5L), Arrays.asList(0.1f, 0.3f, 0.5f));
        SparseEmbedding sparseEmbedding2 =
                new SparseEmbedding(Arrays.asList(2L, 4L, 6L), Arrays.asList(0.2f, 0.4f, 0.6f));

        TextSegment textSegment1 = TextSegment.from("document about technology");
        TextSegment textSegment2 = TextSegment.from("document about science");

        embeddingStore.addAllHybrid(
                Arrays.asList("id1", "id2"),
                Arrays.asList(denseEmbedding1, denseEmbedding2),
                Arrays.asList(sparseEmbedding1, sparseEmbedding2),
                Arrays.asList(textSegment1, textSegment2));

        // when
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(denseEmbedding1)
                .sparseEmbedding(sparseEmbedding1)
                .searchMode(EmbeddingSearchMode.HYBRID) // hybrid search
                .maxResults(10)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        // then
        assertThat(searchResult.matches()).hasSize(2);
        assertThat(searchResult.matches().get(0).score()).isGreaterThan(0);
        assertThat(searchResult.matches().get(0).embedded().text()).isEqualTo("document about technology");
    }

    @Test
    void should_throw_exception_for_hybrid_search_without_dense_embedding() {
        // given
        SparseEmbedding sparseEmbedding =
                new SparseEmbedding(Arrays.asList(1L, 3L, 5L), Arrays.asList(0.1f, 0.3f, 0.5f));

        // when & then
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .sparseEmbedding(sparseEmbedding)
                .searchMode(EmbeddingSearchMode.HYBRID) // hybrid search
                .maxResults(10)
                .build();

        assertThatThrownBy(() -> embeddingStore.search(searchRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Both queryEmbedding and sparseEmbedding must be provided for hybrid search (searchMode=2)");
    }

    @Test
    void should_throw_exception_for_hybrid_search_without_sparse_embedding() {
        // given
        Embedding denseEmbedding = embeddingModel.embed("test document").content();

        // when & then
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(denseEmbedding)
                .searchMode(EmbeddingSearchMode.HYBRID) // hybrid search
                .maxResults(10)
                .build();

        assertThatThrownBy(() -> embeddingStore.search(searchRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Both queryEmbedding and sparseEmbedding must be provided for hybrid search (searchMode=2)");
    }

    @Test
    void should_handle_empty_sparse_embedding_in_hybrid_search() {
        // given
        Embedding denseEmbedding = embeddingModel.embed("test document").content();
        SparseEmbedding emptySparseEmbedding = new SparseEmbedding(Arrays.asList(), Arrays.asList());

        TextSegment textSegment = TextSegment.from("test document");

        embeddingStore.addAllHybrid(
                Arrays.asList("id1"),
                Arrays.asList(denseEmbedding),
                Arrays.asList(emptySparseEmbedding),
                Arrays.asList(textSegment));

        // when
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(denseEmbedding)
                .sparseEmbedding(emptySparseEmbedding)
                .searchMode(EmbeddingSearchMode.HYBRID) // hybrid search
                .maxResults(10)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        // then
        assertThat(searchResult.matches()).hasSize(1);
        assertThat(searchResult.matches().get(0).score()).isGreaterThan(0);
    }

    @Test
    void should_compare_dense_sparse_and_hybrid_search_results() {
        // given
        Embedding denseEmbedding =
                embeddingModel.embed("artificial intelligence").content();
        SparseEmbedding sparseEmbedding =
                new SparseEmbedding(Arrays.asList(1L, 3L, 5L, 7L), Arrays.asList(0.1f, 0.3f, 0.5f, 0.7f));

        TextSegment textSegment1 = TextSegment.from("document about AI and machine learning");
        TextSegment textSegment2 = TextSegment.from("document about neural networks");
        TextSegment textSegment3 = TextSegment.from("document about data science");

        // Add documents with both dense and sparse embeddings
        embeddingStore.addAllHybrid(
                Arrays.asList("id1", "id2", "id3"),
                Arrays.asList(
                        embeddingModel.embed("AI and machine learning").content(),
                        embeddingModel.embed("neural networks").content(),
                        embeddingModel.embed("data science").content()),
                Arrays.asList(
                        new SparseEmbedding(Arrays.asList(1L, 3L), Arrays.asList(0.1f, 0.3f)),
                        new SparseEmbedding(Arrays.asList(5L, 7L), Arrays.asList(0.5f, 0.7f)),
                        new SparseEmbedding(Arrays.asList(2L, 4L), Arrays.asList(0.2f, 0.4f))),
                Arrays.asList(textSegment1, textSegment2, textSegment3));

        // when - dense search
        EmbeddingSearchRequest denseSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(denseEmbedding)
                .searchMode(EmbeddingSearchMode.DENSE) // dense search
                .maxResults(3)
                .build();
        EmbeddingSearchResult<TextSegment> denseResult = embeddingStore.search(denseSearchRequest);

        // when - sparse search
        EmbeddingSearchRequest sparseSearchRequest = EmbeddingSearchRequest.builder()
                .sparseEmbedding(sparseEmbedding)
                .searchMode(EmbeddingSearchMode.SPARSE) // sparse search
                .maxResults(3)
                .build();
        EmbeddingSearchResult<TextSegment> sparseResult = embeddingStore.search(sparseSearchRequest);

        // when - hybrid search
        EmbeddingSearchRequest hybridSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(denseEmbedding)
                .sparseEmbedding(sparseEmbedding)
                .searchMode(EmbeddingSearchMode.HYBRID) // hybrid search
                .maxResults(3)
                .build();
        EmbeddingSearchResult<TextSegment> hybridResult = embeddingStore.search(hybridSearchRequest);

        // then
        assertThat(denseResult.matches()).hasSize(3);
        assertThat(sparseResult.matches()).hasSize(2);
        assertThat(hybridResult.matches()).hasSize(3);

        // All searches should return results with scores > 0
        assertThat(denseResult.matches().get(0).score()).isGreaterThan(0);
        assertThat(sparseResult.matches().get(0).score()).isGreaterThan(0);
        assertThat(hybridResult.matches().get(0).score()).isGreaterThan(0);
    }
}
