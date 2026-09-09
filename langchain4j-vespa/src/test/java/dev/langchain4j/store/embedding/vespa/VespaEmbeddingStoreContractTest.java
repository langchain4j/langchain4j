package dev.langchain4j.store.embedding.vespa;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreAddAllContract;

class VespaEmbeddingStoreContractTest implements EmbeddingStoreAddAllContract {

    @Override
    public EmbeddingStore<TextSegment> embeddingStore() {
        return mock(VespaEmbeddingStore.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    }
}
