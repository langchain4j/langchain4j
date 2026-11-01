package dev.langchain4j.store.embedding.hibernate;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreAddAllContract;
import dev.langchain4j.store.embedding.EmbeddingStoreRemoveAllContract;

class HibernateEmbeddingStoreContractTest implements EmbeddingStoreAddAllContract, EmbeddingStoreRemoveAllContract {

    @Override
    @SuppressWarnings("unchecked")
    public EmbeddingStore<TextSegment> embeddingStore() {
        return mock(HibernateEmbeddingStore.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    }
}
