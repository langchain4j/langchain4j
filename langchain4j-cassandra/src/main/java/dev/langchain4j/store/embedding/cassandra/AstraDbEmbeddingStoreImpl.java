package dev.langchain4j.store.embedding.cassandra;

import com.datastax.astra.sdk.AstraClient;
import com.datastax.oss.driver.api.core.CqlSession;
import com.dtsx.astra.sdk.cassio.MetadataVectorCassandraTable;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * Implementation of {@link EmbeddingStore} using Cassandra AstraDB.
 *
 * @see EmbeddingStore
 * @see MetadataVectorCassandraTable
 */
public class AstraDbEmbeddingStoreImpl extends CassandraEmbeddingStoreSupport {

    /**
     * Build the store from the configuration.
     *
     * @param config
     *      configuration
     */
    public AstraDbEmbeddingStoreImpl(AstraDbEmbeddingConfiguration config) {
        CqlSession cqlSession = AstraClient.builder()
                .withToken(config.getToken())
                .withCqlKeyspace(config.getKeyspace())
                .withDatabaseId(config.getDatabaseId())
                .withDatabaseRegion(config.getDatabaseRegion())
                .enableCql()
                .enableDownloadSecureConnectBundle()
                .build().cqlSession();
        embeddingTable = new MetadataVectorCassandraTable(cqlSession, config.getKeyspace(), config.getTable(), config.getDimension());
    }

}
