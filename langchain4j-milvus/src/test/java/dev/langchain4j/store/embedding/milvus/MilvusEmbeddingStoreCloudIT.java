package dev.langchain4j.store.embedding.milvus;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

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
import io.milvus.v2.common.ConsistencyLevel;
import java.util.Arrays;
import java.util.List;
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
                new SparseEmbedding(Arrays.asList(1L, 3L, 5L), Arrays.asList(0.1f, 0.3f, 0.5f));
        SparseEmbedding sparseEmbedding2 =
                new SparseEmbedding(Arrays.asList(2L, 4L, 6L), Arrays.asList(0.2f, 0.4f, 0.6f));

        TextSegment textSegment1 = TextSegment.from("document about cloud technology");
        TextSegment textSegment2 = TextSegment.from("document about cloud science");

        embeddingStore.addAllHybrid(
                Arrays.asList("cloud_id1", "cloud_id2"),
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
        assertThat(searchResult.matches().get(0).embedded().text()).isEqualTo("document about cloud technology");
    }

    @Test
    void should_perform_sparse_search_in_cloud() {
        // given
        SparseEmbedding sparseEmbedding1 =
                new SparseEmbedding(Arrays.asList(1L, 3L, 5L), Arrays.asList(0.1f, 0.3f, 0.5f));
        SparseEmbedding sparseEmbedding2 =
                new SparseEmbedding(Arrays.asList(2L, 4L, 6L), Arrays.asList(0.2f, 0.4f, 0.6f));

        TextSegment textSegment1 = TextSegment.from("cloud document about technology");
        TextSegment textSegment2 = TextSegment.from("cloud document about science");

        embeddingStore.addAllSparse(
                Arrays.asList("cloud_sparse_id1", "cloud_sparse_id2"),
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
        assertThat(searchResult.matches().get(0).embedded().text()).isEqualTo("cloud document about technology");
    }
}
