package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.client.MongoClient;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;

import static dev.langchain4j.data.document.Metadata.metadata;
import static dev.langchain4j.store.embedding.mongodb.MongoDbTestFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

class MongoDbEmbeddingStoreWithRemovalIT extends EmbeddingStoreWithRemovalIT {
    private MongoDbTestFixture helper = new MongoDbTestFixture(createClient()).initialize();

    MongoClient createClient() {
        return createDefaultClient();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return helper.getEmbeddingStore();
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }

    @Test
    void should_not_interpret_map_comparison_value_as_operator() {
        // given
        TextSegment segment1 = TextSegment.from("first", metadata("type", "a"));
        embeddingStore().add(embeddingModel().embed(segment1).content(), segment1);

        TextSegment segment2 = TextSegment.from("second", metadata("type", "b"));
        embeddingStore().add(embeddingModel().embed(segment2).content(), segment2);

        // when: the map is compared as a literal value through an explicit $eq, so it matches no document
        embeddingStore().removeAll(unvalidatedIsEqualTo("type", Map.of("$ne", "x")));

        // then: counted in the collection, not through the eventually consistent search index
        assertThat(helper.getDatabase().getCollection(helper.getCollectionName()).countDocuments())
                .isEqualTo(2);
    }

    // The filter classes reject values like these, so they are returned from the overridden accessor
    // to check that the store handles them safely on its own.
    private static IsEqualTo unvalidatedIsEqualTo(String key, Object value) {
        return new IsEqualTo(key, "placeholder") {
            @Override
            public Object comparisonValue() {
                return value;
            }
        };
    }

    @AfterEach
    void afterEach() {
        helper.afterTests();
    }
}
