package dev.langchain4j.store.embedding.pinecone;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.internal.Utils.randomUUID;

@EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY", matches = ".+")
public class PineconeEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    EmbeddingStore<TextSegment> embeddingStore = PineconeEmbeddingStore.builder()
            .apiKey(System.getenv("PINECONE_API_KEY"))
            .index("test")
            .nameSpace(randomUUID())
            .createIndex(PineconeServerlessIndexConfig.builder()
                    .cloud("AWS")
                    .region("us-east-1")
                    .dimension(embeddingModel.dimension())
                    .build())
            .build();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Test
    @Disabled("should be enabled once implemented")
    void should_remove_all_by_filter() {
    }

    @Test
    @Disabled("should be enabled once implemented")
    void should_fail_to_remove_all_by_filter_null() {
    }
}
