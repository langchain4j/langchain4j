package dev.langchain4j.store.embedding.redis;

import com.redis.testcontainers.RedisContainer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisAccessControlException;
import redis.clients.jedis.exceptions.JedisDataException;

import static com.redis.testcontainers.RedisStackContainer.DEFAULT_IMAGE_NAME;
import static com.redis.testcontainers.RedisStackContainer.DEFAULT_TAG;
import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisEmbeddingStoreIT extends EmbeddingStoreIT {

    static RedisContainer redis = new RedisContainer(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG))
            .withEnv("REDIS_ARGS", "--requirepass redis-stack");

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
        embeddingStore = RedisEmbeddingStore.builder()
            .host(redis.getHost())
            .port(redis.getFirstMappedPort())
            // We can ignore username when using the `default` user.
            .password("redis-stack")
            .indexName(randomUUID())
            .prefix(randomUUID() + ":")
            .dimension(384)
            .metadataKeys(createMetadata().toMap().keySet())
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

    @Test
    public void testAuthPass() {
        assertThatNoException().isThrownBy(() -> RedisEmbeddingStore.builder()
                .host(redis.getHost())
                .port(redis.getFirstMappedPort())
                .password("redis-stack")
                .indexName(randomUUID())
                .dimension(384)
                .metadataKeys(createMetadata().toMap().keySet())
                .build());
    }

    @Test
    public void testUnauthorized() {
        assertThatThrownBy(() -> RedisEmbeddingStore.builder()
                        .host(redis.getHost())
                        .port(redis.getFirstMappedPort())
                        .indexName(randomUUID())
                        .dimension(384)
                        .metadataKeys(createMetadata().toMap().keySet())
                        .build())
                .isInstanceOf(JedisDataException.class)
                .hasMessageContaining("NOAUTH Authentication required.");
    }

    @Test
    public void testAuthenticationFailed() {
        assertThatThrownBy(() -> RedisEmbeddingStore.builder()
                        .port(redis.getFirstMappedPort())
                        .host(redis.getHost())
                        .password("wrong-password")
                        .indexName(randomUUID())
                        .dimension(384)
                        .metadataKeys(createMetadata().toMap().keySet())
                        .build())
                .isInstanceOf(JedisAccessControlException.class)
                .hasMessageContaining("WRONGPASS invalid username-password pair or user is disabled.");
    }
}
