package dev.langchain4j.store.embedding.azure.documentdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.ListCollectionNamesIterable;
import com.mongodb.client.MongoDatabase;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AzureDocumentDbEmbeddingStoreCompatibilityTest {

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
            """})
    void should_read_and_write_legacy_bson_without_changing_the_stored_format(String json) {
        BsonDocument legacyDocument = BsonDocument.parse(json);
        CodecRegistry codecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder()
                        .register(AzureDocumentDbDocument.class, BsonDocument.class)
                        .build()));
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
        when(iterable.spliterator()).thenReturn(List.of("foo", "bar").spliterator());
        when(database.listCollectionNames()).thenReturn(iterable);

        assertThat(AzureDocumentDbEmbeddingStore.collectionExists(database, "foo"))
                .isTrue();
        assertThat(AzureDocumentDbEmbeddingStore.collectionExists(database, "baz"))
                .isFalse();
    }

    @Test
    void listCollectionNames_should_throw_when_not_iterable() {
        MongoDatabase database = mock(MongoDatabase.class);
        when(database.listCollectionNames()).thenReturn(null);

        try {
            AzureDocumentDbEmbeddingStore.listCollectionNames(database);
        } catch (IllegalStateException e) {
            assertThat(e).hasMessageContaining("non-Iterable");
        }
    }
}
