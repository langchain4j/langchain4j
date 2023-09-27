package dev.langchain4j.store.embedding.cassandra;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Work with Cassandra Embedding Store.
 */
class CassandraEmbeddingStoreTest {

    @Test
    @Disabled("To run this test, you must have a local Cassandra instance, a docker-compose is provided")
    public void testAddEmbeddingAndFindRelevant() {

        CassandraEmbeddingStore cassandraEmbeddingStore = initStore();

        Embedding embedding = Embedding.from(new float[]{9.9F, 4.5F, 3.5F, 1.3F, 1.7F, 5.7F, 6.4F, 5.5F, 8.2F, 9.3F, 1.5F});
        TextSegment textSegment = TextSegment.from("Text", Metadata.from("Key", "Value"));
        String id = cassandraEmbeddingStore.add(embedding, textSegment);
        assertTrue(id != null && !id.isEmpty());

        Embedding refereceEmbedding = Embedding.from(new float[]{8.7F, 4.5F, 3.4F, 1.2F, 5.5F, 5.6F, 6.4F, 5.5F, 8.1F, 9.1F, 1.1F});
        List<EmbeddingMatch<TextSegment>> embeddingMatches = cassandraEmbeddingStore.findRelevant(refereceEmbedding, 1);
        assertEquals(1, embeddingMatches.size());

        EmbeddingMatch<TextSegment> embeddingMatch = embeddingMatches.get(0);
        assertThat(embeddingMatch.score()).isBetween(0d, 1d);
        assertThat(embeddingMatch.embeddingId()).isEqualTo(id);
        assertThat(embeddingMatch.embedding()).isEqualTo(embedding);
        assertThat(embeddingMatch.embedded()).isEqualTo(textSegment);
    }

    private CassandraEmbeddingStore initStore() {
        return CassandraEmbeddingStore.builder()
                .contactPoints("127.0.0.1")
                .port(9042)
                .localDataCenter("datacenter1")
                .table("langchain4j", "table_" + randomUUID().replace("-", ""))
                .vectorDimension(11)
                .build();
    }
}
