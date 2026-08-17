package dev.langchain4j.store.embedding.milvus;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreAddAllValidationTest;

class MilvusEmbeddingStoreAddAllValidationTest extends EmbeddingStoreAddAllValidationTest {

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return mock(MilvusEmbeddingStore.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    }
}
