package dev.langchain4j.store.embedding.azure.documentdb;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.ListCollectionNamesIterable;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import java.util.List;
import java.util.stream.Stream;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AzureDocumentDbEmbeddingStoreLifecycleTest {

    private static final String CONNECTION_STRING = "mongodb://localhost:27017";
    private static final String DATABASE_NAME = "test_database";
    private static final String COLLECTION_NAME = "test_collection";

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
    void should_close_internally_created_client_only_once() {
        mockExistingCollection();

        try (MockedStatic<MongoClients> mongoClients = mockStatic(MongoClients.class)) {
            mongoClients
                    .when(() -> MongoClients.create(any(MongoClientSettings.class)))
                    .thenReturn(mongoClient);

            AzureDocumentDbEmbeddingStore store =
                    builder().connectionString(CONNECTION_STRING).build();
            try (store) {
                verify(mongoClient, never()).close();
            }
            store.close();

            verify(mongoClient).close();
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {CONNECTION_STRING, "ignored-connection-string"})
    void should_not_create_or_close_a_client_when_one_is_supplied(String connectionString) {
        mockExistingCollection();

        try (MockedStatic<MongoClients> mongoClients = mockStatic(MongoClients.class)) {
            try (AzureDocumentDbEmbeddingStore store = builder()
                    .mongoClient(mongoClient)
                    .connectionString(connectionString)
                    .build()) {
                verify(mongoClient, never()).close();
            }

            mongoClients.verifyNoInteractions();
            verify(mongoClient, never()).close();
        }
    }

    @ParameterizedTest
    @MethodSource("initializationFailures")
    void should_close_owned_client_when_initialization_fails(Throwable failure) {
        when(mongoClient.getDatabase(DATABASE_NAME)).thenThrow(failure);

        try (MockedStatic<MongoClients> mongoClients = mockStatic(MongoClients.class)) {
            mongoClients
                    .when(() -> MongoClients.create(any(MongoClientSettings.class)))
                    .thenReturn(mongoClient);

            assertThatThrownBy(
                            () -> builder().connectionString(CONNECTION_STRING).build())
                    .isSameAs(failure);

            verify(mongoClient).close();
        }
    }

    @ParameterizedTest
    @MethodSource("initializationFailures")
    void should_not_close_supplied_client_when_initialization_fails(Throwable failure) {
        when(mongoClient.getDatabase(DATABASE_NAME)).thenThrow(failure);

        assertThatThrownBy(() -> builder().mongoClient(mongoClient).build()).isSameAs(failure);

        verify(mongoClient, never()).close();
    }

    @Test
    void should_close_owned_client_when_collection_creation_fails() {
        when(mongoClient.getDatabase(DATABASE_NAME)).thenReturn(database);
        when(database.listCollectionNames()).thenReturn(collectionNames);
        when(collectionNames.spliterator()).thenReturn(List.<String>of().spliterator());
        MongoException failure = new MongoException("Collection creation failed");
        doThrow(failure).when(database).createCollection(eq(COLLECTION_NAME), any(CreateCollectionOptions.class));

        try (MockedStatic<MongoClients> mongoClients = mockStatic(MongoClients.class)) {
            mongoClients
                    .when(() -> MongoClients.create(any(MongoClientSettings.class)))
                    .thenReturn(mongoClient);

            assertThatThrownBy(
                            () -> builder().connectionString(CONNECTION_STRING).build())
                    .isSameAs(failure);

            verify(mongoClient).close();
        }
    }

    @Test
    void should_close_owned_client_when_index_creation_fails() {
        mockExistingCollection();
        when(collection.listIndexes()).thenReturn(indexes);
        when(indexes.spliterator()).thenReturn(List.<Document>of().spliterator());
        MongoException failure = new MongoException("Index creation failed");
        when(database.runCommand(any(Bson.class))).thenThrow(failure);

        try (MockedStatic<MongoClients> mongoClients = mockStatic(MongoClients.class)) {
            mongoClients
                    .when(() -> MongoClients.create(any(MongoClientSettings.class)))
                    .thenReturn(mongoClient);

            assertThatThrownBy(() -> builder()
                            .connectionString(CONNECTION_STRING)
                            .createIndex(true)
                            .dimensions(3)
                            .build())
                    .isSameAs(failure);

            verify(mongoClient).close();
        }
    }

    @Test
    void should_preserve_initialization_failure_when_closing_also_fails() {
        MongoException initializationFailure = new MongoException("Initialization failed");
        MongoException closeFailure = new MongoException("Close failed");
        when(mongoClient.getDatabase(DATABASE_NAME)).thenThrow(initializationFailure);
        doThrow(closeFailure).when(mongoClient).close();

        try (MockedStatic<MongoClients> mongoClients = mockStatic(MongoClients.class)) {
            mongoClients
                    .when(() -> MongoClients.create(any(MongoClientSettings.class)))
                    .thenReturn(mongoClient);

            assertThatThrownBy(
                            () -> builder().connectionString(CONNECTION_STRING).build())
                    .isSameAs(initializationFailure)
                    .hasSuppressedException(closeFailure);

            verify(mongoClient).close();
        }
    }

    @Test
    void should_propagate_close_failure() {
        mockExistingCollection();
        MongoException failure = new MongoException("Close failed");
        doThrow(failure).when(mongoClient).close();

        try (MockedStatic<MongoClients> mongoClients = mockStatic(MongoClients.class)) {
            mongoClients
                    .when(() -> MongoClients.create(any(MongoClientSettings.class)))
                    .thenReturn(mongoClient);

            AzureDocumentDbEmbeddingStore store =
                    builder().connectionString(CONNECTION_STRING).build();

            assertThatThrownBy(store::close).isSameAs(failure);

            verify(mongoClient).close();
        }
    }

    @Test
    void should_validate_configuration_before_creating_client() {
        try (MockedStatic<MongoClients> mongoClients = mockStatic(MongoClients.class)) {
            assertThatThrownBy(() -> builder()
                            .connectionString(CONNECTION_STRING)
                            .collectionName(null)
                            .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("databaseName and collectionName needs to be provided.");

            mongoClients.verifyNoInteractions();
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
                .databaseName(DATABASE_NAME)
                .collectionName(COLLECTION_NAME)
                .kind("vector-hnsw");
    }

    private static Stream<Throwable> initializationFailures() {
        return Stream.of(new MongoException("Initialization failed"), new AssertionError("Initialization failed"));
    }
}
