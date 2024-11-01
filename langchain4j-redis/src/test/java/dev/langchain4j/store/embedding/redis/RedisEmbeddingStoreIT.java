package dev.langchain4j.store.embedding.redis;

import com.redis.testcontainers.RedisStackContainer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.redis.testcontainers.RedisStackContainer.DEFAULT_IMAGE_NAME;
import static com.redis.testcontainers.RedisStackContainer.DEFAULT_TAG;
import static dev.langchain4j.internal.Utils.randomUUID;

class RedisEmbeddingStoreIT extends EmbeddingStoreIT {

    static RedisStackContainer redis = new RedisStackContainer(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));

    EmbeddingStore<TextSegment> embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void beforeAll() {
        redis.start();
    }

    @AfterAll
    static void afterAll() {
        redis.stop();
    }

    @Override
    protected void clearStore() {
        Map<String, SchemaField> metadataConfig = new HashMap<>();
        Map<String, Object> metadataMap = createMetadata().toMap();

        List<Class<? extends Number>> numericPrefix = Arrays.asList(Integer.class, Long.class, Float.class, Double.class);
        metadataMap.forEach((key, value) -> {
            if (numericPrefix.stream().anyMatch(type -> type.isAssignableFrom(value.getClass()))) {
                metadataConfig.put(key, NumericField.of("$." + key).as(key));
            } else {
                metadataConfig.put(key, TextField.of("$." + key).as(key).weight(1.0));
            }
        });

        embeddingStore = RedisEmbeddingStore.builder()
            .host(redis.getHost())
            .port(redis.getFirstMappedPort())
            .indexName(randomUUID())
            .prefix(randomUUID() + ":")
            .dimension(384)
            .metadataConfig(metadataConfig)
            .build();
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
