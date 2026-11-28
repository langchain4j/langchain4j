package dev.langchain4j.store.embedding.mongodb;

import static dev.langchain4j.store.embedding.filter.Filter.not;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static dev.langchain4j.store.embedding.mongodb.MongoDbTestFixture.*;
import static java.util.Arrays.asList;

import com.mongodb.client.MongoClient;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MongoDbEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {
    private MongoDbTestFixture fixture = new MongoDbTestFixture(createClient()).initialize();

    MongoClient createClient() {
        return createDefaultClient();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return fixture.getEmbeddingStore();
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }

    @Override
    @ParameterizedTest
    @MethodSource("should_filter_by_metadata_not_mongodb")
    protected void should_filter_by_metadata_not(
            Filter metadataFilter, List<Metadata> matchingMetadatas, List<Metadata> notMatchingMetadatas) {
        super.should_filter_by_metadata_not(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    static Stream<Arguments> should_filter_by_metadata_not_mongodb() {
        return Stream.concat(
                EmbeddingStoreWithFilteringIT.should_filter_by_metadata_not(),
                Stream.of(
                        Arguments.of(
                                not(metadataKey("name")
                                        .isEqualTo("Klaus")
                                        .or(metadataKey("age").isEqualTo(42))),
                                asList(new Metadata().put("name", "Alice"), new Metadata()),
                                asList(new Metadata().put("name", "Klaus"), new Metadata().put("age", 42))),
                        Arguments.of(
                                not(metadataKey("name")
                                        .isEqualTo("Klaus")
                                        .and(metadataKey("age").isEqualTo(42))),
                                asList(
                                        new Metadata().put("name", "Klaus"),
                                        new Metadata().put("age", 42),
                                        new Metadata()),
                                asList(new Metadata().put("name", "Klaus").put("age", 42))),
                        Arguments.of(
                                not(not(metadataKey("name")
                                        .isEqualTo("Klaus")
                                        .or(metadataKey("age").isEqualTo(42)))),
                                asList(new Metadata().put("name", "Klaus"), new Metadata().put("age", 42)),
                                asList(new Metadata().put("name", "Alice"), new Metadata()))));
    }

    @AfterEach
    void afterEach() {
        fixture.afterTests();
    }
}
