package dev.langchain4j.store.embedding.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.dtsx.astra.sdk.cassio.MetadataVectorCassandraTable;
import com.dtsx.astra.sdk.cassio.SimilarityMetric;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.net.InetSocketAddress;
import java.util.Arrays;

/**
 * Implementation of {@link EmbeddingStore} using Cassandra AstraDB.
 *
 * @see EmbeddingStore
 * @see MetadataVectorCassandraTable
 */
public class CassandraEmbeddingStore extends CassandraEmbeddingStoreSupport {

    /**
     * Build the store from the configuration.
     *
     * @param config configuration
     */
    public CassandraEmbeddingStore(CassandraEmbeddingConfiguration config) {
        CqlSessionBuilder sessionBuilder = createCqlSessionBuilder(config);
        createKeyspaceIfNotExist(sessionBuilder, config.getKeyspace());
        sessionBuilder.withKeyspace(config.getKeyspace());
        this.embeddingTable = new MetadataVectorCassandraTable(sessionBuilder.build(),
                config.getKeyspace(), config.getTable(), config.getDimension(), SimilarityMetric.COS);
    }

    /**
     * Build the cassandra session from the config. At the difference of adminSession there
     * a keyspace attached to it.
     *
     * @param config current configuration
     * @return cassandra session
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
        try (CqlSession adminSession = cqlSessionBuilder.build()) {
            adminSession.execute(SchemaBuilder.createKeyspace(keyspace)
                    .ifNotExists()
                    .withSimpleStrategy(1)
                    .withDurableWrites(true)
                    .build());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Syntax Sugar Builder.
     */
    public static class Builder {

        /**
         * Configuration built with the builder
         */
        private final CassandraEmbeddingConfiguration.CassandraEmbeddingConfigurationBuilder conf;

        /**
         * Initialization
         */
        public Builder() {
            conf = CassandraEmbeddingConfiguration.builder();
        }

        /**
         * Populating cassandra port.
         *
         * @param port port
         * @return current reference
         */
        public CassandraEmbeddingStore.Builder port(int port) {
            conf.port(port);
            return this;
        }

        /**
         * Populating cassandra contact points.
         *
         * @param hosts port
         * @return current reference
         */
        public CassandraEmbeddingStore.Builder contactPoints(String... hosts) {
            conf.contactPoints(Arrays.asList(hosts));
            return this;
        }

        /**
         * Populating model dimension.
         *
         * @param dimension model dimension
         * @return current reference
         */
        public CassandraEmbeddingStore.Builder vectorDimension(int dimension) {
            conf.dimension(dimension);
            return this;
        }

        /**
         * Populating datacenter.
         *
         * @param dc datacenter
         * @return current reference
         */
        public CassandraEmbeddingStore.Builder localDataCenter(String dc) {
            conf.localDataCenter(dc);
            return this;
        }

        /**
         * Populating table name.
         *
         * @param keyspace keyspace name
         * @param table    table name
         * @return current reference
         */
        public CassandraEmbeddingStore.Builder table(String keyspace, String table) {
            conf.keyspace(keyspace);
            conf.table(table);
            return this;
        }

        /**
         * Building the Store.
         *
         * @return store for Astra.
         */
        public CassandraEmbeddingStore build() {
            return new CassandraEmbeddingStore(conf.build());
        }
    }
}
