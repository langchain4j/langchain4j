package dev.langchain4j.store.embedding.mongodb;

import static dev.langchain4j.data.document.Metadata.metadata;
import static dev.langchain4j.store.embedding.filter.Filter.not;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static dev.langchain4j.store.embedding.mongodb.MongoDbTestFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClient;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
        assertThat(helper.getDatabase()
                        .getCollection(helper.getCollectionName())
                        .countDocuments())
                .isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource
    void should_remove_all_by_negated_filter(Filter filter, List<String> remaining) {
        // given
        TextSegment spam = TextSegment.from("spam", metadata("category", "spam").put("status", "active"));
        TextSegment deletedNews =
                TextSegment.from("deleted news", metadata("category", "news").put("status", "deleted"));
        TextSegment activeNews =
                TextSegment.from("active news", metadata("category", "news").put("status", "active"));
        for (TextSegment segment : List.of(spam, deletedNews, activeNews)) {
            embeddingStore().add(embeddingModel().embed(segment).content(), segment);
        }

        // when
        embeddingStore().removeAll(filter);

        // then
        assertThat(helper.getDatabase()
                        .getCollection(helper.getCollectionName())
                        .distinct("text", String.class))
                .containsExactlyInAnyOrderElementsOf(remaining);
    }

    static Stream<Arguments> should_remove_all_by_negated_filter() {
        Filter spamOrDeleted = metadataKey("category")
                .isEqualTo("spam")
                .or(metadataKey("status").isEqualTo("deleted"));
        Filter activeNews = metadataKey("category")
                .isEqualTo("news")
                .and(metadataKey("status").isEqualTo("active"));
        return Stream.of(
                Arguments.of(not(spamOrDeleted), List.of("spam", "deleted news")),
                Arguments.of(not(activeNews), List.of("active news")),
                Arguments.of(
                        not(not(metadataKey("category").isEqualTo("spam"))), List.of("deleted news", "active news")),
                Arguments.of(not(not(spamOrDeleted)), List.of("active news")));
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
