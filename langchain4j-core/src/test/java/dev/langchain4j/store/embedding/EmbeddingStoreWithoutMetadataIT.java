package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.assertj.core.data.Percentage;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.Utils.randomUUID;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.data.Percentage.withPercentage;

public abstract class EmbeddingStoreWithoutMetadataIT {

    protected abstract EmbeddingStore<TextSegment> embeddingStore();

    protected abstract EmbeddingModel embeddingModel();

    @BeforeEach
    void beforeEach() {
        ensureStoreIsReady();
        clearStore();
        ensureStoreIsEmpty();
    }

    protected void ensureStoreIsReady() {
    }

    protected void clearStore() {
    }

    protected void ensureStoreIsEmpty() {
        assertThat(getAllEmbeddings()).isEmpty();
    }

    @Test
    void should_add_embedding() {

        // given
        Embedding embedding = embeddingModel().embed("hello").content();
        String id = embeddingStore().add(embedding);

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        // when
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(embedding, 10);

        // then
        assertThat(id).isNotBlank();
        assertThat(relevant).hasSize(1);
        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, percentage());
        assertThat(match.embeddingId()).isEqualTo(id);
        if (assertEmbedding()) {
            assertThat(match.embedding()).isEqualTo(embedding);
        }
        assertThat(match.embedded()).isNull();

        // new API
        assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build()).matches()).isEqualTo(relevant);
    }

    @Test
    void should_add_embedding_with_id() {

        // given
        String id = randomUUID();
        Embedding embedding = embeddingModel().embed("hello").content();
        embeddingStore().add(id, embedding);

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        // when
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(embedding, 10);

        // then
        assertThat(relevant).hasSize(1);
        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, percentage());
        assertThat(match.embeddingId()).isEqualTo(id);
        if (assertEmbedding()) {
            assertThat(match.embedding()).isEqualTo(embedding);
        }
        assertThat(match.embedded()).isNull();

        // new API
        assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build()).matches()).isEqualTo(relevant);
    }

    @Test
    void should_add_embedding_with_segment() {

        // given
        TextSegment segment = TextSegment.from("hello");
        Embedding embedding = embeddingModel().embed(segment.text()).content();
        String id = embeddingStore().add(embedding, segment);

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        // when
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(embedding, 10);

        // then
        assertThat(id).isNotBlank();
        assertThat(relevant).hasSize(1);
        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, percentage());
        assertThat(match.embeddingId()).isEqualTo(id);
        if (assertEmbedding()) {
            assertThat(match.embedding()).isEqualTo(embedding);
        }
        assertThat(match.embedded()).isEqualTo(segment);

        // new API
        assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build()).matches()).isEqualTo(relevant);
    }

    @Test
    void should_add_multiple_embeddings() {

        // given
        Embedding firstEmbedding = embeddingModel().embed("hello").content();
        Embedding secondEmbedding = embeddingModel().embed("hi").content();
        List<String> ids = embeddingStore().addAll(asList(firstEmbedding, secondEmbedding));

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(2));

        // when
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(firstEmbedding, 10);

        // then
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isNotBlank();
        assertThat(ids.get(1)).isNotBlank();
        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));

        assertThat(relevant).hasSize(2);
        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, percentage());
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        if (assertEmbedding()) {
            assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        }
        assertThat(firstMatch.embedded()).isNull();

        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(firstEmbedding, secondEmbedding)),
                percentage()
        );
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        if (assertEmbedding()) {
            assertThat(CosineSimilarity.between(secondMatch.embedding(), secondEmbedding))
                    .isCloseTo(1, withPercentage(0.01)); // TODO return strict check back once Qdrant fixes it
        }
        assertThat(secondMatch.embedded()).isNull();

        // new API
        assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .build()).matches()).isEqualTo(relevant);
    }

    @Test
    void should_add_multiple_embeddings_with_segments() {

        // given
        TextSegment firstSegment = TextSegment.from("hello");
        Embedding firstEmbedding = embeddingModel().embed(firstSegment.text()).content();

        TextSegment secondSegment = TextSegment.from("hi");
        Embedding secondEmbedding = embeddingModel().embed(secondSegment.text()).content();

        List<String> ids = embeddingStore().addAll(
                asList(firstEmbedding, secondEmbedding),
                asList(firstSegment, secondSegment)
        );

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(2));

        // when
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(firstEmbedding, 10);

        // then
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isNotBlank();
        assertThat(ids.get(1)).isNotBlank();
        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));

        assertThat(relevant).hasSize(2);
        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, percentage());
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        if (assertEmbedding()) {
            assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        }
        assertThat(firstMatch.embedded()).isEqualTo(firstSegment);

        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(firstEmbedding, secondEmbedding)),
                percentage()
        );
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        if (assertEmbedding()) {
            assertThat(CosineSimilarity.between(secondMatch.embedding(), secondEmbedding))
                    .isCloseTo(1, withPercentage(0.01)); // TODO return strict check back once Qdrant fixes it
        }
        assertThat(secondMatch.embedded()).isEqualTo(secondSegment);

        // new API
        assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .build()).matches()).isEqualTo(relevant);
    }

    @Test
    void should_find_with_min_score() {

        // given
        String firstId = randomUUID();
        Embedding firstEmbedding = embeddingModel().embed("hello").content();
        embeddingStore().add(firstId, firstEmbedding);

        String secondId = randomUUID();
        Embedding secondEmbedding = embeddingModel().embed("hi").content();
        embeddingStore().add(secondId, secondEmbedding);

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(2));

        // when
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(firstEmbedding, 10);

        // then
        assertThat(relevant).hasSize(2);
        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, percentage());
        assertThat(firstMatch.embeddingId()).isEqualTo(firstId);
        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(firstEmbedding, secondEmbedding)),
                percentage()
        );
        assertThat(secondMatch.embeddingId()).isEqualTo(secondId);

        // new API
        assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .build()).matches()).isEqualTo(relevant);

        // when
        List<EmbeddingMatch<TextSegment>> relevant2 = embeddingStore().findRelevant(
                firstEmbedding,
                10,
                secondMatch.score() - 0.01
        );

        // then
        assertThat(relevant2).hasSize(2);
        assertThat(relevant2.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(relevant2.get(1).embeddingId()).isEqualTo(secondId);

        // new API
        assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .minScore(secondMatch.score() - 0.01)
                .build()).matches()).isEqualTo(relevant2);

        // when
        List<EmbeddingMatch<TextSegment>> relevant3 = embeddingStore().findRelevant(
                firstEmbedding,
                10,
                secondMatch.score()
        );

        // then
        assertThat(relevant3).hasSize(2);
        assertThat(relevant3.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(relevant3.get(1).embeddingId()).isEqualTo(secondId);

        // new API
        assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .minScore(secondMatch.score())
                .build()).matches()).isEqualTo(relevant3);

        // when
        List<EmbeddingMatch<TextSegment>> relevant4 = embeddingStore().findRelevant(
                firstEmbedding,
                10,
                secondMatch.score() + 0.01
        );

        // then
        assertThat(relevant4).hasSize(1);
        assertThat(relevant4.get(0).embeddingId()).isEqualTo(firstId);

        // new API
        assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .minScore(secondMatch.score() + 0.01)
                .build()).matches()).isEqualTo(relevant4);
    }

    @Test
    void should_return_correct_score() {

        // given
        Embedding embedding = embeddingModel().embed("hello").content();

        String id = embeddingStore().add(embedding);
        assertThat(id).isNotBlank();

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        Embedding referenceEmbedding = embeddingModel().embed("hi").content();

        // when
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(referenceEmbedding, 1);

        // then
        assertThat(relevant).hasSize(1);
        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(embedding, referenceEmbedding)),
                percentage()
        );

        // new API
        assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(1)
                .build()).matches()).isEqualTo(relevant);
    }

    protected void awaitUntilAsserted(ThrowingRunnable assertion) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollDelay(Duration.ofSeconds(0))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(assertion);
    }

    protected List<EmbeddingMatch<TextSegment>> getAllEmbeddings() {

        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel().embed("test").content())
                .maxResults(1000)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(embeddingSearchRequest);

        return searchResult.matches();
    }

    protected boolean assertEmbedding() {
        return true;
    }

    protected Percentage percentage() {
        return withPercentage(1);
    }
}
