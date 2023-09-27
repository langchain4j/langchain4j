package dev.langchain4j.store.embedding.cassandra;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

/**
 * Work with Cassandra Embedding Store.
 */
class CassandraEmbeddingStoreTest {

    public static final String TEST_KEYSPACE = "langchain4j";
    public static final String TEST_INDEX    = "test_embedding_store";

    @Test
    @Disabled("To run this test, you must have a local Cassandra instance, a docker-compose is provided")
    public void testAddEmbeddingAndFindRelevant()
    throws Exception {

        CassandraEmbeddingStore cassandraEmbeddingStore = initStore();

        Embedding embedding = Embedding.from(new float[]{9.9F, 4.5F, 3.5F, 1.3F, 1.7F, 5.7F, 6.4F, 5.5F, 8.2F, 9.3F, 1.5F});
        TextSegment textSegment = TextSegment.textSegment("Text", Metadata.from("Key", "Value"));
        String added = cassandraEmbeddingStore.add(embedding, textSegment);
        Assertions.assertTrue(added != null && !added.isEmpty());

        Embedding refereceEmbedding = Embedding.from(new float[]{8.7F, 4.5F, 3.4F, 1.2F, 5.5F, 5.6F, 6.4F, 5.5F, 8.1F, 9.1F, 1.1F});
        List<EmbeddingMatch<TextSegment>> embeddingMatches = cassandraEmbeddingStore.findRelevant(refereceEmbedding, 10);
        Assertions.assertEquals(1, embeddingMatches.size());
    }

    private CassandraEmbeddingStore initStore()
    throws Exception {
        return CassandraEmbeddingStore
                .builder()
                .contactPoints("127.0.0.1")
                .port(9042)
                .localDataCenter("datacenter1")
                .table(TEST_KEYSPACE, TEST_INDEX)
                .vectorDimension(11)
                .build();
    }

}
