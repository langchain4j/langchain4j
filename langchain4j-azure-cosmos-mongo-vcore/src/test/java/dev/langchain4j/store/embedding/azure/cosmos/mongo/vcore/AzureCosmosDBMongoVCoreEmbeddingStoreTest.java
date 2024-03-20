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

import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AzureCosmosDBMongoVCoreEmbeddingStoreTest {
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
    void should_create_collection_and_index_if_not_exists() {
        MongoClient client = MongoClients.create(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(System.getenv("AZURE_COSMOS_ENDPOINT")))
                        .applicationName("JAVA_LANG_CHAIN")
                        .build());
        String databaseName = "test_database";
        String collectionName = "test_collection";
        String indexName = "test_index";

        MongoDatabase database = client.getDatabase(databaseName);
        assertThat(isCollectionExist(database, collectionName)).isEqualTo(Boolean.FALSE);

        EmbeddingStore embeddingStore = AzureCosmosDbMongoVCoreEmbeddingStore.builder()
                .mongoClient(client)
                .databaseName(databaseName)
                .collectionName(collectionName)
                .indexName(indexName)
                .applicationName("JAVA_LANG_CHAIN")
                .createIndex(true)
                .kind("vector-hnsw")
                .build();
        assertThat(isCollectionExist(database, collectionName)).isEqualTo(Boolean.TRUE);
        MongoCollection<Document> collection = database.getCollection(collectionName);
        assertThat(isIndexExist(indexName, collection)).isEqualTo(Boolean.TRUE);

        database.drop();
        client.close();
    }

    @Test
    void should_not_create_index_if_createIndex_set_to_false() {
        MongoClient client = MongoClients.create(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(System.getenv("AZURE_COSMOS_ENDPOINT")))
                        .applicationName("JAVA_LANG_CHAIN")
                        .build());
        String databaseName = "test_database";
        String collectionName = "test_collection";
        String indexName = "test_index";

        MongoDatabase database = client.getDatabase(databaseName);

        EmbeddingStore embeddingStore = AzureCosmosDbMongoVCoreEmbeddingStore.builder()
                .mongoClient(client)
                .databaseName(databaseName)
                .collectionName(collectionName)
                .indexName(indexName)
                .applicationName("JAVA_LANG_CHAIN")
                .createIndex(false)
                .kind("vector-hnsw")
                .build();
        MongoCollection<Document> collection = database.getCollection(collectionName);
        assertThat(isIndexExist(indexName, collection)).isEqualTo(Boolean.FALSE);

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
