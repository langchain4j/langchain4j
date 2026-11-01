package dev.langchain4j.store.embedding.azure.documentdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.ListCollectionNamesIterable;
import com.mongodb.client.MongoDatabase;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.stream.Stream;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AzureDocumentDbEmbeddingStoreCompatibilityTest {

    @Test
    void should_keep_implementation_details_out_of_the_public_api() {
        assertThat(AzureDocumentDbEmbeddingStore.class.getDeclaredConstructors())
                .allSatisfy(constructor -> assertThat(Modifier.isPrivate(constructor.getModifiers()))
                        .isTrue());
        assertThat(AzureDocumentDbEmbeddingStore.class.getMethods())
                .noneMatch(method -> method.getName().equals("findRelevant"));
        assertThat(AzureDocumentDbEmbeddingStore.class.getDeclaredClasses())
                .filteredOn(type -> Modifier.isPublic(type.getModifiers()))
                .containsExactlyInAnyOrder(
                        AzureDocumentDbEmbeddingStore.Builder.class,
                        AzureDocumentDbEmbeddingStore.VectorIndexType.class);
        assertThat(Modifier.isPublic(AzureDocumentDbDocument.class.getModifiers()))
                .isFalse();
        assertThat(Modifier.isPublic(AzureDocumentDbMatchedDocument.class.getModifiers()))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"""
        {
          "_id": "legacy-document",
          "embedding": [0.25, 0.5, -0.75],
          "text": "A document stored before the rename",
          "metadata": {"source": "legacy", "page": "1"}
        }
        """, """
            {
              "_id": "legacy-embedding",
              "embedding": [0.25, 0.5, -0.75]
            }
            """, """
                {
                  "_id": "legacy-empty-content",
                  "embedding": [0.25, 0.5, -0.75],
                  "text": "",
                  "metadata": {}
                }
                """})
    void should_read_and_write_legacy_bson_without_changing_the_stored_format(String json) {
        BsonDocument legacyDocument = BsonDocument.parse(json);
        CodecRegistry codecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(), fromCodecs(new AzureDocumentDbDocumentCodec()));
        Codec<AzureDocumentDbDocument> codec = codecRegistry.get(AzureDocumentDbDocument.class);

        AzureDocumentDbDocument document = codec.decode(
                new BsonDocumentReader(legacyDocument), DecoderContext.builder().build());

        assertThat(document.getId()).isEqualTo(legacyDocument.getString("_id").getValue());
        assertThat(document.getEmbedding()).containsExactly(0.25f, 0.5f, -0.75f);

        BsonDocument encodedDocument = new BsonDocument();
        codec.encode(
                new BsonDocumentWriter(encodedDocument),
                document,
                EncoderContext.builder().build());

        assertThat(encodedDocument).isEqualTo(legacyDocument);
    }

    @Test
    void collectionExists_should_work_with_listCollectionNamesIterable() {
        MongoDatabase database = mock(MongoDatabase.class);
        ListCollectionNamesIterable iterable = mock(ListCollectionNamesIterable.class);
        when(iterable.spliterator()).thenAnswer(ignored -> List.of("foo", "bar").spliterator());
        when(database.listCollectionNames()).thenReturn(iterable);

        assertThat(AzureDocumentDbEmbeddingStore.collectionExists(database, "foo"))
                .isTrue();
        assertThat(AzureDocumentDbEmbeddingStore.collectionExists(database, "baz"))
                .isFalse();
    }

    @ParameterizedTest
    @MethodSource("driverFailures")
    void collectionExists_should_propagate_driver_failures(MongoException failure) {
        MongoDatabase database = mock(MongoDatabase.class);
        when(database.listCollectionNames()).thenThrow(failure);

        assertThatThrownBy(() -> AzureDocumentDbEmbeddingStore.collectionExists(database, "collection"))
                .isSameAs(failure);
    }

    @ParameterizedTest
    @MethodSource("driverFailures")
    void collectionExists_should_propagate_cursor_failures(MongoException failure) {
        MongoDatabase database = mock(MongoDatabase.class);
        ListCollectionNamesIterable iterable = mock(ListCollectionNamesIterable.class);
        when(database.listCollectionNames()).thenReturn(iterable);
        when(iterable.spliterator()).thenThrow(failure);

        assertThatThrownBy(() -> AzureDocumentDbEmbeddingStore.collectionExists(database, "collection"))
                .isSameAs(failure);
    }

    private static Stream<MongoException> driverFailures() {
        return Stream.of(
                new MongoTimeoutException("Timed out listing collections"),
                new MongoSecurityException(
                        MongoCredential.createCredential("user", "admin", new char[0]), "Authentication failed"));
    }
}
