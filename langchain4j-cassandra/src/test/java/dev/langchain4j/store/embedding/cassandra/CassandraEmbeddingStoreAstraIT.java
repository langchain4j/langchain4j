package dev.langchain4j.store.embedding.cassandra;

import com.dtsx.astra.sdk.AstraDBAdmin;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.UUID;

import static com.dtsx.astra.sdk.cassio.CassandraSimilarityMetric.COSINE;
import static com.dtsx.astra.sdk.utils.TestUtils.TEST_REGION;
import static com.dtsx.astra.sdk.utils.TestUtils.getAstraToken;

/**
 * Integration test where Cassandra is running in AstraDB (dbaas).
 */
@EnabledIfEnvironmentVariable(named = "ASTRA_DB_APPLICATION_TOKEN", matches = "Astra.*")
class CassandraEmbeddingStoreAstraIT extends CassandraEmbeddingStoreIT {

    /**
     * Initializing the embedding store to work with Saas ASTRA DB.
     *
     * @return
     *      embedding store.
     */
    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        if (embeddingStore == null) {
            // Create if not exists
            UUID dbId = new AstraDBAdmin((getAstraToken())).createDatabase("test_langchain4j");
            embeddingStore = CassandraEmbeddingStore.builderAstra()
                    .token(getAstraToken())
                    .databaseId(dbId)
                    .databaseRegion(TEST_REGION)
                    .keyspace(KEYSPACE)
                    .table(TEST_INDEX)
                    .dimension(embeddingModel().dimension())
                    .metric(COSINE)
                    .build();
        }
        return embeddingStore;
    }
}
