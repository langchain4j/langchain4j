package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithoutMetadataIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;

import java.util.List;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.mongodb.TestHelper.*;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MongoDbEmbeddingStoreMiscIT {

    public static class ContainerIT extends MongoDbEmbeddingStoreWithRemovalIT {
        static MongoDBAtlasLocalContainer mongodb = new MongoDBAtlasLocalContainer("mongodb/mongodb-atlas-local:7.0.9");

        @BeforeAll
        static void start() {
            TestHelper.assertDoContainerTests();
            mongodb.start();
        }

        @AfterAll
        static void stop() {
            mongodb.stop();
        }

        @Override
        MongoClient createClient() {
            return createClientFromContainer(mongodb);
        }
    }

    TestHelper helper;

    MongoClient createClient() {
        return createClientFromEnv();
    }

    protected EmbeddingStore<TextSegment> embeddingStore() {
        return helper.getEmbeddingStore();
    }

    protected EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }

    @AfterEach
    void afterEach() {
        helper.afterTests();
    }

    @Test
    void should_find_relevant_with_filter() {
        // given
        helper = new TestHelper(createClient()).initialize(builder -> builder
                        .filter(Filters.and(Filters.eq("metadata.test-key", "test-value"))));

        TextSegment segment = TextSegment.from("this segment should be found", Metadata.from("test-key", "test-value"));
        Embedding embedding = embeddingModel().embed(segment.text()).content();

        TextSegment filterSegment = TextSegment.from("this segment should not be found", Metadata.from("test-key", "no-value"));
        Embedding filterEmbedding = embeddingModel().embed(filterSegment.text()).content();

        List<String> ids = embeddingStore().addAll(asList(embedding, filterEmbedding), asList(segment, filterSegment));
        assertThat(ids).hasSize(2);

        TextSegment refSegment = TextSegment.from("find a segment");
        Embedding refEmbedding = embeddingModel().embed(refSegment.text()).content();

        awaitUntilAsserted(() -> {
            // when
            List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(refEmbedding, 2);

            // then
            assertThat(relevant).hasSize(1);

            EmbeddingMatch<TextSegment> match = relevant.get(0);
            assertThat(match.score()).isCloseTo(0.88, withPercentage(1));
            assertThat(match.embeddingId()).isEqualTo(ids.get(0));
            assertThat(match.embedding()).isEqualTo(embedding);
            assertThat(match.embedded()).isEqualTo(segment);
        });
    }

    @Test
    void should_fail_when_index_absent() {
        helper = new TestHelper(createClient());
        try {
            helper = helper.initialize(builder -> builder.createIndex(false));
            fail("Expected exception");
        } catch (RuntimeException r) {
            assertTrue(r.getMessage().contains("Search Index 'test_index' not found"));
        }
    }
}
