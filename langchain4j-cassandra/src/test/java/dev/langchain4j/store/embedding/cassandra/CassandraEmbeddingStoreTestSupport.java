package dev.langchain4j.store.embedding.cassandra;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.astradb.AstraDbEmbeddingStore;
import dev.langchain4j.store.memory.chat.cassandra.CassandraChatMemoryStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class CassandraEmbeddingStoreTestSupport {

    protected static final String KEYSPACE = "langchain4j";

    protected static final String TEST_INDEX = "test_embedding_store";

    protected static CassandraEmbeddingStore embeddingStore;

    @Test
    @Order(1)
    @DisplayName("1. Should create a database")
    void shouldInitializeDatabase() {
        createDatabase();
    }

    @Test
    @Order(2)
    @DisplayName("2. Connection to the database")
    void shouldConnectToDatabase() {
        embeddingStore = createEmbeddingStore();
        Assertions.assertTrue(embeddingStore.getCassandraSession()
                .getMetadata()
                .getKeyspace(KEYSPACE)
                .isPresent());
    }

    @Test
    @Order(3)
    @DisplayName("3. EmbeddingStore initialization (table)")
    void shouldCreateEmbeddingStore() {
        embeddingStore.create();
        // Table exists
        Assertions.assertTrue(embeddingStore.getCassandraSession()
                .refreshSchema()
                .getKeyspace(KEYSPACE).get()
                .getTable(TEST_INDEX).isPresent());
        embeddingStore.clear();
    }

    @Test
    @Order(4)
    @DisplayName("4. Insert Items ")
    void testAddEmbeddingAndFindRelevant() {

        Embedding embedding = Embedding.from(new float[]{9.9F, 4.5F, 3.5F, 1.3F, 1.7F, 5.7F, 6.4F, 5.5F, 8.2F, 9.3F, 1.5F});
        TextSegment textSegment = TextSegment.from("Text", Metadata.from("Key", "Value"));
        String id = embeddingStore.add(embedding, textSegment);
        assertTrue(id != null && !id.isEmpty());

        Embedding refereceEmbedding = Embedding.from(new float[]{8.7F, 4.5F, 3.4F, 1.2F, 5.5F, 5.6F, 6.4F, 5.5F, 8.1F, 9.1F, 1.1F});
        List<EmbeddingMatch<TextSegment>> embeddingMatches = embeddingStore.findRelevant(refereceEmbedding, 1);
        assertEquals(1, embeddingMatches.size());

        EmbeddingMatch<TextSegment> embeddingMatch = embeddingMatches.get(0);
        assertThat(embeddingMatch.score()).isBetween(0d, 1d);
        assertThat(embeddingMatch.embeddingId()).isEqualTo(id);
        assertThat(embeddingMatch.embedding()).isEqualTo(embedding);
        assertThat(embeddingMatch.embedded()).isEqualTo(textSegment);
    }


    abstract void createDatabase();

    abstract CassandraEmbeddingStore createEmbeddingStore();

}
