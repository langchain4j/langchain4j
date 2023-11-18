package dev.langchain4j.store.embedding.cassandra;

import com.dtsx.astra.sdk.cassio.SimilarityMetric;
import com.dtsx.astra.sdk.utils.AstraEnvironment;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static com.dtsx.astra.sdk.utils.TestUtils.TEST_REGION;
import static com.dtsx.astra.sdk.utils.TestUtils.getAstraToken;
import static com.dtsx.astra.sdk.utils.TestUtils.setupDatabase;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Testing implementation of Embedding Store using AstraDB.
 */
@EnabledIfEnvironmentVariable(named = "ASTRA_DB_APPLICATION_TOKEN", matches = "Astra.*")
class CassandraEmbeddingStoreWithinAstraIT extends AbstractEmbeddingStoreTestSupport {


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
    CassandraEmbeddingStore createEmbeddingStore() {
        return CassandraEmbeddingStore.builderAstra()
                .token(token)
                .databaseId(dbId)
                .databaseRegion(TEST_REGION)
                .keyspace(KEYSPACE)
                .table(TEST_INDEX)
                .dimension(11)
                .metric(SimilarityMetric.COS)
                .build();
    }

}
