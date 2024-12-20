package dev.langchain4j.store.embedding.redis;

import static com.redis.testcontainers.RedisStackContainer.DEFAULT_IMAGE_NAME;
import static com.redis.testcontainers.RedisStackContainer.DEFAULT_TAG;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.redis.RedisSchema.JSON_PATH_PREFIX;

import com.redis.testcontainers.RedisContainer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import redis.clients.jedis.search.schemafields.TagField;

public class RedisEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    static RedisContainer redis = new RedisContainer(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    RedisEmbeddingStore embeddingStore = RedisEmbeddingStore.builder()
            .host(redis.getHost())
            .port(redis.getFirstMappedPort())
            .indexName(randomUUID())
            .prefix(randomUUID() + ":")
            .dimension(embeddingModel.dimension())
            .metadataConfig(
                    Map.of("type", TagField.of(JSON_PATH_PREFIX + "type").as("type")))
            .build();

    @BeforeAll
    static void beforeAll() {
        redis.start();
    }

    @AfterAll
    static void afterAll() {
        redis.stop();
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
