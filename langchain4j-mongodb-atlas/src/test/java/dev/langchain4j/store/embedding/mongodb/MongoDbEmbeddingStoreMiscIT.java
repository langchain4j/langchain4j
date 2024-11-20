package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.mongodb.MongoDbTestFixture.*;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.data.Percentage.withPercentage;

class MongoDbEmbeddingStoreMiscIT {

    private MongoDbTestFixture fixture;

    public static class ContainerIT extends MongoDbEmbeddingStoreMiscIT {
        @BeforeAll
        static void start() {
            assertDoContainerTests();
        }

        @Override
        MongoClient createClient() {
            return createClientFromContainer();
        }
    }

    MongoClient createClient() {
        return createClientFromEnv();
    }

    protected EmbeddingStore<TextSegment> embeddingStore() {
        return fixture.getEmbeddingStore();
    }

    protected EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }

    @AfterEach
    void afterEach() {
        if (fixture != null) {
            fixture.afterTests();
        }
    }

    @Test
    void should_find_relevant_with_filter() {
        // given
        fixture = new MongoDbTestFixture(createClient()).initialize(builder -> builder
                        .filter(Filters.and(Filters.eq("metadata.test-key", "test-value"))));

        waitForIndex();

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


    void waitForIndex() {
        // to avoid "cannot query search index while in state INITIAL_SYNC" error
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(EMBEDDING_MODEL.embed("dummy").content())
                .maxResults(1)
                .build();
        awaitUntilAsserted(
                () -> assertThatNoException().isThrownBy(() -> embeddingStore().search(embeddingSearchRequest)));
    }
}
