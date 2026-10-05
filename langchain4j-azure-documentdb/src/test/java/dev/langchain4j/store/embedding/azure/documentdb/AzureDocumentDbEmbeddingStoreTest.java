package dev.langchain4j.store.embedding.azure.documentdb;

import static dev.langchain4j.store.embedding.azure.documentdb.AzureDocumentDbEmbeddingStore.VectorIndexType.VECTOR_HNSW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.ListCollectionNamesIterable;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.InsertManyResult;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.azure.documentdb.AzureDocumentDbEmbeddingStore.VectorIndexType;
import java.util.List;
import java.util.stream.StreamSupport;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AzureDocumentDbEmbeddingStoreTest {

    private static final String DATABASE_NAME = "test_db";
    private static final String COLLECTION_NAME = "test_coll";
    private static final String INDEX_NAME = "test_index";

    @Mock
    private MongoClient mongoClient;

    @Mock
    private MongoDatabase database;

    @Mock
    private MongoCollection<AzureDocumentDbDocument> collection;

    @Mock
    private ListCollectionNamesIterable collectionNames;

    @Mock
    private ListIndexesIterable<Document> indexes;

    @Test
    void should_require_kind_before_creating_a_client() {
        try (MockedStatic<MongoClients> clients = mockStatic(MongoClients.class)) {
            assertThatThrownBy(() -> builder().build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("kind cannot be null");
            assertThatThrownBy(() -> builder().kind((VectorIndexType) null).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("kind cannot be null");
            assertThatThrownBy(() -> builder().kind((String) null).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("kind cannot be null");

            clients.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void should_require_positive_dimensions_when_creating_an_index(Integer dimensions) {
        try (MockedStatic<MongoClients> clients = mockStatic(MongoClients.class)) {
            assertThatThrownBy(() -> builder()
                            .kind(VECTOR_HNSW)
                            .createIndex(true)
                            .dimensions(dimensions)
                            .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(
                            dimensions == null
                                    ? "dimensions must be provided when createIndex is true"
                                    : "dimensions must be greater than zero, but is: " + dimensions);

            clients.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void should_not_require_dimensions_when_not_creating_an_index(Boolean createIndex) {
        mockExistingCollection();

        try (AzureDocumentDbEmbeddingStore store = builder()
                .mongoClient(mongoClient)
                .kind(VECTOR_HNSW)
                .createIndex(createIndex)
                .build()) {
            verify(collection, never()).listIndexes();
            verify(database, never()).runCommand(any(Bson.class));
        }
    }

    @ParameterizedTest
    @EnumSource(VectorIndexType.class)
    void should_accept_typed_index_kind(VectorIndexType kind) {
        assertCreatedIndex(builder().kind(kind).dimensions(384), kind.getValue(), 384);
    }

    @ParameterizedTest
    @ValueSource(strings = {"vector-ivf", "vector-hnsw"})
    void should_accept_string_index_kind(String kind) {
        assertCreatedIndex(builder().kind(kind).dimensions(768), kind, 768);
    }

    @ParameterizedTest
    @ValueSource(ints = {384, 768, 1536, 3072})
    void should_use_the_explicit_embedding_dimensions(int dimensions) {
        assertCreatedIndex(builder().kind(VECTOR_HNSW).dimensions(dimensions), "vector-hnsw", dimensions);
    }

    @Test
    void should_not_expose_document_contents_when_an_insert_is_not_acknowledged() {
        mockExistingCollection();
        when(collection.insertMany(anyList())).thenReturn(InsertManyResult.unacknowledged());

        try (AzureDocumentDbEmbeddingStore store =
                builder().mongoClient(mongoClient).kind(VECTOR_HNSW).build()) {
            Embedding embedding = Embedding.from(new float[] {0.25f, 0.5f, -0.75f});
            TextSegment segment =
                    TextSegment.from("confidential document text", Metadata.from("private-key", "confidential-value"));

            assertThatThrownBy(() -> store.addAll(List.of("private-id"), List.of(embedding), List.of(segment)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to add embeddings to Azure DocumentDB: the insert was not acknowledged");
        }
    }

    @Test
    void should_fail_if_mongoClient_missing() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            AzureDocumentDbEmbeddingStore.builder().mongoClient(null).build();
        });
    }

    @Test
    void should_fail_if_connectionString_missing() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            AzureDocumentDbEmbeddingStore.builder().connectionString(null).build();
        });

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            AzureDocumentDbEmbeddingStore.builder().connectionString("").build();
        });
    }

    @Test
    void should_fail_if_databaseName_collectionName_missing() {

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            AzureDocumentDbEmbeddingStore.builder()
                    .connectionString("Test_connection_string")
                    .databaseName(null)
                    .build();
        });

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            AzureDocumentDbEmbeddingStore.builder()
                    .connectionString("Test_connection_string")
                    .databaseName("")
                    .build();
        });

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            AzureDocumentDbEmbeddingStore.builder()
                    .connectionString("Test_connection_string")
                    .databaseName("test_database")
                    .collectionName(null)
                    .build();
        });

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            AzureDocumentDbEmbeddingStore.builder()
                    .connectionString("Test_connection_string")
                    .databaseName("test_database")
                    .collectionName("")
                    .build();
        });
    }

    @Test
    void should_fail_if_wrong_vector_index_type() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            AzureDocumentDbEmbeddingStore.builder()
                    .connectionString("Test_connection_string")
                    .databaseName("test_database")
                    .collectionName("test_collection")
                    .kind("")
                    .build();
        });
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AZURE_DOCUMENTDB_CONNECTION_STRING", matches = ".+")
    void should_create_collection_and_index_if_not_exists() {
        MongoClient client = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(System.getenv("AZURE_DOCUMENTDB_CONNECTION_STRING")))
                .applicationName("JAVA_LANG_CHAIN")
                .build());

        MongoDatabase database = client.getDatabase(DATABASE_NAME);
        assertThat(isCollectionExist(database, COLLECTION_NAME)).isEqualTo(Boolean.FALSE);

        EmbeddingStore embeddingStore = AzureDocumentDbEmbeddingStore.builder()
                .mongoClient(client)
                .databaseName(DATABASE_NAME)
                .collectionName(COLLECTION_NAME)
                .indexName(INDEX_NAME)
                .applicationName("JAVA_LANG_CHAIN")
                .createIndex(true)
                .kind("vector-hnsw")
                .dimensions(1536)
                .build();
        assertThat(isCollectionExist(database, COLLECTION_NAME)).isEqualTo(Boolean.TRUE);
        MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);
        assertThat(isIndexExist(INDEX_NAME, collection)).isEqualTo(Boolean.TRUE);

        database.drop();
        client.close();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AZURE_DOCUMENTDB_CONNECTION_STRING", matches = ".+")
    void should_not_create_index_if_createIndex_set_to_false() {
        MongoClient client = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(System.getenv("AZURE_DOCUMENTDB_CONNECTION_STRING")))
                .applicationName("JAVA_LANG_CHAIN")
                .build());

        MongoDatabase database = client.getDatabase(DATABASE_NAME);

        EmbeddingStore embeddingStore = AzureDocumentDbEmbeddingStore.builder()
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

    private void assertCreatedIndex(AzureDocumentDbEmbeddingStore.Builder builder, String kind, int dimensions) {
        mockExistingCollection();
        when(collection.listIndexes()).thenReturn(indexes);
        when(indexes.spliterator()).thenReturn(List.<Document>of().spliterator());

        try (AzureDocumentDbEmbeddingStore store =
                builder.mongoClient(mongoClient).createIndex(true).build()) {
            ArgumentCaptor<Bson> command = ArgumentCaptor.forClass(Bson.class);
            verify(database).runCommand(command.capture());
            BsonDocument index = command.getValue()
                    .toBsonDocument()
                    .getArray("indexes")
                    .get(0)
                    .asDocument();

            assertThat(index.getString("name").getValue()).isEqualTo("defaultIndexAzureCosmos");
            assertThat(index.getDocument("key").getString("embedding").getValue())
                    .isEqualTo("cosmosSearch");
            BsonDocument options = index.getDocument("cosmosSearchOptions");
            assertThat(options.getString("kind").getValue()).isEqualTo(kind);
            assertThat(options.getInt32("dimensions").getValue()).isEqualTo(dimensions);
            assertThat(options.getString("similarity").getValue()).isEqualTo("COS");
        }
    }

    private void mockExistingCollection() {
        when(mongoClient.getDatabase(DATABASE_NAME)).thenReturn(database);
        when(database.listCollectionNames()).thenReturn(collectionNames);
        when(collectionNames.spliterator())
                .thenAnswer(ignored -> List.of(COLLECTION_NAME).spliterator());
        when(database.getCollection(COLLECTION_NAME, AzureDocumentDbDocument.class))
                .thenReturn(collection);
        when(collection.withCodecRegistry(any(CodecRegistry.class))).thenReturn(collection);
    }

    private static AzureDocumentDbEmbeddingStore.Builder builder() {
        return AzureDocumentDbEmbeddingStore.builder()
                .connectionString("mongodb://localhost:27017")
                .databaseName(DATABASE_NAME)
                .collectionName(COLLECTION_NAME);
    }
}
