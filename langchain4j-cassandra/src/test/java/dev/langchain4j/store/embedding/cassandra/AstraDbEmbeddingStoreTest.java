package dev.langchain4j.store.embedding.cassandra;

import com.datastax.astra.sdk.AstraClient;
import com.datastax.oss.driver.api.core.CqlSession;
import com.dtsx.astra.sdk.utils.TestUtils;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static com.dtsx.astra.sdk.utils.TestUtils.getAstraToken;
import static com.dtsx.astra.sdk.utils.TestUtils.setupDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testing implementation of Embedding Store using AstraDB.
 */
class AstraDbEmbeddingStoreTest {

    private static final String TEST_KEYSPACE = "langchain4j";
    private static final String TEST_INDEX = "test_embedding_store";

    /**
     * We want to trigger the test only if the expected variable
     * is present.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "ASTRA_DB_APPLICATION_TOKEN", matches = "Astra.*")
    void testAddEmbeddingAndFindRelevant() {
        String astraToken = getAstraToken();
        String databaseId = setupDatabase("langchain4j", TEST_KEYSPACE);

        // Flush Table for test to be idempotent
        truncateTable(databaseId, TEST_KEYSPACE, TEST_INDEX);

        // Create the Store with the builder
        AstraDbEmbeddingStore astraDbEmbeddingStore = new AstraDbEmbeddingStore(AstraDbEmbeddingConfiguration
                .builder()
                .token(astraToken)
                .databaseId(databaseId)
                .databaseRegion(TestUtils.TEST_REGION)
                .keyspace(TEST_KEYSPACE)
                .table(TEST_INDEX)
                .dimension(11)
                .build());

        Embedding embedding = Embedding.from(new float[]{9.9F, 4.5F, 3.5F, 1.3F, 1.7F, 5.7F, 6.4F, 5.5F, 8.2F, 9.3F, 1.5F});
        TextSegment textSegment = TextSegment.from("Text", Metadata.from("Key", "Value"));
        String id = astraDbEmbeddingStore.add(embedding, textSegment);
        assertTrue(id != null && !id.isEmpty());

        Embedding refereceEmbedding = Embedding.from(new float[]{8.7F, 4.5F, 3.4F, 1.2F, 5.5F, 5.6F, 6.4F, 5.5F, 8.1F, 9.1F, 1.1F});
        List<EmbeddingMatch<TextSegment>> embeddingMatches = astraDbEmbeddingStore.findRelevant(refereceEmbedding, 10);
        assertEquals(1, embeddingMatches.size());

        EmbeddingMatch<TextSegment> embeddingMatch = embeddingMatches.get(0);
        assertThat(embeddingMatch.score()).isBetween(0d, 1d);
        assertThat(embeddingMatch.embeddingId()).isEqualTo(id);
        assertThat(embeddingMatch.embedding()).isEqualTo(embedding);
        assertThat(embeddingMatch.embedded()).isEqualTo(textSegment);
    }

    private void truncateTable(String databaseId, String keyspace, String table) {
        try (AstraClient astraClient = AstraClient.builder()
                      .withToken(getAstraToken())
                      .withCqlKeyspace(keyspace)
                      .withDatabaseId(databaseId)
                      .withDatabaseRegion(TestUtils.TEST_REGION)
                      .enableCql()
                      .enableDownloadSecureConnectBundle()
                      .build()) {
            astraClient.cqlSession()
                       .execute("TRUNCATE TABLE " + table);
        }
    }
}
