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
public class AstraDbEmbeddingStore extends CassandraEmbeddingStoreSupport {

    /**
     * Build the store from the configuration.
     *
     * @param config configuration
     */
    public AstraDbEmbeddingStore(AstraDbEmbeddingConfiguration config) {
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
        private final AstraDbEmbeddingConfiguration.AstraDbEmbeddingConfigurationBuilder conf;

        /**
         * Initialization
         */
        public Builder() {
            conf = AstraDbEmbeddingConfiguration.builder();
        }

        /**
         * Populating token.
         *
         * @param token token
         * @return current reference
         */
        public Builder token(String token) {
            conf.token(token);
            return this;
        }

        /**
         * Populating token.
         *
         * @param databaseId     database Identifier
         * @param databaseRegion database region
         * @return current reference
         */
        public Builder database(String databaseId, String databaseRegion) {
            conf.databaseId(databaseId);
            conf.databaseRegion(databaseRegion);
            return this;
        }

        /**
         * Populating model dimension.
         *
         * @param dimension model dimension
         * @return current reference
         */
        public Builder vectorDimension(int dimension) {
            conf.dimension(dimension);
            return this;
        }

        /**
         * Populating table name.
         *
         * @param keyspace keyspace name
         * @param table    table name
         * @return current reference
         */
        public Builder table(String keyspace, String table) {
            conf.keyspace(keyspace);
            conf.table(table);
            return this;
        }

        /**
         * Building the Store.
         *
         * @return store for Astra.
         */
        public AstraDbEmbeddingStore build() {
            return new AstraDbEmbeddingStore(conf.build());
        }
    }
}
