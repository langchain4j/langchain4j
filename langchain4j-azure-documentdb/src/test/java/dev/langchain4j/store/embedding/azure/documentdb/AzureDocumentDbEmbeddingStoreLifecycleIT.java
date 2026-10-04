package dev.langchain4j.store.embedding.azure.documentdb;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_ENDPOINT", matches = ".+")
class AzureDocumentDbEmbeddingStoreLifecycleIT {

    private static final String COLLECTION_NAME = "embeddings";
    private static final Embedding EMBEDDING = Embedding.from(new float[] {1, 0, 0});
    private static final TextSegment SEGMENT = TextSegment.from("A lifecycle test document");

    @Test
    void should_close_owned_client_after_real_database_operations() {
        String connectionString = System.getenv("AZURE_COSMOS_ENDPOINT");
        try (MongoClient verificationClient = MongoClients.create(connectionString)) {
            MongoDatabase database = verificationClient.getDatabase(databaseName());
            try {
                AzureDocumentDbEmbeddingStore store = builder(database.getName(), "vector-hnsw")
                        .connectionString(connectionString)
                        .build();
                try (store) {
                    assertCanAddAndSearch(store);
                }

                assertThatThrownBy(() -> store.add(EMBEDDING))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("open");
                store.close();
                assertThat(database.getCollection(COLLECTION_NAME).countDocuments())
                        .isEqualTo(1);
            } finally {
                database.drop();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"vector-ivf", "vector-hnsw"})
    void should_leave_supplied_client_usable_after_closing_store(String kind) {
        try (MongoClient client = MongoClients.create(System.getenv("AZURE_COSMOS_ENDPOINT"))) {
            MongoDatabase database = client.getDatabase(databaseName());
            try {
                try (AzureDocumentDbEmbeddingStore store =
                        builder(database.getName(), kind).mongoClient(client).build()) {
                    assertCanAddAndSearch(store);
                }

                assertThat(database.getCollection(COLLECTION_NAME).countDocuments())
                        .isEqualTo(1);
            } finally {
                database.drop();
            }
        }
    }

    private static void assertCanAddAndSearch(AzureDocumentDbEmbeddingStore store) {
        String id = store.add(EMBEDDING, SEGMENT);

        awaitUntilAsserted(() -> assertThat(store.search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(EMBEDDING)
                                .maxResults(1)
                                .build())
                        .matches())
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.embeddingId()).isEqualTo(id);
                    assertThat(match.embedding()).isEqualTo(EMBEDDING);
                    assertThat(match.embedded()).isEqualTo(SEGMENT);
                }));
    }

    private static AzureDocumentDbEmbeddingStore.Builder builder(String databaseName, String kind) {
        return AzureDocumentDbEmbeddingStore.builder()
                .databaseName(databaseName)
                .collectionName(COLLECTION_NAME)
                .indexName("vector_index")
                .kind(kind)
                .dimensions(3)
                .numLists(1)
                .createIndex(true);
    }

    private static String databaseName() {
        return "lc4j_lifecycle_" + UUID.randomUUID().toString().replace("-", "");
    }
}
