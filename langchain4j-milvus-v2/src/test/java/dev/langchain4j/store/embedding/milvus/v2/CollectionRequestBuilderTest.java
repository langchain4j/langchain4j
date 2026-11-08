package dev.langchain4j.store.embedding.milvus.v2;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static dev.langchain4j.store.embedding.milvus.v2.CollectionRequestBuilder.buildHybridSearchRequest;
import static dev.langchain4j.store.embedding.milvus.v2.CollectionRequestBuilder.buildQueryRequest;
import static dev.langchain4j.store.embedding.milvus.v2.CollectionRequestBuilder.buildSearchRequest;
import static dev.langchain4j.store.embedding.milvus.v2.CollectionRequestBuilder.createDenseAnnSearchReq;
import static dev.langchain4j.store.embedding.milvus.v2.CollectionRequestBuilder.createDenseSearchReq;
import static dev.langchain4j.store.embedding.milvus.v2.CollectionRequestBuilder.createHybridSearchReq;
import static dev.langchain4j.store.embedding.milvus.v2.CollectionRequestBuilder.createSparseAnnSearchReq;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.filter.Filter;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class CollectionRequestBuilderTest implements WithAssertions {

    private static final String COLLECTION_NAME = "test_collection";
    private static final FieldDefinition FIELD_DEFINITION =
            new FieldDefinition("id", "text", "metadata", "vector", "sparse_vector");

    private static final MilvusServerVersion MILVUS_2 = MilvusServerVersion.from("2.6.26");
    private static final MilvusServerVersion MILVUS_3 = MilvusServerVersion.from("3.0.4");

    private static final Filter FILTER = Filter.not(metadataKey("k").containsString("a\\b"));

    // Milvus 3 needs one more level of escaping for the backslash in the LIKE pattern
    private static final String FILTER_EXPRESSION_FOR_MILVUS_3 =
            "not(((exists metadata[\"k\"]) and metadata[\"k\"] LIKE \"%a\\\\\\\\b%\"))";

    @Test
    void should_build_vector_search_request() {
        // given
        Embedding queryEmbedding = Embedding.from(Arrays.asList(1.0f, 2.0f, 3.0f));
        MilvusV2EmbeddingSearchRequest searchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .minScore(0.5)
                .build();

        // when
        SearchReq result = buildSearchRequest(
                searchRequest,
                COLLECTION_NAME,
                FIELD_DEFINITION,
                IndexParam.MetricType.COSINE,
                ConsistencyLevel.STRONG,
                MILVUS_2);

        // then
        assertThat(result.getCollectionName()).isEqualTo(COLLECTION_NAME);
        assertThat(result.getAnnsField()).isEqualTo("vector");
        assertThat(result.getMetricType()).isEqualTo(IndexParam.MetricType.COSINE);
        assertThat(result.getLimit()).isEqualTo(5);
        assertThat(result.getOutputFields()).containsExactly("id", "text", "metadata");
    }

    @Test
    void should_quote_and_escape_row_ids_in_query_expression() {
        List<String> rowIds = Arrays.asList("normal-id", "id'with'apostrophe", "id\"with-quote", "id\\with-backslash");

        QueryReq result = buildQueryRequest(COLLECTION_NAME, FIELD_DEFINITION, rowIds, ConsistencyLevel.STRONG);

        assertThat(result.getFilter())
                .isEqualTo(
                        "id in [\"normal-id\", \"id'with'apostrophe\", \"id\\\"with-quote\", \"id\\\\with-backslash\"]");
        assertThat(result.getLimit()).isEqualTo(4);
    }

    @Test
    void should_build_hybrid_search_request() {
        // given
        Embedding queryEmbedding = Embedding.from(Arrays.asList(1.0f, 2.0f, 3.0f));
        SparseEmbedding sparseEmbedding = new SparseEmbedding(new long[] {1L, 3L, 5L}, new float[] {0.1f, 0.3f, 0.5f});
        MilvusV2EmbeddingSearchRequest searchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(queryEmbedding)
                .sparseEmbedding(sparseEmbedding)
                .maxResults(15)
                .minScore(0.7)
                .build();

        MilvusV2Ranker ranker = MilvusV2Ranker.rrf(60);

        // when
        HybridSearchReq result = buildHybridSearchRequest(
                searchRequest,
                COLLECTION_NAME,
                FIELD_DEFINITION,
                IndexParam.MetricType.COSINE,
                IndexParam.MetricType.IP,
                ranker,
                ConsistencyLevel.STRONG,
                MILVUS_2);

        // then
        assertThat(result.getCollectionName()).isEqualTo(COLLECTION_NAME);
        assertThat(result.getLimit()).isEqualTo(15);
        assertThat(result.getRanker().toString()).isEqualTo(ranker.toFunction().toString());
        assertThat(result.getSearchRequests()).hasSize(2);

        // Check dense search request
        var denseSearchReq = result.getSearchRequests().get(0);
        assertThat(denseSearchReq.getVectorFieldName()).isEqualTo("vector");
        assertThat(denseSearchReq.getMetricType()).isEqualTo(IndexParam.MetricType.COSINE);
        assertThat(denseSearchReq.getLimit()).isEqualTo(15);

        // Check sparse search request
        var sparseSearchReq = result.getSearchRequests().get(1);
        assertThat(sparseSearchReq.getVectorFieldName()).isEqualTo("sparse_vector");
        assertThat(sparseSearchReq.getMetricType()).isEqualTo(IndexParam.MetricType.IP);
        assertThat(sparseSearchReq.getLimit()).isEqualTo(15);
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
        assertThat(result.getLimit()).isEqualTo(5);
        assertThat(result.getOutputFields()).containsExactly("id", "text", "metadata");
    }

    @Test
    void should_create_hybrid_search_request_with_ann_search_reqs() {
        // given
        Embedding queryEmbedding = Embedding.from(Arrays.asList(1.0f, 2.0f, 3.0f));
        SparseEmbedding sparseEmbedding = new SparseEmbedding(new long[] {1L, 3L, 5L}, new float[] {0.1f, 0.3f, 0.5f});
        MilvusV2Ranker ranker = MilvusV2Ranker.rrf(60);

        // when
        HybridSearchReq result = createHybridSearchReq(
                FIELD_DEFINITION,
                queryEmbedding,
                sparseEmbedding,
                null,
                IndexParam.MetricType.COSINE,
                IndexParam.MetricType.IP,
                null, // filter expression
                20,
                COLLECTION_NAME,
                ranker,
                ConsistencyLevel.STRONG);

        // then
        assertThat(result.getCollectionName()).isEqualTo(COLLECTION_NAME);
        assertThat(result.getLimit()).isEqualTo(20);
        assertThat(result.getRanker().toString()).isEqualTo(ranker.toFunction().toString());
        assertThat(result.getSearchRequests()).hasSize(2);

        // Check dense ANN search request
        var denseAnnReq = result.getSearchRequests().get(0);
        assertThat(denseAnnReq.getVectorFieldName()).isEqualTo("vector");
        assertThat(denseAnnReq.getMetricType()).isEqualTo(IndexParam.MetricType.COSINE);
        assertThat(denseAnnReq.getLimit()).isEqualTo(20);

        // Check sparse ANN search request
        var sparseAnnReq = result.getSearchRequests().get(1);
        assertThat(sparseAnnReq.getVectorFieldName()).isEqualTo("sparse_vector");
        assertThat(sparseAnnReq.getMetricType()).isEqualTo(IndexParam.MetricType.IP);
        assertThat(sparseAnnReq.getLimit()).isEqualTo(20);
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
                null, // filter expression
                5);

        // then
        assertThat(result.getVectorFieldName()).isEqualTo("vector");
        assertThat(result.getMetricType()).isEqualTo(IndexParam.MetricType.COSINE);
        assertThat(result.getLimit()).isEqualTo(5);
    }

    @Test
    void should_create_sparse_ann_search_request() {
        // given
        SparseEmbedding sparseEmbedding = new SparseEmbedding(new long[] {1L, 3L, 5L}, new float[] {0.1f, 0.3f, 0.5f});

        // when
        var result = createSparseAnnSearchReq(
                FIELD_DEFINITION,
                sparseEmbedding,
                IndexParam.MetricType.IP,
                null, // filter expression
                10);

        // then
        assertThat(result.getVectorFieldName()).isEqualTo("sparse_vector");
        assertThat(result.getMetricType()).isEqualTo(IndexParam.MetricType.IP);
        assertThat(result.getLimit()).isEqualTo(10);
    }

    @Test
    void should_put_the_filter_expression_on_the_search_request() {
        MilvusV2EmbeddingSearchRequest searchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(Embedding.from(Arrays.asList(1.0f, 2.0f, 3.0f)))
                .filter(FILTER)
                .maxResults(5)
                .build();

        SearchReq result = buildSearchRequest(
                searchRequest,
                COLLECTION_NAME,
                FIELD_DEFINITION,
                IndexParam.MetricType.COSINE,
                ConsistencyLevel.STRONG,
                MILVUS_3);

        assertThat(result.getFilter()).isEqualTo(FILTER_EXPRESSION_FOR_MILVUS_3);
    }

    @Test
    void should_put_the_filter_expression_on_every_ann_search_request_of_a_hybrid_search() {
        MilvusV2EmbeddingSearchRequest searchRequest = MilvusV2EmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(Embedding.from(Arrays.asList(1.0f, 2.0f, 3.0f)))
                .sparseEmbedding(new SparseEmbedding(new long[] {1L}, new float[] {0.1f}))
                .filter(FILTER)
                .maxResults(5)
                .build();

        HybridSearchReq result = buildHybridSearchRequest(
                searchRequest,
                COLLECTION_NAME,
                FIELD_DEFINITION,
                IndexParam.MetricType.COSINE,
                IndexParam.MetricType.IP,
                MilvusV2Ranker.rrf(60),
                ConsistencyLevel.STRONG,
                MILVUS_3);

        assertThat(result.getSearchRequests())
                .extracting(AnnSearchReq::getFilter)
                .containsExactly(FILTER_EXPRESSION_FOR_MILVUS_3, FILTER_EXPRESSION_FOR_MILVUS_3);
    }
}
