package dev.langchain4j.store.embedding;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.List;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class EmbeddingStoreWithoutMetadataIT {

    protected abstract EmbeddingStore<TextSegment> embeddingStore();

    protected abstract EmbeddingModel embeddingModel();

    @BeforeEach
    void beforeEach() {
        ensureStoreIsReady();
        clearStore();
        ensureStoreIsEmpty();
    }

    protected void ensureStoreIsReady() {}

    protected void clearStore() {}

    protected void ensureStoreIsEmpty() {
        assertThat(getAllEmbeddings()).isEmpty();
    }

    @Test
    void should_add_embedding() {
        
        // given
        Embedding embedding = embeddingModel().embed("hello").content();
        String id = embeddingStore().add(embedding);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);

        // then
        assertThat(id).isNotBlank();
        
        assertThat(searchResult.matches()).hasSize(1);
        EmbeddingMatch<TextSegment> match = searchResult.matches().get(0);
        assertThat(match.score()).isCloseTo(1, percentage());
        assertThat(match.embeddingId()).isEqualTo(id);
        if (assertEmbedding()) {
            assertThat(match.embedding()).isEqualTo(embedding);
        }
        assertThat(match.embedded()).isNull();
    }

    @Test
    void should_add_embedding_with_id() {
        
        // given
        String id = randomUUID();
        Embedding embedding = embeddingModel().embed("hello").content();
        embeddingStore().add(id, embedding);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();
        
        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);

        // then
        assertThat(searchResult.matches()).hasSize(1);
        EmbeddingMatch<TextSegment> match = searchResult.matches().get(0);
        assertThat(match.score()).isCloseTo(1, percentage());
        assertThat(match.embeddingId()).isEqualTo(id);
        if (assertEmbedding()) {
            assertThat(match.embedding()).isEqualTo(embedding);
        }
        assertThat(match.embedded()).isNull();
    }

    @Test
    void should_add_embedding_with_segment() {
        // given
        TextSegment segment = TextSegment.from("hello");
        Embedding embedding = embeddingModel().embed(segment.text()).content();
        String id = embeddingStore().add(embedding, segment);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);

        // then
        assertThat(id).isNotBlank();
        assertThat(searchResult.matches()).hasSize(1);
        EmbeddingMatch<TextSegment> match = searchResult.matches().get(0);
        assertThat(match.score()).isCloseTo(1, percentage());
        assertThat(match.embeddingId()).isEqualTo(id);
        if (assertEmbedding()) {
            assertThat(match.embedding()).isEqualTo(embedding);
        }
        assertThat(match.embedded()).isEqualTo(segment);
    }

    @Test
    void should_add_multiple_embeddings() {
        // given
        Embedding firstEmbedding = embeddingModel().embed("hello").content();
        Embedding secondEmbedding = embeddingModel().embed("hi").content();
        List<String> ids = embeddingStore().addAll(asList(firstEmbedding, secondEmbedding));
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(2));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);

        // then
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isNotBlank();
        assertThat(ids.get(1)).isNotBlank();
        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));

        assertThat(searchResult.matches()).hasSize(2);
        EmbeddingMatch<TextSegment> firstMatch = searchResult.matches().get(0);
        assertThat(firstMatch.score()).isCloseTo(1, percentage());
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        if (assertEmbedding()) {
            assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        }
        assertThat(firstMatch.embedded()).isNull();

        EmbeddingMatch<TextSegment> secondMatch = searchResult.matches().get(1);
        assertThat(secondMatch.score())
                .isCloseTo(
                        RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(firstEmbedding, secondEmbedding)),
                        percentage());
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        if (assertEmbedding()) {
            assertThat(CosineSimilarity.between(secondMatch.embedding(), secondEmbedding))
                    .isCloseTo(1, withPercentage(0.01)); // TODO return strict check back once Qdrant fixes it
        }
        assertThat(secondMatch.embedded()).isNull();
    }

    @Test
    void should_add_multiple_embeddings_with_segments() {
        // given
        TextSegment firstSegment = TextSegment.from("hello");
        Embedding firstEmbedding = embeddingModel().embed(firstSegment.text()).content();

        TextSegment secondSegment = TextSegment.from("hi");
        Embedding secondEmbedding = embeddingModel().embed(secondSegment.text()).content();

        List<String> ids =
                embeddingStore().addAll(asList(firstEmbedding, secondEmbedding), asList(firstSegment, secondSegment));
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(2));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);

        // then
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isNotBlank();
        assertThat(ids.get(1)).isNotBlank();
        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));

        assertThat(searchResult.matches()).hasSize(2);
        EmbeddingMatch<TextSegment> firstMatch = searchResult.matches().get(0);
        assertThat(firstMatch.score()).isCloseTo(1, percentage());
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        if (assertEmbedding()) {
            assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        }
        assertThat(firstMatch.embedded()).isEqualTo(firstSegment);

        EmbeddingMatch<TextSegment> secondMatch = searchResult.matches().get(1);
        assertThat(secondMatch.score())
                .isCloseTo(
                        RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(firstEmbedding, secondEmbedding)),
                        percentage());
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        if (assertEmbedding()) {
            assertThat(CosineSimilarity.between(secondMatch.embedding(), secondEmbedding))
                    .isCloseTo(1, withPercentage(0.01)); // TODO return strict check back once Qdrant fixes it
        }
        assertThat(secondMatch.embedded()).isEqualTo(secondSegment);
    }

    @Test
    void should_add_multiple_embeddings_with_ids_and_segments() {

        final String id1 = randomUUID();
        final String id2 = randomUUID();

        // given
        TextSegment firstSegment = TextSegment.from("hello");
        Embedding firstEmbedding = embeddingModel().embed(firstSegment.text()).content();

        TextSegment secondSegment = TextSegment.from("hi");
        Embedding secondEmbedding = embeddingModel().embed(secondSegment.text()).content();

        embeddingStore()
                .addAll(asList(id1, id2), asList(firstEmbedding, secondEmbedding), asList(firstSegment, secondSegment));
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(2));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);

        // then
        assertThat(searchResult.matches()).hasSize(2);
        assertThat(searchResult.matches().get(0)).isNotNull();
        assertThat(searchResult.matches().get(1)).isNotNull();
        assertThat(searchResult.matches().get(0).embeddingId()).isEqualTo(id1);
        assertThat(searchResult.matches().get(1).embeddingId()).isEqualTo(id2);

        EmbeddingMatch<TextSegment> firstMatch = searchResult.matches().get(0);
        assertThat(firstMatch.score()).isCloseTo(1, percentage());
        assertThat(firstMatch.embeddingId()).isEqualTo(id1);
        if (assertEmbedding()) {
            assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        }
        assertThat(firstMatch.embedded()).isEqualTo(firstSegment);

        EmbeddingMatch<TextSegment> secondMatch = searchResult.matches().get(1);
        assertThat(secondMatch.score())
                .isCloseTo(
                        RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(firstEmbedding, secondEmbedding)),
                        percentage());
        assertThat(secondMatch.embeddingId()).isEqualTo(id2);
        if (assertEmbedding()) {
            assertThat(CosineSimilarity.between(secondMatch.embedding(), secondEmbedding))
                    .isCloseTo(1, withPercentage(0.01)); // TODO return strict check back once Qdrant fixes it
        }
        assertThat(secondMatch.embedded()).isEqualTo(secondSegment);
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

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);

        // then
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();
        assertThat(matches).hasSize(2);

        EmbeddingMatch<TextSegment> firstMatch = matches.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, percentage());
        assertThat(firstMatch.embeddingId()).isEqualTo(firstId);

        EmbeddingMatch<TextSegment> secondMatch = matches.get(1);
        assertThat(secondMatch.score())
                .isCloseTo(
                        RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(firstEmbedding, secondEmbedding)),
                        percentage());
        assertThat(secondMatch.embeddingId()).isEqualTo(secondId);


        // given
        EmbeddingSearchRequest searchRequest2 = EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .minScore(secondMatch.score() - 0.01)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult2 = embeddingStore().search(searchRequest2);
        List<EmbeddingMatch<TextSegment>> matches2 = searchResult2.matches();

        // then
        assertThat(matches2).hasSize(2);
        assertThat(matches2.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(matches2.get(1).embeddingId()).isEqualTo(secondId);


        // given
        EmbeddingSearchRequest searchRequest3 = EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .minScore(secondMatch.score())
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult3 = embeddingStore().search(searchRequest3);
        List<EmbeddingMatch<TextSegment>> matches3 = searchResult3.matches();

        // then
        assertThat(matches3).hasSize(2);
        assertThat(matches3.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(matches3.get(1).embeddingId()).isEqualTo(secondId);


        // given
        EmbeddingSearchRequest searchRequest4 = EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .minScore(secondMatch.score() + 0.01)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult4 = embeddingStore().search(searchRequest4);
        List<EmbeddingMatch<TextSegment>> matches4 = searchResult4.matches();

        // then
        assertThat(matches4).hasSize(1);
        assertThat(matches4.get(0).embeddingId()).isEqualTo(firstId);
    }

    @Test
    void should_return_correct_score() {
        // given
        Embedding embedding = embeddingModel().embed("hello").content();
        String id = embeddingStore().add(embedding);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        Embedding referenceEmbedding = embeddingModel().embed("hi").content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(1)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);

        // then
        assertThat(id).isNotBlank();

        assertThat(searchResult.matches()).hasSize(1);
        EmbeddingMatch<TextSegment> match = searchResult.matches().get(0);
        assertThat(match.score())
                .isCloseTo(
                        RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(embedding, referenceEmbedding)),
                        percentage());
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
