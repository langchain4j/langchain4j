package dev.langchain4j.store.embedding.elasticsearch;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * disabled default, because this need local deployment of Elasticsearch
 */
@Disabled
class ElasticsearchEmbeddingStoreImplTest {

    private final EmbeddingStore<TextSegment> store = new ElasticsearchEmbeddingStoreImpl(
            "http://localhost:9200",
            null,
            null,
            null,
            "test-index");

    @Test
    void testAdd() {
        // test add without id
        String id = store.add(Embedding.from(Arrays.asList(0.50f, 0.85f, 0.760f, 0.24f)),
                TextSegment.from("test string", Metadata.metadata("field", "value")));
        System.out.println("id=" + id);

        // test add with id
        String selfId = Utils.randomUUID();
        store.add(selfId, Embedding.from(Arrays.asList(0.80f, 0.45f, 0.89f, 0.24f)));
        System.out.println("id=" + selfId);
    }

    @Test
    void testAddAll() {
        // test add All Method without embedded
        List<String> ids = store.addAll(Arrays.asList(
                Embedding.from(Arrays.asList(0.3f, 0.87f, 0.90f, 0.24f)),
                Embedding.from(Arrays.asList(0.54f, 0.34f, 0.67f, 0.24f)),
                Embedding.from(Arrays.asList(0.80f, 0.45f, 0.779f, 0.5556f))
        ));
        System.out.println("ids=" + ids);

        // test add all method with embedded
        ids = store.addAll(Arrays.asList(
                Embedding.from(Arrays.asList(0.3f, 0.87f, 0.90f, 0.24f)),
                Embedding.from(Arrays.asList(0.54f, 0.34f, 0.67f, 0.24f)),
                Embedding.from(Arrays.asList(0.80f, 0.45f, 0.779f, 0.5556f))
        ), Arrays.asList(
                TextSegment.from("testString1", Metadata.metadata("field1", "value1")),
                TextSegment.from("testString2", Metadata.metadata("field2", "value2")),
                TextSegment.from("testingString3", Metadata.metadata("field3", "value3"))
        ));
        System.out.println("ids=" + ids);
    }

    @Test
    void testAddEmpty() {
        // see log
        store.addAll(Collections.emptyList());
    }

    @Test
    void testAddNotEqualSizeEmbeddingAndEmbedded() {
        Throwable ex = Assertions.assertThrows(IllegalArgumentException.class, () -> store.addAll(Arrays.asList(
                Embedding.from(Arrays.asList(0.3f, 0.87f, 0.90f, 0.24f)),
                Embedding.from(Arrays.asList(0.54f, 0.34f, 0.67f, 0.24f, 0.55f)),
                Embedding.from(Arrays.asList(0.80f, 0.45f, 0.779f, 0.5556f))
        ), Arrays.asList(
                TextSegment.from("testString1", Metadata.metadata("field1", "value1")),
                TextSegment.from("testString2", Metadata.metadata("field2", "value2"))
        )));
        Assertions.assertEquals("embeddings size is not equal to embedded size", ex.getMessage());
    }

    @Test
    void testFindRelevant() {
        List<EmbeddingMatch<TextSegment>> res = store.findRelevant(Embedding.from(Arrays.asList(0.80f, 0.70f, 0.90f, 0.55f)), 5);
        res.forEach(System.out::println);
    }
}
