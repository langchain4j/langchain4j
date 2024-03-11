package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class MilvusEmbeddingStoreCloudIT extends EmbeddingStoreWithFilteringIT {

    private static final String COLLECTION_NAME = "test_collection";

    MilvusEmbeddingStore embeddingStore = MilvusEmbeddingStore.builder()
            .uri("https://in03-d11858f677102da.api.gcp-us-west1.zillizcloud.com") // TODO env var
            .token(System.getenv("MILVUS_API_KEY"))
            .collectionName(COLLECTION_NAME)
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
                .uri("https://in03-d11858f677102da.api.gcp-us-west1.zillizcloud.com") // TODO env var
                .token(System.getenv("MILVUS_API_KEY"))
                .collectionName("test")
                .dimension(384)
                .retrieveEmbeddingsOnSearch(retrieveEmbeddingsOnSearch)
                .build();

        Embedding firstEmbedding = embeddingModel.embed("hello").content();
        Embedding secondEmbedding = embeddingModel.embed("hi").content();
        embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);
        assertThat(relevant.get(0).embedding()).isNull();
        assertThat(relevant.get(1).embedding()).isNull();
    }
}