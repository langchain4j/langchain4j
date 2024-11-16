package dev.langchain4j.store.embedding.azure.search;

import com.azure.search.documents.SearchClient;
import com.azure.search.documents.models.IndexDocumentsResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractAzureAiSearchEmbeddingStoreTest {

    @InjectMocks
    private AbstractAzureAiSearchEmbeddingStore abstractAzureAiSearchEmbeddingStore =
            new AbstractAzureAiSearchEmbeddingStore() {};

    @Mock
    private SearchClient searchClient;

    @Test
    void addElementsTest() {
        var indexDocumentsResult = mock(IndexDocumentsResult.class);

        when(searchClient.uploadDocuments(anyIterable()))
                .thenReturn(indexDocumentsResult);

        final Embedding embedding = new Embedding(new float[]{0.1f, 0.2f});
        final EmbeddingRecord<TextSegment> embeddingRecord = new EmbeddingRecord<TextSegment>(null, embedding, null);
        final String id = abstractAzureAiSearchEmbeddingStore.add(embeddingRecord);
        assertNotNull(id);

        final List<String> ids = abstractAzureAiSearchEmbeddingStore.addBatch(Collections.singletonList(embeddingRecord));
        assertNotNull(ids);
        assertFalse(ids.isEmpty());
    }
}
