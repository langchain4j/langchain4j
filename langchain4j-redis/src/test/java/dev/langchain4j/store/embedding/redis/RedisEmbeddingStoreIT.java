package dev.langchain4j.store.embedding.redis;

import com.redis.testcontainers.RedisContainer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.redis.testcontainers.RedisStackContainer.DEFAULT_IMAGE_NAME;
import static com.redis.testcontainers.RedisStackContainer.DEFAULT_TAG;
import static dev.langchain4j.internal.Utils.randomUUID;
import java.util.List;

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
        embeddingStore = createStore(randomUUID());
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
    void should_find_embedding_in_proper_store() {
       final EmbeddingStore<TextSegment> firstStore = createStore("first-store");
       final EmbeddingStore<TextSegment> secondStore = createStore("second-store");

       Embedding embedding = embeddingModel().embed("hello").content();

       String id = firstStore.add(embedding);
       assertThat(id).isNotBlank();

       awaitUntilPersisted();

       List<EmbeddingMatch<TextSegment>> relevant = firstStore.findRelevant(embedding, 10);
       assertThat(relevant).hasSize(1);
       assertThat(secondStore.findRelevant(embedding, 10)).hasSize(0);
       
       EmbeddingMatch<TextSegment> match = relevant.get(0);
       assertThat(match.score()).isCloseTo(1, withPercentage(1));
       assertThat(match.embeddingId()).isEqualTo(id);
       assertThat(match.embedding()).isEqualTo(embedding);
       assertThat(match.embedded()).isNull();

       // new API
       assertThat(firstStore.search(EmbeddingSearchRequest.builder()
               .queryEmbedding(embedding)
               .maxResults(10)
               .build()).matches()).isEqualTo(relevant);
       assertThat(secondStore.search(EmbeddingSearchRequest.builder()
               .queryEmbedding(embedding)
               .maxResults(10)
               .build()).matches()).hasSize(0);
    }
    
    private EmbeddingStore<TextSegment> createStore(String name) {
      return RedisEmbeddingStore.builder()
          .host(redis.getHost())
          .port(redis.getFirstMappedPort())
          .name(name)
          .dimension(384)
          .metadataKeys(createMetadata().toMap().keySet())
          .build();      
  }      
}
