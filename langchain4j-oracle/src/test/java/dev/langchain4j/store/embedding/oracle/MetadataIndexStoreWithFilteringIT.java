package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

public class MetadataIndexStoreWithFilteringIT extends OracleEmbeddingStoreWithFilteringIT {

  private final OracleEmbeddingStore embeddingStore = CommonTestOperations.newEmbeddingStoreBuilder()
      .index(
          JSONIndex.builder()
              .createOption(CreateOption.CREATE_OR_REPLACE)
              .key("name", String.class, JSONIndex.Builder.Order.ASC),
          JSONIndex.builder()
              .createOption(CreateOption.CREATE_OR_REPLACE)
              .key("age", Float.class, JSONIndex.Builder.Order.ASC))
      .build();
  @Override
  protected EmbeddingStore<TextSegment> embeddingStore() {
    return embeddingStore;
  }
}
