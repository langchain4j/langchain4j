package dev.langchain4j.store.memory.chat.cassandra;

import com.datastax.astra.sdk.AstraClient;

/**
 * AstraDb is a version of Cassandra running in Saas Mode.
 * <p>
 * The initialization of the CQLSession will be done through an AstraClient
 */
public class AstraDbChatMemoryStore extends CassandraChatMemoryStore {

    /**
     * Constructor with default table name.
     *
     * @param token        token
     * @param dbId         database identifier
     * @param dbRegion     database region
     * @param keyspaceName keyspace name
     */
    public AstraDbChatMemoryStore(String token, String dbId, String dbRegion, String keyspaceName) {
        this(token, dbId, dbRegion, keyspaceName, DEFAULT_TABLE_NAME);
    }

    /**
     * Constructor with explicit table name.
     *
     * @param token        token
     * @param dbId         database identifier
     * @param dbRegion     database region
     * @param keyspaceName keyspace name
     * @param tableName    table name
     */
    public AstraDbChatMemoryStore(String token, String dbId, String dbRegion, String keyspaceName, String tableName) {
        super(AstraClient.builder()
                .withToken(token)
                .withCqlKeyspace(keyspaceName)
                .withDatabaseId(dbId)
                .withDatabaseRegion(dbRegion)
                .enableCql()
                .enableDownloadSecureConnectBundle()
                .build().cqlSession(), keyspaceName, tableName);
    }
}

