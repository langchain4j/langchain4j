package dev.langchain4j.store.embedding.cassandra;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Plain old Java Object (POJO) to hold the configuration for the CassandraEmbeddingStore.
 * Wrapping all arguments needed to initialize a store in a single object makes it easier to pass them around.
 * It also makes it easier to add new arguments in the future, without having to change the constructor of the store.
 * This is especially useful when the store is used in a pipeline, where the arguments are passed around multiple times.
 *
 * @see CassandraEmbeddingStore
 */
@Getter
@Builder
public class AstraDbEmbeddingConfiguration {

    /**
     * Represents the Api Key to interact with Astra DB
     *
     * @see <a href="https://docs.datastax.com/en/astra/docs/manage-application-tokens.html">Astra DB Api Key</a>
     */
    @NonNull
    private String token;

    /**
     * Represents the unique identifier for your database.
     */
    @NonNull
    private String databaseId;

    /**
     * Represents the region where your database is hosted. A database can be deployed
     * in multiple regions at the same time, and you can choose the region that is closest to your users.
     * If a database has a single region, it will be picked for you.
     */
    private String databaseRegion;

    /**
     * Represents the workspace name where you create your tables. One database can hold multiple keyspaces.
     * Best practice is to provide a keyspace for each application.
     */
    @NonNull
    protected String keyspace;

    /**
     * Represents the name of the table.
     */
    @NonNull
    protected String table;

    /**
     * Represents the dimension of the vector used to save the embeddings.
     */
    @NonNull
    protected Integer dimension;

    /**
     * Initialize the builder.
     *
     * @return cassandra embedding configuration builder
     */
    public static AstraDbEmbeddingConfiguration.AstraDbEmbeddingConfigurationBuilder builder() {
        return new AstraDbEmbeddingConfiguration.AstraDbEmbeddingConfigurationBuilder();
    }

    /**
     * Signature for the builder.
     */
    public static class AstraDbEmbeddingConfigurationBuilder {
    }
}
