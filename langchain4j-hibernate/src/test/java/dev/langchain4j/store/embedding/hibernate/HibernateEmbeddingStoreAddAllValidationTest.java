package dev.langchain4j.store.embedding.hibernate;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreAddAllValidationTest;

class HibernateEmbeddingStoreAddAllValidationTest extends EmbeddingStoreAddAllValidationTest {

    @Override
    @SuppressWarnings("unchecked")
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return mock(HibernateEmbeddingStore.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    }
}
