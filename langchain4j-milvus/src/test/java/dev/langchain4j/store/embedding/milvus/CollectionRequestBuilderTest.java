package dev.langchain4j.store.embedding.milvus;

import static dev.langchain4j.store.embedding.milvus.CollectionRequestBuilder.*;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.embedding.SparseEmbedding;
import dev.langchain4j.store.embedding.EmbeddingSearchMode;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import java.util.Arrays;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class CollectionRequestBuilderTest implements WithAssertions {

    private static final String COLLECTION_NAME = "test_collection";
    private static final FieldDefinition FIELD_DEFINITION =
            new FieldDefinition("id", "text", "metadata", "vector", "sparse_vector");

    @Test
    void should_build_dense_search_request() {
        // given
        Embedding queryEmbedding = Embedding.from(Arrays.asList(1.0f, 2.0f, 3.0f));
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .searchMode(EmbeddingSearchMode.DENSE) // dense search
                .maxResults(5)
                .minScore(0.5)
                .build();

        // when
        SearchReq result = buildSearchRequest(
                searchRequest,
                COLLECTION_NAME,
                FIELD_DEFINITION,
                IndexParam.MetricType.COSINE,
                IndexParam.MetricType.IP,
                ConsistencyLevel.STRONG);

        // then
        assertThat(result.getCollectionName()).isEqualTo(COLLECTION_NAME);
        assertThat(result.getAnnsField()).isEqualTo("vector");
        assertThat(result.getMetricType()).isEqualTo(IndexParam.MetricType.COSINE);
        assertThat(result.getTopK()).isEqualTo(5);
        assertThat(result.getOutputFields()).containsExactly("id", "text", "metadata");
    }

    @Test
    void should_build_sparse_search_request() {
        // given
        SparseEmbedding sparseEmbedding =
                new SparseEmbedding(Arrays.asList(1L, 3L, 5L), Arrays.asList(0.1f, 0.3f, 0.5f));
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .sparseEmbedding(sparseEmbedding)
                .searchMode(EmbeddingSearchMode.SPARSE) // sparse search
                .maxResults(10)
                .minScore(0.3)
                .build();

        // when
        SearchReq result = buildSearchRequest(
                searchRequest,
                COLLECTION_NAME,
                FIELD_DEFINITION,
                IndexParam.MetricType.COSINE,
                IndexParam.MetricType.IP,
                ConsistencyLevel.STRONG);

        // then
        assertThat(result.getCollectionName()).isEqualTo(COLLECTION_NAME);
        assertThat(result.getAnnsField()).isEqualTo("sparse_vector");
        assertThat(result.getMetricType()).isEqualTo(IndexParam.MetricType.IP);
        assertThat(result.getTopK()).isEqualTo(10);
        assertThat(result.getOutputFields()).containsExactly("id", "text", "metadata");
    }

    @Test
    void should_build_hybrid_search_request() {
        // given
        Embedding queryEmbedding = Embedding.from(Arrays.asList(1.0f, 2.0f, 3.0f));
        SparseEmbedding sparseEmbedding =
                new SparseEmbedding(Arrays.asList(1L, 3L, 5L), Arrays.asList(0.1f, 0.3f, 0.5f));
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .sparseEmbedding(sparseEmbedding)
                .searchMode(EmbeddingSearchMode.HYBRID) // hybrid search
                .maxResults(15)
                .minScore(0.7)
                .build();

        RRFRanker ranker = new RRFRanker(60);

        // when
        HybridSearchReq result = buildHybridSearchRequest(
                searchRequest,
                COLLECTION_NAME,
                FIELD_DEFINITION,
                IndexParam.MetricType.COSINE,
                IndexParam.MetricType.IP,
                ranker,
                ConsistencyLevel.STRONG);

        // then
        assertThat(result.getCollectionName()).isEqualTo(COLLECTION_NAME);
        assertThat(result.getTopK()).isEqualTo(15);
        assertThat(result.getRanker()).isEqualTo(ranker);
        assertThat(result.getSearchRequests()).hasSize(2);

        // Check dense search request
        var denseSearchReq = result.getSearchRequests().get(0);
        assertThat(denseSearchReq.getVectorFieldName()).isEqualTo("vector");
        assertThat(denseSearchReq.getMetricType()).isEqualTo(IndexParam.MetricType.COSINE);
        assertThat(denseSearchReq.getTopK()).isEqualTo(15);

        // Check sparse search request
        var sparseSearchReq = result.getSearchRequests().get(1);
        assertThat(sparseSearchReq.getVectorFieldName()).isEqualTo("sparse_vector");
        assertThat(sparseSearchReq.getMetricType()).isEqualTo(IndexParam.MetricType.IP);
        assertThat(sparseSearchReq.getTopK()).isEqualTo(15);
    }

    @Test
    void should_create_dense_search_request() {
        // given
        Embedding queryEmbedding = Embedding.from(Arrays.asList(1.0f, 2.0f, 3.0f));

        // when
        SearchReq result = createDenseSearchReq(
                COLLECTION_NAME,
                queryEmbedding,
                FIELD_DEFINITION,
                IndexParam.MetricType.COSINE,
                ConsistencyLevel.STRONG,
                5,
                null);

        // then
        assertThat(result.getCollectionName()).isEqualTo(COLLECTION_NAME);
        assertThat(result.getAnnsField()).isEqualTo("vector");
        assertThat(result.getMetricType()).isEqualTo(IndexParam.MetricType.COSINE);
        assertThat(result.getTopK()).isEqualTo(5);
        assertThat(result.getOutputFields()).containsExactly("id", "text", "metadata");
    }

    @Test
    void should_create_sparse_search_request() {
        // given
        SparseEmbedding sparseEmbedding =
                new SparseEmbedding(Arrays.asList(1L, 3L, 5L), Arrays.asList(0.1f, 0.3f, 0.5f));

        // when
        SearchReq result = createSparseSearchReq(
                COLLECTION_NAME,
                sparseEmbedding,
                FIELD_DEFINITION,
                IndexParam.MetricType.IP,
                ConsistencyLevel.STRONG,
                10,
                null);

        // then
        assertThat(result.getCollectionName()).isEqualTo(COLLECTION_NAME);
        assertThat(result.getAnnsField()).isEqualTo("sparse_vector");
        assertThat(result.getMetricType()).isEqualTo(IndexParam.MetricType.IP);
        assertThat(result.getTopK()).isEqualTo(10);
        assertThat(result.getOutputFields()).containsExactly("id", "text", "metadata");
    }

    @Test
    void should_create_hybrid_search_request_with_ann_search_reqs() {
        // given
        Embedding queryEmbedding = Embedding.from(Arrays.asList(1.0f, 2.0f, 3.0f));
        SparseEmbedding sparseEmbedding =
                new SparseEmbedding(Arrays.asList(1L, 3L, 5L), Arrays.asList(0.1f, 0.3f, 0.5f));
        RRFRanker ranker = new RRFRanker(60);

        // when
        HybridSearchReq result = createHybridSearchReq(
                FIELD_DEFINITION,
                queryEmbedding,
                sparseEmbedding,
                IndexParam.MetricType.COSINE,
                IndexParam.MetricType.IP,
                null, // filter
                20,
                COLLECTION_NAME,
                ranker,
                ConsistencyLevel.STRONG);

        // then
        assertThat(result.getCollectionName()).isEqualTo(COLLECTION_NAME);
        assertThat(result.getTopK()).isEqualTo(20);
        assertThat(result.getRanker()).isEqualTo(ranker);
        assertThat(result.getSearchRequests()).hasSize(2);

        // Check dense ANN search request
        var denseAnnReq = result.getSearchRequests().get(0);
        assertThat(denseAnnReq.getVectorFieldName()).isEqualTo("vector");
        assertThat(denseAnnReq.getMetricType()).isEqualTo(IndexParam.MetricType.COSINE);
        assertThat(denseAnnReq.getTopK()).isEqualTo(20);

        // Check sparse ANN search request
        var sparseAnnReq = result.getSearchRequests().get(1);
        assertThat(sparseAnnReq.getVectorFieldName()).isEqualTo("sparse_vector");
        assertThat(sparseAnnReq.getMetricType()).isEqualTo(IndexParam.MetricType.IP);
        assertThat(sparseAnnReq.getTopK()).isEqualTo(20);
    }

    @Test
    void should_create_dense_ann_search_request() {
        // given
        Embedding queryEmbedding = Embedding.from(Arrays.asList(1.0f, 2.0f, 3.0f));

        // when
        var result = createDenseAnnSearchReq(
                FIELD_DEFINITION,
                queryEmbedding,
                IndexParam.MetricType.COSINE,
                null, // filter
                5);

        // then
        assertThat(result.getVectorFieldName()).isEqualTo("vector");
        assertThat(result.getMetricType()).isEqualTo(IndexParam.MetricType.COSINE);
        assertThat(result.getTopK()).isEqualTo(5);
    }

    @Test
    void should_create_sparse_ann_search_request() {
        // given
        SparseEmbedding sparseEmbedding =
                new SparseEmbedding(Arrays.asList(1L, 3L, 5L), Arrays.asList(0.1f, 0.3f, 0.5f));

        // when
        var result = createSparseAnnSearchReq(
                FIELD_DEFINITION,
                sparseEmbedding,
                IndexParam.MetricType.IP,
                null, // filter
                10);

        // then
        assertThat(result.getVectorFieldName()).isEqualTo("sparse_vector");
        assertThat(result.getMetricType()).isEqualTo(IndexParam.MetricType.IP);
        assertThat(result.getTopK()).isEqualTo(10);
    }
}
