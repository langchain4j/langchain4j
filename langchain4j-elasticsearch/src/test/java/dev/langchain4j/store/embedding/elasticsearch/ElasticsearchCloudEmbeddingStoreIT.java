package dev.langchain4j.store.embedding.elasticsearch;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import lombok.SneakyThrows;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.internal.Utils.randomUUID;

@EnabledIfEnvironmentVariable(named = "ELASTICSEARCH_CLOUD_API_KEY", matches = ".+")
class ElasticsearchCloudEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    EmbeddingStore<TextSegment> embeddingStore = ElasticsearchEmbeddingStore.builder()
            .serverUrl(System.getenv("ELASTICSEARCH_CLOUD_URL"))
            .apiKey(System.getenv("ELASTICSEARCH_CLOUD_API_KEY"))
            .indexName(randomUUID())
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

    @Override
    protected void ensureStoreIsEmpty() {
        // TODO fix
    }

    @Override
    @SneakyThrows
    protected void awaitUntilPersisted() {
        Thread.sleep(1000);
    }
}
