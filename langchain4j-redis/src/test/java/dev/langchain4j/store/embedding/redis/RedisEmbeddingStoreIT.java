package dev.langchain4j.store.embedding.redis;

import com.redis.testcontainers.RedisContainer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;

import java.util.HashMap;
import java.util.Map;

import static com.redis.testcontainers.RedisStackContainer.DEFAULT_IMAGE_NAME;
import static com.redis.testcontainers.RedisStackContainer.DEFAULT_TAG;
import static dev.langchain4j.internal.Utils.randomUUID;

class RedisEmbeddingStoreIT extends EmbeddingStoreIT {

    static RedisContainer redis = new RedisContainer(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));

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
        try (JedisPooled jedis = new JedisPooled(redis.getHost(), redis.getFirstMappedPort())) {
            jedis.flushDB(); // TODO fix: why redis returns embeddings from different indexes?
        }

        Map<String, SchemaField> schemaFieldMap = new HashMap<>();
        Map<String, Object> metadataMap = createMetadata().toMap();

        metadataMap.forEach((key, value) -> {
            if (key.startsWith("integer") || key.startsWith("long") || key.startsWith("float") || key.startsWith("double")) {
                schemaFieldMap.put(key, NumericField.of("$." + key).as(key));
            } else {
                schemaFieldMap.put(key, TextField.of("$." + key).as(key).weight(1.0));
            }
        });

        embeddingStore = RedisEmbeddingStore.builder()
                .host(redis.getHost())
                .port(redis.getFirstMappedPort())
                .indexName(randomUUID())
                .dimension(embeddingModel.dimension())
                .metadataKeys(metadataMap.keySet())
                .schemaFiledMap(schemaFieldMap)
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

    @Override
    protected void ensureStoreIsReady() {
        // do nothing here
    }
}
