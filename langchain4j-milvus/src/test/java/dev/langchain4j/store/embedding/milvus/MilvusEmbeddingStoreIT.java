package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import java.util.List;

import static io.milvus.common.clientenum.ConsistencyLevelEnum.STRONG;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MilvusEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    private static final String COLLECTION_NAME = "test_collection";

    @Container
    private static final MilvusContainer milvus = new MilvusContainer("milvusdb/milvus:v2.3.16");

    MilvusEmbeddingStore embeddingStore = MilvusEmbeddingStore.builder()
            .uri(milvus.getEndpoint())
            .collectionName(COLLECTION_NAME)
            .consistencyLevel(STRONG)
            .username(System.getenv("MILVUS_USERNAME"))
            .password(System.getenv("MILVUS_PASSWORD"))
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

        EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
                .host(milvus.getHost())
                .port(milvus.getMappedPort(19530))
                .collectionName(COLLECTION_NAME)
                .consistencyLevel(STRONG)
                .dimension(384)
                .retrieveEmbeddingsOnSearch(false)
                .build();

        Embedding firstEmbedding = embeddingModel.embed("hello").content();
        Embedding secondEmbedding = embeddingModel.embed("hi").content();
        embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .build()).matches();
        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).embedding()).isNull();
        assertThat(matches.get(1).embedding()).isNull();
    }
}