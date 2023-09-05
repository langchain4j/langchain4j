package dev.langchain4j.store.embedding.cassandra;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.AbstractEmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;

/**
 * Represent an Embedding store using Cassandra AstraDB.
 *
 * @author Cedrick Lunven (clun)
 */
@Slf4j
public class AstraDbEmbeddingStore extends AbstractEmbeddingStore<TextSegment> {

    /** Default implementation bu can be override. */
    private static final String DEFAULT_IMPLEMENTATION =
            "dev.langchain4j.store.embedding.cassandra.AstraDbEmbeddingStoreImpl";

    /**
     * Store Configuration.
     */
    private final AstraDbEmbeddingConfiguration configuration;

    /**
     * Constructor with default table name.
     *
     * @param config
     *      load configuration
     */
    public AstraDbEmbeddingStore(AstraDbEmbeddingConfiguration config) {
        this(DEFAULT_IMPLEMENTATION, config);
    }

    /**
     * Constructor with default table name.
     *
     * @param config
     *      load configuration
     */
    public AstraDbEmbeddingStore(String impl, AstraDbEmbeddingConfiguration config) {
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
                .getConstructor(AstraDbEmbeddingConfiguration.class)
                .newInstance(configuration);
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
         * @param token
         *      token
         * @return
         *      current reference
         */
        public Builder token(String token) {
            conf.token(token);
            return this;
        }

        /**
         * Populating token.
         *
         * @param databaseId
         *      database Identifier
         * @param databaseRegion
         *      database region
         * @return
         *      current reference
         */
        public Builder database(String databaseId, String databaseRegion) {
            conf.databaseId(databaseId);
            conf.databaseRegion(databaseRegion);
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
        public Builder vectorDimension(int dimension) {
            conf.dimension(dimension);
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
        public Builder table(String keyspace, String table) {
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
        public AstraDbEmbeddingStore build() {
            return new AstraDbEmbeddingStore(conf.build());
        }

    }

}
