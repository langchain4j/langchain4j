package dev.langchain4j.store.embedding.cassandra;

import com.dtsx.astra.sdk.utils.TestUtils;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dtsx.astra.sdk.utils.TestUtils.readToken;
import static com.dtsx.astra.sdk.utils.TestUtils.setupDatabase;

/**
 * Testing implementation of Embedding Store using AstraDB.
 */
public class AstraDbEmbeddingStoreTest {

    public static final String TEST_DB       = "langchain4j";
    public static final String TEST_KEYSPACE = "langchain4j";
    public static final String TEST_INDEX    = "test_embedding_store";

    @Test
    @Disabled("To run this test, you must have an astra Account and Token (it is free astra.datastax.com)")
    public void testAddEmbeddingAndFindRelevant()
     throws Exception {

        // Store initialization
        AstraDbEmbeddingStore astraDbEmbeddingStore = initStore();

        Embedding embedding = Embedding.from(new float[]{9.9F, 4.5F, 3.5F, 1.3F, 1.7F, 5.7F, 6.4F, 5.5F, 8.2F, 9.3F, 1.5F});
        TextSegment textSegment = TextSegment.textSegment("Text", Metadata.from("Key", "Value"));
        String added = astraDbEmbeddingStore.add(embedding, textSegment);
        Assertions.assertTrue(added != null && !added.isEmpty());

        Embedding refereceEmbedding = Embedding.from(new float[]{8.7F, 4.5F, 3.4F, 1.2F, 5.5F, 5.6F, 6.4F, 5.5F, 8.1F, 9.1F, 1.1F});
        List<EmbeddingMatch<TextSegment>> embeddingMatches = astraDbEmbeddingStore.findRelevant(refereceEmbedding, 10);
        Assertions.assertEquals(1, embeddingMatches.size());
    }

    /**
     * To run this test, you must have an astra Account and Token.
     * Go to <a ref="https://astra.datastax.com">Astra</a> and create account for free.
     *
     * @return
     *      error
     * @throws Exception
     *      initialization error
     */
    private AstraDbEmbeddingStore initStore()
            throws Exception {

        // Read Token from environment variable ASTRA_DB_APPLICATION_TOKEN
        String astraToken  = readToken();

        // Database will be created if not exist (can take 90 seconds on first run)
        String databaseId = setupDatabase(TEST_DB, TEST_KEYSPACE);

        // Store initialization
        return new AstraDbEmbeddingStore(AstraDbEmbeddingConfiguration
                .builder().token(astraToken).databaseId(databaseId)
                .databaseRegion(TestUtils.TEST_REGION)
                .keyspace(TEST_KEYSPACE)
                .table(TEST_INDEX)
                .dimension(11).build());
    }
}
