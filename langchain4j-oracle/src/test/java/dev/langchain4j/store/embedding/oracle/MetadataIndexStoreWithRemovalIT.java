package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

public class MetadataIndexStoreWithRemovalIT extends OracleEmbeddingStoreWithRemovalIT {
  private final OracleEmbeddingStore embeddingStore = CommonTestOperations.newEmbeddingStoreBuilder()
      .index(
          Index.jsonIndexBuilder()
              .createOption(CreateOption.CREATE_OR_REPLACE)
              .key("name", String.class, JSONIndexBuilder.Order.ASC)
              .build(),
          Index.jsonIndexBuilder()
              .createOption(CreateOption.CREATE_OR_REPLACE)
              .key("age", Float.class, JSONIndexBuilder.Order.ASC)
              .build())
      .build();
  @Override
  protected EmbeddingStore<TextSegment> embeddingStore() {
    return embeddingStore;
  }
}
