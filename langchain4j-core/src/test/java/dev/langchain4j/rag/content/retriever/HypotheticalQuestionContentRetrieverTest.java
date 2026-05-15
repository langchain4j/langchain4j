package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.data.segment.HypotheticalQuestionTextSegmentTransformer.ORIGINAL_TEXT_METADATA_KEY;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HypotheticalQuestionContentRetrieverTest {

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
    void should_retrieve_original_text_from_hqe_metadata() {

        // given
        TextSegment questionSegment = TextSegment.from(
                "What is photosynthesis?",
                new Metadata().put(ORIGINAL_TEXT_METADATA_KEY, "Plants convert sunlight into energy."));

        when(embeddingStore.search(any()))
                .thenReturn(
                        new EmbeddingSearchResult<>(List.of(new EmbeddingMatch<>(0.9, "id-1", null, questionSegment))));

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        // when
        List<Content> results = retriever.retrieve(QUERY);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).textSegment().text()).isEqualTo("Plants convert sunlight into energy.");
    }

    @Test
    void should_deduplicate_by_original_text() {

        // given
        TextSegment q1 = TextSegment.from(
                "What is photosynthesis?",
                new Metadata().put(ORIGINAL_TEXT_METADATA_KEY, "Plants convert sunlight into energy."));
        TextSegment q2 = TextSegment.from(
                "How do plants make food?",
                new Metadata().put(ORIGINAL_TEXT_METADATA_KEY, "Plants convert sunlight into energy."));

        when(embeddingStore.search(any()))
                .thenReturn(new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.9, "id-1", null, q1), new EmbeddingMatch<>(0.8, "id-2", null, q2))));

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        // when
        List<Content> results = retriever.retrieve(QUERY);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).textSegment().text()).isEqualTo("Plants convert sunlight into energy.");
    }

    @Test
    void should_keep_max_score_when_deduplicating() {

        // given
        TextSegment q1 =
                TextSegment.from("Question A?", new Metadata().put(ORIGINAL_TEXT_METADATA_KEY, "Same original text."));
        TextSegment q2 =
                TextSegment.from("Question B?", new Metadata().put(ORIGINAL_TEXT_METADATA_KEY, "Same original text."));

        when(embeddingStore.search(any()))
                .thenReturn(new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.7, "id-1", null, q1), new EmbeddingMatch<>(0.95, "id-2", null, q2))));

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        // when
        List<Content> results = retriever.retrieve(QUERY);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).metadata().get(ContentMetadata.SCORE)).isEqualTo(0.95);
        assertThat(results.get(0).metadata().get(ContentMetadata.EMBEDDING_ID)).isEqualTo("id-2");
    }

    @Test
    void should_limit_results_to_maxResults() {

        // given
        TextSegment q1 = TextSegment.from("Q1?", new Metadata().put(ORIGINAL_TEXT_METADATA_KEY, "Text A."));
        TextSegment q2 = TextSegment.from("Q2?", new Metadata().put(ORIGINAL_TEXT_METADATA_KEY, "Text B."));
        TextSegment q3 = TextSegment.from("Q3?", new Metadata().put(ORIGINAL_TEXT_METADATA_KEY, "Text C."));

        when(embeddingStore.search(any()))
                .thenReturn(new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.9, "id-1", null, q1),
                        new EmbeddingMatch<>(0.8, "id-2", null, q2),
                        new EmbeddingMatch<>(0.7, "id-3", null, q3))));

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .build();

        // when
        List<Content> results = retriever.retrieve(QUERY);

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).textSegment().text()).isEqualTo("Text A.");
        assertThat(results.get(1).textSegment().text()).isEqualTo("Text B.");
    }

    @Test
    void should_sort_results_by_score_descending() {

        // given
        TextSegment q1 = TextSegment.from("Q1?", new Metadata().put(ORIGINAL_TEXT_METADATA_KEY, "Low score text."));
        TextSegment q2 = TextSegment.from("Q2?", new Metadata().put(ORIGINAL_TEXT_METADATA_KEY, "High score text."));

        when(embeddingStore.search(any()))
                .thenReturn(new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.5, "id-1", null, q1), new EmbeddingMatch<>(0.9, "id-2", null, q2))));

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        // when
        List<Content> results = retriever.retrieve(QUERY);

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).textSegment().text()).isEqualTo("High score text.");
        assertThat(results.get(1).textSegment().text()).isEqualTo("Low score text.");
    }

    @Test
    void should_not_deduplicate_same_original_text_from_different_sources() {

        // given
        TextSegment q1 = TextSegment.from(
                "Question A?",
                new Metadata().put(ORIGINAL_TEXT_METADATA_KEY, "Shared text.").put("file_name", "a.txt"));
        TextSegment q2 = TextSegment.from(
                "Question B?",
                new Metadata().put(ORIGINAL_TEXT_METADATA_KEY, "Shared text.").put("file_name", "b.txt"));

        when(embeddingStore.search(any()))
                .thenReturn(new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.9, "id-1", null, q1), new EmbeddingMatch<>(0.8, "id-2", null, q2))));

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .build();

        // when
        List<Content> results = retriever.retrieve(QUERY);

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).textSegment().text()).isEqualTo("Shared text.");
        assertThat(results.get(0).textSegment().metadata().getString("file_name"))
                .isEqualTo("a.txt");
        assertThat(results.get(1).textSegment().text()).isEqualTo("Shared text.");
        assertThat(results.get(1).textSegment().metadata().getString("file_name"))
                .isEqualTo("b.txt");
    }

    @Test
    void should_return_empty_list_when_no_matches() {

        // given
        when(embeddingStore.search(any())).thenReturn(new EmbeddingSearchResult<>(List.of()));

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        // when
        List<Content> results = retriever.retrieve(QUERY);

        // then
        assertThat(results).isEmpty();
    }

    @Test
    void should_preserve_original_metadata_without_hqe_key() {

        // given
        Metadata meta = new Metadata()
                .put("file_name", "doc.txt")
                .put("author", "Alice")
                .put(ORIGINAL_TEXT_METADATA_KEY, "Original text.");
        TextSegment questionSegment = TextSegment.from("What is this?", meta);

        when(embeddingStore.search(any()))
                .thenReturn(
                        new EmbeddingSearchResult<>(List.of(new EmbeddingMatch<>(0.9, "id-1", null, questionSegment))));

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        // when
        List<Content> results = retriever.retrieve(QUERY);

        // then
        assertThat(results).hasSize(1);
        TextSegment resultSegment = results.get(0).textSegment();
        assertThat(resultSegment.text()).isEqualTo("Original text.");
        assertThat(resultSegment.metadata().getString("file_name")).isEqualTo("doc.txt");
        assertThat(resultSegment.metadata().getString("author")).isEqualTo("Alice");
        // hqe_original_text should be removed from the result metadata
        assertThat(resultSegment.metadata().getString(ORIGINAL_TEXT_METADATA_KEY))
                .isNull();
    }

    @Test
    void should_have_correct_default_values() {

        // given
        when(embeddingStore.search(any())).thenReturn(new EmbeddingSearchResult<>(List.of()));

        HypotheticalQuestionContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        // then
        assertThat(retriever.toString()).contains("Default");
    }
}
