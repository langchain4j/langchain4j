package dev.langchain4j.store.embedding.redis;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@Disabled("needs Redis running locally")
class RedisEmbeddingStoreTest {

    /**
     * First start Redis locally:
     * docker pull redis/redis-stack:latest
     * docker run -d -p 6379:6379 -p 8001:8001 redis/redis-stack:latest
     */

    private final EmbeddingStore<TextSegment> store = new RedisEmbeddingStore(
            "localhost",
            6379,
            "default",
            "password",
            4,
            singletonList("field")
    );

    @Test
    void testAdd() {
        // test add without id
        String id = store.add(Embedding.from(asList(0.50f, 0.85f, 0.760f, 0.24f)),
                TextSegment.from("test string", Metadata.from("field", "value")));
        System.out.println("id=" + id);

        // test add with id
        String selfId = Utils.randomUUID();
        store.add(selfId, Embedding.from(asList(0.80f, 0.45f, 0.89f, 0.24f)));
        System.out.println("id=" + selfId);
    }

    @Test
    void testAddAll() {
        // test add All Method without embedded
        List<String> ids = store.addAll(asList(
                Embedding.from(asList(0.3f, 0.87f, 0.90f, 0.24f)),
                Embedding.from(asList(0.54f, 0.34f, 0.67f, 0.24f)),
                Embedding.from(asList(0.80f, 0.45f, 0.779f, 0.5556f))
        ));
        System.out.println("ids=" + ids);

        // test add all method with embedded
        ids = store.addAll(asList(
                Embedding.from(asList(0.3f, 0.87f, 0.90f, 0.24f)),
                Embedding.from(asList(0.54f, 0.34f, 0.67f, 0.24f)),
                Embedding.from(asList(0.80f, 0.45f, 0.779f, 0.5556f))
        ), asList(
                TextSegment.from("testString1", Metadata.from("field", "value1")),
                TextSegment.from("testString2", Metadata.from("field", "value2")),
                TextSegment.from("testingString3", Metadata.from("field", "value3"))
        ));
        System.out.println("ids=" + ids);
    }

    @Test
    void testAddEmpty() {
        // see log
        store.addAll(emptyList());
    }

    @Test
    void testFindRelevant() {
        List<EmbeddingMatch<TextSegment>> res = store.findRelevant(Embedding.from(asList(0.80f, 0.45f, 0.89f, 0.24f)), 5);
        res.forEach(System.out::println);
    }

    @Test
    void testScore() {
        String id = store.add(Embedding.from(asList(0.50f, 0.85f, 0.760f, 0.24f)),
                TextSegment.from("test string", Metadata.from("field", "value")));
        System.out.println("id=" + id);

        // use the same embedding to search
        List<EmbeddingMatch<TextSegment>> res = store.findRelevant(Embedding.from(asList(0.50f, 0.85f, 0.760f, 0.24f)), 1);
        res.forEach(System.out::println);

        // the result embeddingMatch score is 5.96046447754E-8, but expected is 1 because they are same vectors.
    }
}
