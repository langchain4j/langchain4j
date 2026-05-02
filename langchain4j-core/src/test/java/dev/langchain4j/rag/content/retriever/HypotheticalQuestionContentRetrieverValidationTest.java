package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.data.segment.HypotheticalQuestionTextSegmentTransformer.ORIGINAL_TEXT_METADATA_KEY;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HypotheticalQuestionContentRetrieverValidationTest {

    private EmbeddingStore<TextSegment> embeddingStore;
    private EmbeddingModel embeddingModel;

    private static final Embedding EMBEDDING = Embedding.from(asList(1f, 2f, 3f));
    private static final Query QUERY = Query.from("What is photosynthesis?");

    @BeforeEach
    void beforeEach() {
        embeddingStore = mock(EmbeddingStore.class);
        embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(EMBEDDING));
    }

    @Test
    void should_reject_match_when_hqe_metadata_missing() {

        TextSegment segment = TextSegment.from("Direct text without HQE metadata.");
        when(embeddingStore.search(any()))
                .thenReturn(new EmbeddingSearchResult<>(List.of(new EmbeddingMatch<>(0.8, "id-1", null, segment))));

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        assertThatThrownBy(() -> retriever.retrieve(QUERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ORIGINAL_TEXT_METADATA_KEY);
    }

    @Test
    void should_reject_match_when_embedded_segment_missing() {

        when(embeddingStore.search(any()))
                .thenReturn(new EmbeddingSearchResult<>(List.of(new EmbeddingMatch<>(0.8, "id-1", null, null))));

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        assertThatThrownBy(() -> retriever.retrieve(QUERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EmbeddingMatch.embedded()");
    }

    @Test
    void should_reject_null_embedding_store() {
        assertThatThrownBy(() -> HypotheticalQuestionContentRetriever.builder()
                        .embeddingModel(embeddingModel)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_null_embedding_model() {
        assertThatThrownBy(() -> HypotheticalQuestionContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_candidate_max_results_smaller_than_max_results() {
        assertThatThrownBy(() -> HypotheticalQuestionContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .maxResults(3)
                        .candidateMaxResults(2)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidateMaxResults");
    }

    @Test
    void should_reject_invalid_min_score() {
        assertThatThrownBy(() -> HypotheticalQuestionContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .minScore(1.1)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minScore");
    }

    @Test
    void should_reject_mixed_hqe_and_non_hqe_results() {

        TextSegment hqeSegment = TextSegment.from(
                "What is Java?", new Metadata().put(ORIGINAL_TEXT_METADATA_KEY, "Java is a programming language."));
        TextSegment nonHqeSegment = TextSegment.from("Python is a programming language.");

        when(embeddingStore.search(any()))
                .thenReturn(new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.9, "id-1", null, hqeSegment),
                        new EmbeddingMatch<>(0.8, "id-2", null, nonHqeSegment))));

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        assertThatThrownBy(() -> retriever.retrieve(QUERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ORIGINAL_TEXT_METADATA_KEY);
    }
}
