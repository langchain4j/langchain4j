package dev.langchain4j.store.embedding.cassandra;

import com.datastax.astra.sdk.AstraClient;
import com.datastax.oss.driver.api.core.CqlSession;
import com.dtsx.astra.sdk.utils.TestUtils;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static com.dtsx.astra.sdk.utils.TestUtils.getAstraToken;
import static com.dtsx.astra.sdk.utils.TestUtils.setupDatabase;

/**
 * Testing implementation of Embedding Store using AstraDB.
 */
class AstraDbEmbeddingStoreTest {

    public static final String TEST_DB       = "langchain4j";
    public static final String TEST_KEYSPACE = "langchain4j";
    public static final String TEST_INDEX    = "test_embedding_store";

    @Test
    @EnabledIfEnvironmentVariable(named = "ASTRA_DB_APPLICATION_TOKEN", matches = "Astra.*")
    public void testAddEmbeddingAndFindRelevant()
     throws Exception {

        // Read Token from environment variable ASTRA_DB_APPLICATION_TOKEN
        String astraToken  = getAstraToken();

        // Database will be created if not exist (can take 90 seconds on first run)
        String databaseId = setupDatabase(TEST_DB, TEST_KEYSPACE);

        // Store initialization
        AstraDbEmbeddingStore astraDbEmbeddingStore = new AstraDbEmbeddingStore(AstraDbEmbeddingConfiguration
                .builder().token(astraToken).databaseId(databaseId)
                .databaseRegion(TestUtils.TEST_REGION)
                .keyspace(TEST_KEYSPACE)
                .table(TEST_INDEX)
                .dimension(11).build());

        // Flushing Table before Start (idem potent)
        AstraClient.builder()
                .withToken(astraToken)
                .withCqlKeyspace(TEST_KEYSPACE)
                .withDatabaseId(databaseId)
                .withDatabaseRegion(TestUtils.TEST_REGION)
                .enableCql()
                .enableDownloadSecureConnectBundle()
                .build().cqlSession().execute("TRUNCATE TABLE " + TEST_INDEX);

        Embedding embedding = Embedding.from(new float[]{9.9F, 4.5F, 3.5F, 1.3F, 1.7F, 5.7F, 6.4F, 5.5F, 8.2F, 9.3F, 1.5F});
        TextSegment textSegment = TextSegment.textSegment("Text", Metadata.from("Key", "Value"));
        String added = astraDbEmbeddingStore.add(embedding, textSegment);
        Assertions.assertTrue(added != null && !added.isEmpty());

        Embedding refereceEmbedding = Embedding.from(new float[]{8.7F, 4.5F, 3.4F, 1.2F, 5.5F, 5.6F, 6.4F, 5.5F, 8.1F, 9.1F, 1.1F});
        List<EmbeddingMatch<TextSegment>> embeddingMatches = astraDbEmbeddingStore.findRelevant(refereceEmbedding, 10);
        Assertions.assertEquals(1, embeddingMatches.size());
    }

}
