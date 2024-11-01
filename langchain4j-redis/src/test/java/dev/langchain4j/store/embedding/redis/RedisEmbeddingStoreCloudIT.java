package dev.langchain4j.store.embedding.redis;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.randomUUID;

@EnabledIfEnvironmentVariable(named = "REDIS_CLOUD_URI", matches = ".+")
class RedisEmbeddingStoreCloudIT extends EmbeddingStoreIT {

    RedisEmbeddingStore embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected void clearStore() {
        Map<String, SchemaField> metadataConfig = new HashMap<>();
        Map<String, Object> metadataMap = createMetadata().toMap();

        List<Class<? extends Number>> numericPrefix = Arrays.asList(Integer.class, Long.class, Float.class, Double.class);
        metadataMap.forEach((key, value) -> {
            if (numericPrefix.stream().anyMatch(type -> type.isAssignableFrom(value.getClass()))) {
                metadataConfig.put(key, NumericField.of("$." + key).as(key));
            } else {
                metadataConfig.put(key, TagField.of("$." + key).as(key));
            }
        });

        embeddingStore = RedisEmbeddingStore.builder()
            .uri(System.getenv("REDIS_CLOUD_URI"))
            .indexName(randomUUID())
            .dimension(embeddingModel.dimension())
            .metadataConfig(metadataConfig)
            .build();
    }

    @AfterEach
    void afterEach() {
        embeddingStore.close();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
