package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

public class EmbeddingIndexStoreWithFilteringIT extends OracleEmbeddingStoreWithFilteringIT {

    private final OracleEmbeddingStore embeddingStore = CommonTestOperations.newEmbeddingStoreBuilder()
            .index(Index.ivfIndexBuilder()
                    .createOption(CreateOption.CREATE_OR_REPLACE)
                    .build())
            .build();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }
}
