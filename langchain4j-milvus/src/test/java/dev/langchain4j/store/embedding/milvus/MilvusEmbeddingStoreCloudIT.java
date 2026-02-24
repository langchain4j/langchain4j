package dev.langchain4j.store.embedding.milvus;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import io.milvus.v2.common.ConsistencyLevel;
import java.util.Arrays;
import java.util.List;
import io.milvus.v2.common.IndexParam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MILVUS_API_KEY", matches = ".+")
class MilvusEmbeddingStoreCloudIT extends EmbeddingStoreWithFilteringIT {

    private static final String COLLECTION_NAME = "test_collection";

    MilvusEmbeddingStore embeddingStore = MilvusEmbeddingStore.builder()
            .uri(System.getenv("MILVUS_URI"))
            .token(System.getenv("MILVUS_API_KEY"))
            .collectionName(COLLECTION_NAME)
            .consistencyLevel(ConsistencyLevel.STRONG)
            .dimension(384)
            .retrieveEmbeddingsOnSearch(true)
            .sparseMode(MilvusEmbeddingStore.MilvusSparseMode.CUSTOM)
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

        boolean retrieveEmbeddingsOnSearch = false;

        EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
                .uri(System.getenv("MILVUS_URI"))
                .token(System.getenv("MILVUS_API_KEY"))
                .collectionName(COLLECTION_NAME)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .dimension(384)
                .retrieveEmbeddingsOnSearch(retrieveEmbeddingsOnSearch)
                .sparseMode(MilvusEmbeddingStore.MilvusSparseMode.CUSTOM)
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
    void should_perform_hybrid_search_in_cloud() {
        // given
        Embedding denseEmbedding1 =
                embeddingModel.embed("cloud technology document").content();
        Embedding denseEmbedding2 =
                embeddingModel.embed("cloud science document").content();

        SparseEmbedding sparseEmbedding1 =
                new SparseEmbedding(new long[]{1L, 3L, 5L}, new float[]{0.1f, 0.3f, 0.5f});
        SparseEmbedding sparseEmbedding2 =
                new SparseEmbedding(new long[]{2L, 4L, 6L}, new float[]{0.2f, 0.4f, 0.6f});

        TextSegment textSegment1 = TextSegment.from("document about cloud technology");
        TextSegment textSegment2 = TextSegment.from("document about cloud science");

        embeddingStore.addAllHybrid(
                Arrays.asList("cloud_id1", "cloud_id2"),
                Arrays.asList(denseEmbedding1, denseEmbedding2),
                Arrays.asList(sparseEmbedding1, sparseEmbedding2),
                Arrays.asList(textSegment1, textSegment2));

        // when
        MilvusEmbeddingSearchRequest searchRequest = MilvusEmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(denseEmbedding1)
                .sparseEmbedding(sparseEmbedding1)
                .searchMode(MilvusEmbeddingSearchMode.HYBRID) // hybrid search
                .maxResults(10)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        // then
        assertThat(searchResult.matches()).hasSize(2);
        assertThat(searchResult.matches().get(0).score()).isGreaterThan(0);
        assertThat(searchResult.matches().get(0).embedded().text()).isEqualTo("document about cloud technology");
    }

    @Test
    void should_perform_sparse_search_in_cloud() {
        // given
        SparseEmbedding sparseEmbedding1 =
                new SparseEmbedding(new long[]{1L, 3L, 5L}, new float[]{0.1f, 0.3f, 0.5f});
        SparseEmbedding sparseEmbedding2 =
                new SparseEmbedding(new long[]{2L, 4L, 6L}, new float[]{0.2f, 0.4f, 0.6f});

        TextSegment textSegment1 = TextSegment.from("cloud document about technology");
        TextSegment textSegment2 = TextSegment.from("cloud document about science");

        embeddingStore.addAllSparse(
                Arrays.asList("cloud_sparse_id1", "cloud_sparse_id2"),
                Arrays.asList(sparseEmbedding1, sparseEmbedding2),
                Arrays.asList(textSegment1, textSegment2));

        // when
        MilvusEmbeddingSearchRequest searchRequest = MilvusEmbeddingSearchRequest.milvusBuilder()
                .sparseEmbedding(sparseEmbedding1)
                .searchMode(MilvusEmbeddingSearchMode.SPARSE) // sparse search
                .maxResults(10)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        // then
        assertThat(searchResult.matches()).hasSize(1);
        assertThat(searchResult.matches().get(0).score()).isGreaterThan(0);
        assertThat(searchResult.matches().get(0).embedded().text()).isEqualTo("cloud document about technology");
    }

    @Test
    void should_perform_sparse_search_bm25_in_cloud() {
        String coll = COLLECTION_NAME + "_bm25_sparse_" + System.currentTimeMillis();
        MilvusEmbeddingStore store = MilvusEmbeddingStore.builder()
                .uri(System.getenv("MILVUS_URI"))
                .token(System.getenv("MILVUS_API_KEY"))
                .collectionName(coll)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .dimension(384)
                .retrieveEmbeddingsOnSearch(true)
                .build();

        try {
            TextSegment t1 = TextSegment.from("Cloud database supports full-text search with BM25.");
            TextSegment t2 = TextSegment.from("Semantic vectors capture meaning, BM25 captures lexical matches.");
            Embedding d1 = embeddingModel.embed(t1.text()).content();
            Embedding d2 = embeddingModel.embed(t2.text()).content();

            store.addAll(Arrays.asList("b1", "b2"), Arrays.asList(d1, d2), Arrays.asList(t1, t2));

            MilvusEmbeddingSearchRequest req = MilvusEmbeddingSearchRequest.milvusBuilder()
                    .sparseQueryText("full-text BM25 cloud")
                    .searchMode(MilvusEmbeddingSearchMode.SPARSE)
                    .maxResults(5)
                    .build();

            EmbeddingSearchResult<TextSegment> res = store.search(req);

            assertThat(res.matches()).isNotEmpty();
            assertThat(res.matches().get(0).score()).isGreaterThan(0.0);
            assertThat(res.matches().get(0).embedded().text()).containsAnyOf("BM25", "full-text", "cloud");
        } finally {
            store.dropCollection(coll);
        }
    }

    @Test
    void should_perform_hybrid_search_bm25_in_cloud() {
        String coll = COLLECTION_NAME + "_bm25_hybrid_" + System.currentTimeMillis();
        MilvusEmbeddingStore store = MilvusEmbeddingStore.builder()
                .uri(System.getenv("MILVUS_URI"))
                .token(System.getenv("MILVUS_API_KEY"))
                .collectionName(coll)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .dimension(384)
                .retrieveEmbeddingsOnSearch(true)
                .build();

        try {
            TextSegment t1 = TextSegment.from("Hybrid search combines dense and BM25 sparse signals in cloud.");
            TextSegment t2 = TextSegment.from("Unrelated document about gardening.");
            Embedding d1 = embeddingModel.embed(t1.text()).content();
            Embedding d2 = embeddingModel.embed(t2.text()).content();

            store.addAll(Arrays.asList("h1", "h2"), Arrays.asList(d1, d2), Arrays.asList(t1, t2));

            Embedding queryDense = embeddingModel.embed("How does hybrid search combine signals?").content();

            MilvusEmbeddingSearchRequest req = MilvusEmbeddingSearchRequest.milvusBuilder()
                    .queryEmbedding(queryDense)
                    .sparseQueryText("BM25 hybrid cloud")
                    .searchMode(MilvusEmbeddingSearchMode.HYBRID)
                    .maxResults(5)
                    .build();

            EmbeddingSearchResult<TextSegment> res = store.search(req);

            assertThat(res.matches()).isNotEmpty();
            assertThat(res.matches().get(0).score()).isGreaterThan(0.0);
            assertThat(res.matches().get(0).embedded().text()).containsAnyOf("Hybrid", "BM25");
        } finally {
            store.dropCollection(coll);
        }
    }

}
