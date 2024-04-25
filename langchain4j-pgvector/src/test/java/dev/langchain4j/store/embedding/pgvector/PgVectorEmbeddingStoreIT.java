package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.*;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

@Testcontainers
public class PgVectorEmbeddingStoreIT extends EmbeddingStoreIT {

  @Container
  static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

  EmbeddingStore<TextSegment> embeddingStore;

  EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

  @BeforeEach
  void beforeEach() {
    embeddingStore = PgVectorEmbeddingStore.builder()
            .host(pgVector.getHost())
            .port(pgVector.getFirstMappedPort())
            .user("test")
            .password("test")
            .database("test")
            .table("test")
            .dimension(384)
            .dropTableFirst(true)
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
  void should_not_retrieve_embeddings_when_searching_by_filter() {
    Metadata metadata = createMetadata();

    TextSegment segment = TextSegment.from("hello", metadata);
    Embedding embedding = embeddingModel().embed(segment.text()).content();

    String id = embeddingStore().add(embedding, segment);
    assertThat(id).isNotBlank();

    {
      // Not returned.
      TextSegment altSegment = TextSegment.from("hello?");
      Embedding altEmbedding = embeddingModel().embed(altSegment.text()).content();
      embeddingStore().add(altEmbedding, segment);
    }

    awaitUntilPersisted();

    List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(embedding, 1);
    assertThat(relevant).hasSize(1);

    EmbeddingMatch<TextSegment> match = relevant.get(0);
    assertThat(match.score()).isCloseTo(1, withPercentage(1));
    assertThat(match.embeddingId()).isEqualTo(id);
    assertThat(match.embedding()).isEqualTo(embedding);

    assertThat(match.embedded().text()).isEqualTo(segment.text());

    assertThat(match.embedded().metadata().getString("string_empty")).isEqualTo("");
    assertThat(match.embedded().metadata().getString("string_space")).isEqualTo(" ");
    assertThat(match.embedded().metadata().getString("string_abc")).isEqualTo("abc");

    assertThat(match.embedded().metadata().getInteger("integer_min")).isEqualTo(Integer.MIN_VALUE);
    assertThat(match.embedded().metadata().getInteger("integer_minus_1")).isEqualTo(-1);
    assertThat(match.embedded().metadata().getInteger("integer_0")).isEqualTo(0);
    assertThat(match.embedded().metadata().getInteger("integer_1")).isEqualTo(1);
    assertThat(match.embedded().metadata().getInteger("integer_max")).isEqualTo(Integer.MAX_VALUE);

    assertThat(match.embedded().metadata().getLong("long_min")).isEqualTo(Long.MIN_VALUE);
    assertThat(match.embedded().metadata().getLong("long_minus_1")).isEqualTo(-1L);
    assertThat(match.embedded().metadata().getLong("long_0")).isEqualTo(0L);
    assertThat(match.embedded().metadata().getLong("long_1")).isEqualTo(1L);
    assertThat(match.embedded().metadata().getLong("long_max")).isEqualTo(Long.MAX_VALUE);

    assertThat(match.embedded().metadata().getFloat("float_min")).isEqualTo(-Float.MAX_VALUE);
    assertThat(match.embedded().metadata().getFloat("float_minus_1")).isEqualTo(-1f);
    assertThat(match.embedded().metadata().getFloat("float_0")).isEqualTo(Float.MIN_VALUE);
    assertThat(match.embedded().metadata().getFloat("float_1")).isEqualTo(1f);
    assertThat(match.embedded().metadata().getFloat("float_123")).isEqualTo(1.23456789f);
    assertThat(match.embedded().metadata().getFloat("float_max")).isEqualTo(Float.MAX_VALUE);

    assertThat(match.embedded().metadata().getDouble("double_minus_1")).isEqualTo(-1d);
    assertThat(match.embedded().metadata().getDouble("double_0")).isEqualTo(Double.MIN_VALUE);
    assertThat(match.embedded().metadata().getDouble("double_1")).isEqualTo(1d);
    assertThat(match.embedded().metadata().getDouble("double_123")).isEqualTo(1.23456789d);

    // new API
    assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
            .queryEmbedding(embedding)
            .maxResults(1).filter(new IsGreaterThan("long_1", 0))
            .build()).matches()).isEqualTo(relevant);

    assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
            .queryEmbedding(embedding)
            .maxResults(1).filter(new IsGreaterThanOrEqualTo("long_1", 0))
            .build()).matches()).isEqualTo(relevant);

    assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
            .queryEmbedding(embedding)
            .maxResults(1).filter(new IsEqualTo("float_0", Float.MIN_VALUE))
            .build()).matches()).isEqualTo(relevant);

    assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
            .queryEmbedding(embedding)
            .maxResults(1).filter(new IsLessThan("double_1", 2d))
            .build()).matches()).isEqualTo(relevant);

    assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
            .queryEmbedding(embedding)
            .maxResults(1).filter(new IsLessThanOrEqualTo("double_1", 2d))
            .build()).matches()).isEqualTo(relevant);

    assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
            .queryEmbedding(embedding)
            .maxResults(1).filter(new IsIn("integer_0", Arrays.asList(0, 1)))
            .build()).matches()).isEqualTo(relevant);

    assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
            .queryEmbedding(embedding)
            .maxResults(1).filter(new IsNotIn("double_1", Arrays.asList(3d, 2d)))
            .build()).matches()).isEqualTo(relevant);

    assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
            .queryEmbedding(embedding)
            .maxResults(1).filter(new And(new IsNotIn("double_1", Arrays.asList(3d, 2d)), new IsIn("double_1", Arrays.asList(1d, 2d))))
            .build()).matches()).isEqualTo(relevant);
  }

  @Test
  void should_remove_embeddings() {
    Embedding embedding = embeddingModel().embed("hello").content();

    String id = embeddingStore().add(embedding);
    assertThat(id).isNotBlank();

    embeddingStore.remove(id);
    List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(embedding, 1);
    assertThat(relevant).isEmpty();
  }
}
