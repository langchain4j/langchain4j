package dev.langchain4j.store.embedding.milvus;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import io.milvus.v2.common.ConsistencyLevel;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

@Testcontainers
class MilvusEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    @Container
    static MilvusContainer milvus = new MilvusContainer("milvusdb/milvus:v2.5.8");

    MilvusEmbeddingStore embeddingStore = MilvusEmbeddingStore.builder()
            .uri(milvus.getEndpoint())
            .collectionName("test_collection_" + randomUUID().replace("-", ""))
            .username(System.getenv("MILVUS_USERNAME"))
            .password(System.getenv("MILVUS_PASSWORD"))
            .consistencyLevel(ConsistencyLevel.STRONG)
            .dimension(384)
            .sparseVectorFieldName("sparse_vector_field")
            .sparseMode(MilvusEmbeddingStore.MilvusSparseMode.CUSTOM)
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

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

        MilvusEmbeddingSearchRequest milvusEmbeddingSearchRequest = MilvusEmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(embeddingModel().embed("test").content())
                .maxResults(1000)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(milvusEmbeddingSearchRequest);

        return searchResult.matches();
    }


    @Test
    void should_remove_hybrid_embeddings() {
        // given
        Embedding denseEmbedding =
                embeddingModel.embed("test document for removal").content();
        SparseEmbedding sparseEmbedding =
                new SparseEmbedding(new long[]{1L, 3L, 5L}, new float[]{0.1f, 0.3f, 0.5f});
        TextSegment textSegment = TextSegment.from("document to be removed");

        String id = "removal_test_id";

        embeddingStore.addAllHybrid(
                Arrays.asList(id),
                Arrays.asList(denseEmbedding),
                Arrays.asList(sparseEmbedding),
                Arrays.asList(textSegment));

        // when
        embeddingStore.removeAll(Arrays.asList(id));

        // then - should not find the removed embedding
        MilvusEmbeddingSearchRequest searchRequest = MilvusEmbeddingSearchRequest.milvusBuilder()
                .queryEmbedding(denseEmbedding)
                .searchMode(MilvusEmbeddingSearchMode.DENSE) // dense search
                .maxResults(10)
                .build();

        var searchResult = embeddingStore.search(searchRequest);
        assertThat(searchResult.matches()).isEmpty();
    }
}
