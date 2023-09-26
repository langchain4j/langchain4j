package dev.langchain4j.store.embedding.cassandra;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.AbstractEmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import static dev.langchain4j.store.embedding.cassandra.CassandraEmbeddingConfiguration.CassandraEmbeddingConfigurationBuilder;

/**
 * Represents an embeddings with
 */
public class CassandraEmbeddingStore extends AbstractEmbeddingStore<TextSegment> {

    /** Default implementation bu can be override. */
    private static final String DEFAULT_IMPLEMENTATION =
            "dev.langchain4j.store.embedding.cassandra.CassandraEmbeddingStoreImpl";

    /**
     * Store Configuration.
     */
    private final CassandraEmbeddingConfiguration configuration;

    /**
     * Constructor with default table name.
     *
     * @param config
     *      load configuration
     */
    public CassandraEmbeddingStore(CassandraEmbeddingConfiguration config) {
        this(DEFAULT_IMPLEMENTATION, config);
    }

    /**
     * Constructor with default table name.
     *
     * @param config
     *      load configuration
     */
    public CassandraEmbeddingStore(String impl, CassandraEmbeddingConfiguration config) {
        this.configuration           = config;
        this.implementationClassName = impl;
        getDelegateImplementation();
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    protected EmbeddingStore<TextSegment> loadImplementation()
            throws ClassNotFoundException, NoSuchMethodException, InstantiationException,
            IllegalAccessException, InvocationTargetException {
        return (EmbeddingStore<TextSegment>) Class
                .forName(implementationClassName)
                .getConstructor(CassandraEmbeddingConfiguration.class)
                .newInstance(configuration);
    }

    public static CassandraEmbeddingStore.Builder builder() {
        return new CassandraEmbeddingStore.Builder();
    }

    /**
     * Syntax Sugar Builder.
     */
    public static class Builder {

        /**
         * Configuration built with the builder
         */
        private final CassandraEmbeddingConfigurationBuilder conf;

        /**
         * Initialization
         */
        public Builder() {
            conf = CassandraEmbeddingConfiguration.builder();
        }

        /**
         * Populating cassandra port.
         *
         * @param port
         *      port
         * @return
         *      current reference
         */
        public CassandraEmbeddingStore.Builder port(int port) {
            conf.port(port);
            return this;
        }

        /**
         * Populating cassandra contact points.
         *
         * @param hosts
         *      port
         * @return
         *      current reference
         */
        public CassandraEmbeddingStore.Builder contactPoints(String... hosts) {
            conf.contactPoints(Arrays.asList(hosts));
            return this;
        }

        /**
         * Populating model dimension.
         *
         * @param dimension
         *      model dimension
         * @return
         *      current reference
         */
        public CassandraEmbeddingStore.Builder vectorDimension(int dimension) {
            conf.dimension(dimension);
            return this;
        }

        /**
         * Populating datacenter.
         *
         * @param dc
         *      datacenter
         * @return
         *      current reference
         */
        public CassandraEmbeddingStore.Builder localDataCenter(String dc) {
            conf.localDataCenter(dc);
            return this;
        }

        /**
         * Populating table name.
         *
         * @param keyspace
         *      keyspace name
         * @param table
         *      table name
         * @return
         *      current reference
         */
        public CassandraEmbeddingStore.Builder table(String keyspace, String table) {
            conf.keyspace(keyspace);
            conf.table(table);
            return this;
        }

        /**
         * Building the Store.
         *
         * @return
         *      store for Astra.
         */
        public CassandraEmbeddingStore build() {
            return new CassandraEmbeddingStore(conf.build());
        }

    }

}
