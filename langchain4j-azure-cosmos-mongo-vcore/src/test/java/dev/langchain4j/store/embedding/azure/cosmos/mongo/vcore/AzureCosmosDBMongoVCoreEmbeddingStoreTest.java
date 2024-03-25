package dev.langchain4j.store.embedding.azure.cosmos.mongo.vcore;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.in;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AzureCosmosDBMongoVCoreEmbeddingStoreTest {

    private static final String DATABASE_NAME = "test_db";
    private static final String COLLECTION_NAME = "test_coll";
    private static final String INDEX_NAME = "test_index";

    @Test
    void should_fail_if_mongoClient_missing() {
        assertThrows(IllegalArgumentException.class, () -> {
            AzureCosmosDbMongoVCoreEmbeddingStore.builder()
                    .mongoClient(null)
                    .build();
        });
    }

    @Test
    void should_fail_if_connectionString_missing() {
        assertThrows(IllegalArgumentException.class, () -> {
            AzureCosmosDbMongoVCoreEmbeddingStore.builder()
                    .connectionString(null)
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            AzureCosmosDbMongoVCoreEmbeddingStore.builder()
                    .connectionString("")
                    .build();
        });
    }

    @Test
    void should_fail_if_databaseName_collectionName_missing() {

        assertThrows(IllegalArgumentException.class, () -> {
            AzureCosmosDbMongoVCoreEmbeddingStore.builder()
                    .connectionString("Test_connection_string")
                    .databaseName(null)
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            AzureCosmosDbMongoVCoreEmbeddingStore.builder()
                    .connectionString("Test_connection_string")
                    .databaseName("")
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            AzureCosmosDbMongoVCoreEmbeddingStore.builder()
                    .connectionString("Test_connection_string")
                    .databaseName("test_database")
                    .collectionName(null)
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            AzureCosmosDbMongoVCoreEmbeddingStore.builder()
                    .connectionString("Test_connection_string")
                    .databaseName("test_database")
                    .collectionName("")
                    .build();
        });
    }

    @Test
    void should_fail_if_wrong_vector_index_type() {
        assertThrows(IllegalArgumentException.class, () -> {
            AzureCosmosDbMongoVCoreEmbeddingStore.builder()
                    .connectionString("Test_connection_string")
                    .databaseName("test_database")
                    .collectionName("test_collection")
                    .kind("")
                    .build();
        });
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_ENDPOINT", matches = ".+")
    void should_create_collection_and_index_if_not_exists() {
        MongoClient client = MongoClients.create(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(System.getenv("AZURE_COSMOS_ENDPOINT")))
                        .applicationName("JAVA_LANG_CHAIN")
                        .build());

        MongoDatabase database = client.getDatabase(DATABASE_NAME);
        assertThat(isCollectionExist(database, COLLECTION_NAME)).isEqualTo(Boolean.FALSE);

        EmbeddingStore embeddingStore = AzureCosmosDbMongoVCoreEmbeddingStore.builder()
                .mongoClient(client)
                .databaseName(DATABASE_NAME)
                .collectionName(COLLECTION_NAME)
                .indexName(INDEX_NAME)
                .applicationName("JAVA_LANG_CHAIN")
                .createIndex(true)
                .kind("vector-hnsw")
                .build();
        assertThat(isCollectionExist(database, COLLECTION_NAME)).isEqualTo(Boolean.TRUE);
        MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);
        assertThat(isIndexExist(INDEX_NAME, collection)).isEqualTo(Boolean.TRUE);

        database.drop();
        client.close();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_ENDPOINT", matches = ".+")
    void should_not_create_index_if_createIndex_set_to_false() {
        MongoClient client = MongoClients.create(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(System.getenv("AZURE_COSMOS_ENDPOINT")))
                        .applicationName("JAVA_LANG_CHAIN")
                        .build());

        MongoDatabase database = client.getDatabase(DATABASE_NAME);

        EmbeddingStore embeddingStore = AzureCosmosDbMongoVCoreEmbeddingStore.builder()
                .mongoClient(client)
                .databaseName(DATABASE_NAME)
                .collectionName(COLLECTION_NAME)
                .indexName(INDEX_NAME)
                .applicationName("JAVA_LANG_CHAIN")
                .createIndex(false)
                .kind("vector-hnsw")
                .build();
        MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);
        assertThat(isIndexExist(INDEX_NAME, collection)).isEqualTo(Boolean.FALSE);

        database.drop();
        client.close();
    }

    private boolean isCollectionExist(MongoDatabase database, String collectionName) {
        return StreamSupport.stream(database.listCollectionNames().spliterator(), false)
                .anyMatch(collectionName::equals);
    }

    private boolean isIndexExist(String indexName, MongoCollection<Document> collection) {
        return StreamSupport.stream(collection.listIndexes().spliterator(), false)
                .anyMatch(index -> indexName.equals(index.getString("name")));
    }
}
