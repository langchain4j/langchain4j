package dev.langchain4j.store.embedding.astradb;

import com.dtsx.astra.sdk.AstraDBCollection;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingRecord;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class AstraDbEmbeddingStoreTest {

    private AstraDbEmbeddingStore store = new AstraDbEmbeddingStore(mock(AstraDBCollection.class));

    @Test
    void addTest() {

        final Embedding embedding = new Embedding(new float[]{0.1f, 0.2f});
        final EmbeddingRecord<TextSegment> embeddingRecord = new EmbeddingRecord<TextSegment>(null, embedding, null);
        final String id = store.add(embeddingRecord);
        assertNotNull(id);

        final List<String> ids = store.addBatch(Collections.singletonList(embeddingRecord));
        assertNotNull(ids);
        assertFalse(ids.isEmpty());
    }

}
