package dev.langchain4j.store.embedding.milvus;

import static java.util.Arrays.asList;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import io.milvus.v2.common.ConsistencyLevel;
import java.util.Arrays;
import io.milvus.v2.common.IndexParam;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

/**
 * Built-in BM25 for sparse embedding.
 */
@Testcontainers
class MilvusBM25IT implements WithAssertions {

    @Container
    private static final MilvusContainer milvus = new MilvusContainer("milvusdb/milvus:v2.5.8");

    private EmbeddingModel embeddingModel;
    private MilvusEmbeddingStore embeddingStore;
    private String collectionName;

    @BeforeEach
    void setUp() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        collectionName = "bm25_it_" + System.currentTimeMillis();

        // sparseMode is BM25 in default
        embeddingStore = MilvusEmbeddingStore.builder()
                .uri(milvus.getEndpoint())
                .collectionName(collectionName)
                .username(System.getenv("MILVUS_USERNAME"))
                .password(System.getenv("MILVUS_PASSWORD"))
                .dimension(384)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .retrieveEmbeddingsOnSearch(true)
                .build();
    }

    @Test
    void should_index_text_and_search_with_query_string_bm25() {
        TextSegment t1 = TextSegment.from("Milvus supports full-text search with BM25.");
        TextSegment t2 = TextSegment.from("Semantic search with dense vectors captures meaning.");
        TextSegment t3 = TextSegment.from("BM25 focuses on lexical relevance through term statistics.");

        Embedding d1 = embeddingModel.embed(t1.text()).content();
        Embedding d2 = embeddingModel.embed(t2.text()).content();
        Embedding d3 = embeddingModel.embed(t3.text()).content();

        embeddingStore.addAll(
                asList("id1", "id2", "id3"),
                asList(d1, d2, d3),
                asList(t1, t2, t3));

        MilvusEmbeddingSearchRequest sparseReq = MilvusEmbeddingSearchRequest.milvusBuilder()
                .sparseQueryText("full-text search BM25")
                .searchMode(MilvusEmbeddingSearchMode.SPARSE)
                .maxResults(5)
                .build();

        EmbeddingSearchResult<TextSegment> sparseResult = embeddingStore.search(sparseReq);

        assertThat(sparseResult.matches()).isNotEmpty();
        assertThat(sparseResult.matches().get(0).embedded().text())
                .containsAnyOf("BM25", "full-text");
        assertThat(sparseResult.matches().get(0).score()).isGreaterThan(0.0);
    }

    @Test
    void should_throw_when_client_sparse_insert_in_bm25_mode() {
        SparseEmbedding sparse = new SparseEmbedding(
                new long[]{1L, 3L, 5L}, new float[]{0.1f, 0.3f, 0.5f});
        TextSegment text = TextSegment.from("a document");

        // When using built-in BM25 for sparse, it's not allowed to provide sparse embedding
        assertThatThrownBy(() ->
                embeddingStore.addAllSparse(
                        asList("sid"),
                        asList(sparse),
                        asList(text))
        ).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() ->
                embeddingStore.addAllHybrid(
                        asList("hid"),
                        asList(embeddingModel.embed(text.text()).content()),
                        asList(sparse),
                        asList(text))
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_perform_hybrid_dense_plus_bm25_query_text() {
        TextSegment t1 = TextSegment.from("Vector database supports semantic search.");
        TextSegment t2 = TextSegment.from("BM25 is a ranking function for full-text search.");
        TextSegment t3 = TextSegment.from("Hybrid search combines dense and sparse signals.");

        Embedding d1 = embeddingModel.embed(t1.text()).content();
        Embedding d2 = embeddingModel.embed(t2.text()).content();
        Embedding d3 = embeddingModel.embed(t3.text()).content();

        embeddingStore.addAll(
                Arrays.asList("a", "b", "c"),
                Arrays.asList(d1, d2, d3),
                Arrays.asList(t1, t2, t3));

        // Hybrid search with dense embedding and sparse query text
        MilvusEmbeddingSearchRequest hybridReq = MilvusEmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(embeddingModel.embed("How does hybrid search work?").content())
                .sparseQueryText("full-text BM25 hybrid")
                .searchMode(MilvusEmbeddingSearchMode.HYBRID)
                .maxResults(5)
                .build();

        EmbeddingSearchResult<TextSegment> hybrid = embeddingStore.search(hybridReq);

        assertThat(hybrid.matches()).isNotEmpty();
        assertThat(hybrid.matches().get(0).score()).isGreaterThan(0.0);
    }

    @Test
    void should_reject_query_text_with_non_bm25_metric_in_sparse_search() {
        String query = "some text query";

        assertThatThrownBy(() ->
                CollectionRequestBuilder.createSparseSearchReq(
                        "col", query,
                        new FieldDefinition("id","text","meta","dense","sparse"),
                        IndexParam.MetricType.IP,
                        io.milvus.v2.common.ConsistencyLevel.STRONG,
                        3, null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_query_text_with_non_bm25_metric_in_ann_sparse() {
        String query = "another text";

        assertThatThrownBy(() ->
                CollectionRequestBuilder.createSparseAnnSearchReq(
                        new FieldDefinition("id","text","meta","dense","sparse"),
                        query,
                        IndexParam.MetricType.COSINE,
                        null, 5)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
