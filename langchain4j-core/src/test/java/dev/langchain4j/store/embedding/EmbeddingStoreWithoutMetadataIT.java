package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.internal.Utils.randomUUID;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

public abstract class EmbeddingStoreWithoutMetadataIT {

    protected abstract EmbeddingStore<TextSegment> embeddingStore();

    protected abstract EmbeddingModel embeddingModel();

    @BeforeEach
    void beforeEach() {
        clearStore();
        ensureStoreIsEmpty();
    }

    protected void clearStore() {
    }

    protected void ensureStoreIsEmpty() {
        Embedding embedding = embeddingModel().embed("hello").content();
        assertThat(embeddingStore().findRelevant(embedding, 1000)).isEmpty();
    }

    @Test
    void should_add_embedding() {

        Embedding embedding = embeddingModel().embed("hello").content();

        String id = embeddingStore().add(embedding);
        assertThat(id).isNotBlank();

        awaitUntilPersisted();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();
    }

    @Test
    void should_add_embedding_with_id() {

        String id = randomUUID();
        Embedding embedding = embeddingModel().embed("hello").content();

        embeddingStore().add(id, embedding);

        awaitUntilPersisted();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();
    }

    @Test
    void should_add_embedding_with_segment() {

        TextSegment segment = TextSegment.from("hello");
        Embedding embedding = embeddingModel().embed(segment.text()).content();

        String id = embeddingStore().add(embedding, segment);
        assertThat(id).isNotBlank();

        awaitUntilPersisted();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isEqualTo(segment);
    }

    @Test
    void should_add_multiple_embeddings() {

        Embedding firstEmbedding = embeddingModel().embed("hello").content();
        Embedding secondEmbedding = embeddingModel().embed("hi").content();

        List<String> ids = embeddingStore().addAll(asList(firstEmbedding, secondEmbedding));
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isNotBlank();
        assertThat(ids.get(1)).isNotBlank();
        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));

        awaitUntilPersisted();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);

        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        assertThat(firstMatch.embedded()).isNull();

        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(firstEmbedding, secondEmbedding)),
                withPercentage(1)
        );
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        assertThat(CosineSimilarity.between(secondMatch.embedding(), secondEmbedding))
                .isCloseTo(1, withPercentage(0.01)); // TODO return strict check back once Qdrant fixes it
        assertThat(secondMatch.embedded()).isNull();
    }

    @Test
    void should_add_multiple_embeddings_with_segments() {

        TextSegment firstSegment = TextSegment.from("hello");
        Embedding firstEmbedding = embeddingModel().embed(firstSegment.text()).content();

        TextSegment secondSegment = TextSegment.from("hi");
        Embedding secondEmbedding = embeddingModel().embed(secondSegment.text()).content();

        List<String> ids = embeddingStore().addAll(
                asList(firstEmbedding, secondEmbedding),
                asList(firstSegment, secondSegment)
        );
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isNotBlank();
        assertThat(ids.get(1)).isNotBlank();
        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));

        awaitUntilPersisted();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);

        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        assertThat(firstMatch.embedded()).isEqualTo(firstSegment);

        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(firstEmbedding, secondEmbedding)),
                withPercentage(1)
        );
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        assertThat(CosineSimilarity.between(secondMatch.embedding(), secondEmbedding))
                .isCloseTo(1, withPercentage(0.01)); // TODO return strict check back once Qdrant fixes it
        assertThat(secondMatch.embedded()).isEqualTo(secondSegment);
    }

    @Test
    void should_find_with_min_score() {

        String firstId = randomUUID();
        Embedding firstEmbedding = embeddingModel().embed("hello").content();
        embeddingStore().add(firstId, firstEmbedding);

        String secondId = randomUUID();
        Embedding secondEmbedding = embeddingModel().embed("hi").content();
        embeddingStore().add(secondId, secondEmbedding);

        awaitUntilPersisted();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);
        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(firstId);
        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(firstEmbedding, secondEmbedding)),
                withPercentage(1)
        );
        assertThat(secondMatch.embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant2 = embeddingStore().findRelevant(
                firstEmbedding,
                10,
                secondMatch.score() - 0.01
        );
        assertThat(relevant2).hasSize(2);
        assertThat(relevant2.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(relevant2.get(1).embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant3 = embeddingStore().findRelevant(
                firstEmbedding,
                10,
                secondMatch.score()
        );
        assertThat(relevant3).hasSize(2);
        assertThat(relevant3.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(relevant3.get(1).embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant4 = embeddingStore().findRelevant(
                firstEmbedding,
                10,
                secondMatch.score() + 0.01
        );
        assertThat(relevant4).hasSize(1);
        assertThat(relevant4.get(0).embeddingId()).isEqualTo(firstId);
    }

    @Test
    void should_return_correct_score() {

        Embedding embedding = embeddingModel().embed("hello").content();

        String id = embeddingStore().add(embedding);
        assertThat(id).isNotBlank();

        awaitUntilPersisted();

        Embedding referenceEmbedding = embeddingModel().embed("hi").content();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(referenceEmbedding, 1);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(embedding, referenceEmbedding)),
                withPercentage(1)
        );
    }

    protected void awaitUntilPersisted() {
        // not waiting by default
    }
}
