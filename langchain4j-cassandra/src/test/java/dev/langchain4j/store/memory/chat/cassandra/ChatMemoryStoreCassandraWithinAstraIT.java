package dev.langchain4j.store.memory.chat.cassandra;

import com.dtsx.astra.sdk.utils.AstraEnvironment;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static com.dtsx.astra.sdk.utils.TestUtils.TEST_REGION;
import static com.dtsx.astra.sdk.utils.TestUtils.getAstraToken;
import static com.dtsx.astra.sdk.utils.TestUtils.setupDatabase;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test Cassandra Chat Memory Store with a Saas DB.
 */
@EnabledIfEnvironmentVariable(named = "ASTRA_DB_APPLICATION_TOKEN", matches = "Astra.*")
class ChatMemoryStoreCassandraWithinAstraIT extends AbstractChatMemoryStoreTestSupport {

    static final String DB = "test_langchain4j";
    static String token;
    static String dbId;

    @Override
    void createDatabase() {
        token = getAstraToken();
        assertNotNull(token);
        dbId = setupDatabase(AstraEnvironment.PROD, DB, KEYSPACE, true);
        assertNotNull(dbId);
    }

    @Override
    CassandraChatMemoryStore createChatMemoryStore() {
        return CassandraChatMemoryStore.builderAstra()
                .token(token)
                .databaseId(dbId)
                .databaseRegion(TEST_REGION)
                .keyspace(KEYSPACE)
                .build();
    }

}
