package dev.langchain4j.store.embedding.cassandra;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import java.util.List;

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
public class CassandraEmbeddingConfiguration {

    /**
     * Default Cassandra Port.
     */
    public static Integer DEFAULT_PORT = 9042;

    // --- Connectivity Parameters ---

    /**
     * Represents the cassandra Contact points.
     */
    @NonNull
    private List<String> contactPoints;

    /**
     * Represent the local data center.
     */
    @NonNull
    private String localDataCenter;

    /**
     * Connection Port
     */
    @NonNull
    private Integer port;

    /**
     * (Optional) Represents the username to connect to the database.
     */
    private String userName;

    /**
     * (Optional) Represents the password to connect to the database.
     */
    private String password;

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
     * Represents the dimension of the model use to create the embeddings. The vector holding the embeddings
     * is a fixed size. The dimension of the vector is the dimension of the model used to create the embeddings.
     */
    @NonNull
    protected Integer dimension;

    /**
     * Initialize the builder.
     *
     * @return cassandra embedding configuration buildesr
     */
    public static CassandraEmbeddingConfigurationBuilder builder() {
        return new CassandraEmbeddingConfigurationBuilder();
    }

    /**
     * Signature for the builder.
     */
    public static class CassandraEmbeddingConfigurationBuilder {
    }
}
