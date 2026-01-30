package dev.langchain4j.store.embedding.azure.cosmos.mongo.vcore;

import com.mongodb.client.ListCollectionNamesIterable;
import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AzureCosmosDbMongoVCoreEmbeddingStoreCompatibilityTest {

    @Test
    void collectionExists_should_work_with_listCollectionNamesIterable() {
        MongoDatabase database = mock(MongoDatabase.class);
        ListCollectionNamesIterable iterable = mock(ListCollectionNamesIterable.class);
        when(iterable.spliterator()).thenReturn(List.of("foo", "bar").spliterator());
        when(database.listCollectionNames()).thenReturn(iterable);

        assertThat(AzureCosmosDbMongoVCoreEmbeddingStore.collectionExists(database, "foo")).isTrue();
        assertThat(AzureCosmosDbMongoVCoreEmbeddingStore.collectionExists(database, "baz")).isFalse();
    }

    @Test
    void listCollectionNames_should_throw_when_not_iterable() {
        MongoDatabase database = mock(MongoDatabase.class);
        when(database.listCollectionNames()).thenReturn(null);

        try {
            AzureCosmosDbMongoVCoreEmbeddingStore.listCollectionNames(database);
        } catch (IllegalStateException e) {
            assertThat(e).hasMessageContaining("non-Iterable");
        }
    }
}
