package dev.langchain4j.store.embedding.elasticsearch;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;

import static dev.langchain4j.internal.Utils.randomUUID;

@Disabled("needs Elasticsearch to be running locally")
class ElasticsearchEmbeddingStoreIT extends EmbeddingStoreIT {

    /**
     * First start elasticsearch locally:
     * docker pull docker.elastic.co/elasticsearch/elasticsearch:8.9.0
     * docker run -d -p 9200:9200 -p 9300:9300 -e discovery.type=single-node -e xpack.security.enabled=false docker.elastic.co/elasticsearch/elasticsearch:8.9.0
     */

    EmbeddingStore<TextSegment> embeddingStore = ElasticsearchEmbeddingStore.builder()
            .serverUrl("http://localhost:9200")
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
