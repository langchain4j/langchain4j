package dev.langchain4j.store.embedding.elastic;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * disabled default, to be changed by mockito
 */
@Disabled
class ElasticEmbeddingStoreImplTest {

    private final EmbeddingStore<TextSegment> store = new ElasticEmbeddingStoreImpl("http://localhost:9200", null, "test-index");

    @Test
    void testAdd() {
        String id = store.add(Embedding.from(Arrays.asList(0.50f, 0.85f, 0.760f)), TextSegment.from("test string", Metadata.metadata("field", "value")));
        System.out.println("id=" + id);
    }

    @Test
    void testAddAll() {
        // TODO
    }

    @Test
    void testFindRelevant() {
        List<EmbeddingMatch<TextSegment>> res = store.findRelevant(Embedding.from(Arrays.asList(0.80f, 0.70f, 0.90f)), 5);
        System.out.println(res);
    }
}
