package dev.langchain4j.store.memory.chat.cassandra;

import com.dtsx.astra.sdk.AstraDBAdmin;
import com.dtsx.astra.sdk.db.domain.CloudProviderType;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.UUID;

import static com.dtsx.astra.sdk.utils.TestUtils.TEST_REGION;
import static com.dtsx.astra.sdk.utils.TestUtils.getAstraToken;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test Cassandra Chat Memory Store with a Saas DB.
 */
@EnabledIfEnvironmentVariable(named = "ASTRA_DB_APPLICATION_TOKEN", matches = "Astra.*")
class CassandraChatMemoryStoreAstraIT extends CassandraChatMemoryStoreTestSupport {

    static final String DB = "test_langchain4j";
    static String token;
    static UUID dbId;

    @Override
    void createDatabase() {
        token = getAstraToken();
        assertThat(token).isNotNull();
        dbId = new AstraDBAdmin(token).createDatabase(DB, CloudProviderType.GCP, "us-east1");
        assertThat(dbId).isNotNull();
    }

    @Override
    CassandraChatMemoryStore createChatMemoryStore() {
        return CassandraChatMemoryStore.builderAstra()
                .token(getAstraToken())
                .databaseId(dbId)
                .databaseRegion(TEST_REGION)
                .keyspace(KEYSPACE)
                .build();
    }

}
