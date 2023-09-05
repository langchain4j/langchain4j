package dev.langchain4j.store.embedding.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.dtsx.astra.sdk.cassio.MetadataVectorCassandraTable;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.net.InetSocketAddress;

/**
 * Implementation of {@link EmbeddingStore} using Cassandra AstraDB.
 *
 * @see EmbeddingStore
 * @see MetadataVectorCassandraTable
 */
public class CassandraEmbeddingStoreImpl extends CassandraEmbeddingStoreSupport {

    /**
     * Build the store from the configuration.
     *
     * @param config
     *      configuration
     */
    public CassandraEmbeddingStoreImpl(CassandraEmbeddingConfiguration config) {
        CqlSessionBuilder sessionBuilder = createCqlSessionBuilder(config);
        createKeyspaceIfNotExist(sessionBuilder, config.getKeyspace());
        sessionBuilder.withKeyspace(config.getKeyspace());
        this.embeddingTable = new MetadataVectorCassandraTable(sessionBuilder.build(),
               config.getKeyspace(), config.getTable(), config.getDimension());
    }

    /**
     * Build the cassandra session from the config. At the difference of adminSession there
     * a keyspace attached to it.
     *
     * @param config
     *      current configuration
     * @return
     *      cassandra session
     */
    private CqlSessionBuilder createCqlSessionBuilder(CassandraEmbeddingConfiguration config) {
        CqlSessionBuilder cqlSessionBuilder = CqlSession.builder();
        cqlSessionBuilder.withLocalDatacenter(config.getLocalDataCenter());
        if (config.getUserName() != null && config.getPassword() != null) {
            cqlSessionBuilder.withAuthCredentials(config.getUserName(), config.getPassword());
        }
        config.getContactPoints().forEach(cp ->
                cqlSessionBuilder.addContactPoint(new InetSocketAddress(cp, config.getPort())));
        return cqlSessionBuilder;
    }

    /**
     * Create the keyspace in cassandra Destination if not exist.
     */
    private void createKeyspaceIfNotExist(CqlSessionBuilder cqlSessionBuilder, String keyspace) {
        try(CqlSession adminSession = cqlSessionBuilder.build()) {
            adminSession.execute(SchemaBuilder.createKeyspace(keyspace)
                    .ifNotExists()
                    .withSimpleStrategy(1)
                    .withDurableWrites(true)
                    .build());
        }
    }

}
