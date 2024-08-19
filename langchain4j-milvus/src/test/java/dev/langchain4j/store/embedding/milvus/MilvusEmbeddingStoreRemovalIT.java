package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import static dev.langchain4j.internal.Utils.randomUUID;
import static io.milvus.common.clientenum.ConsistencyLevelEnum.STRONG;

@Testcontainers
class MilvusEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    @Container
    static MilvusContainer milvus = new MilvusContainer("milvusdb/milvus:v2.3.16");

    MilvusEmbeddingStore embeddingStore = MilvusEmbeddingStore.builder()
            .uri(milvus.getEndpoint())
            .collectionName("test_collection_" + randomUUID().replace("-", ""))
            .username(System.getenv("MILVUS_USERNAME"))
            .password(System.getenv("MILVUS_PASSWORD"))
            .consistencyLevel(STRONG)
            .dimension(384)
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
}