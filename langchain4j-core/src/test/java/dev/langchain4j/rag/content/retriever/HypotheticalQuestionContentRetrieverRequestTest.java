package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HypotheticalQuestionContentRetrieverRequestTest {

    private static final Query QUERY = Query.from("What is photosynthesis?");
    private static final Embedding EMBEDDING = Embedding.from(asList(1f, 2f, 3f));

    private EmbeddingStore<TextSegment> embeddingStore;
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void beforeEach() {
        embeddingStore = mock(EmbeddingStore.class);
        embeddingModel = mock(EmbeddingModel.class);
        when(embeddingStore.search(any())).thenReturn(new EmbeddingSearchResult<>(java.util.List.of()));
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(EMBEDDING));
    }

    @Test
    void should_search_with_default_request_parameters() {

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        retriever.retrieve(QUERY);

        verify(embeddingStore)
                .search(EmbeddingSearchRequest.builder()
                        .query(QUERY.text())
                        .queryEmbedding(EMBEDDING)
                        .maxResults(HypotheticalQuestionContentRetriever.DEFAULT_MAX_RESULTS * 3)
                        .minScore(HypotheticalQuestionContentRetriever.DEFAULT_MIN_SCORE)
                        .build());
        verifyNoMoreInteractions(embeddingStore);
        verify(embeddingModel).embed(QUERY.text());
        verifyNoMoreInteractions(embeddingModel);
    }

    @Test
    void should_search_with_custom_request_parameters() {

        Filter filter = metadataKey("source").isEqualTo("plants");

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .candidateMaxResults(6)
                .minScore(0.7)
                .filter(filter)
                .build();

        retriever.retrieve(QUERY);

        verify(embeddingStore)
                .search(EmbeddingSearchRequest.builder()
                        .query(QUERY.text())
                        .queryEmbedding(EMBEDDING)
                        .maxResults(6)
                        .minScore(0.7)
                        .filter(filter)
                        .build());
        verifyNoMoreInteractions(embeddingStore);
        verify(embeddingModel).embed(QUERY.text());
        verifyNoMoreInteractions(embeddingModel);
    }

    @Test
    void should_search_with_dynamic_request_parameters() {

        Filter filter = metadataKey("source").isEqualTo("plants");

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(10)
                .dynamicMaxResults(query -> 2)
                .candidateMaxResults(10)
                .dynamicCandidateMaxResults(query -> 6)
                .minScore(0.1)
                .dynamicMinScore(query -> 0.7)
                .dynamicFilter(query -> filter)
                .build();

        retriever.retrieve(QUERY);

        verify(embeddingStore)
                .search(EmbeddingSearchRequest.builder()
                        .query(QUERY.text())
                        .queryEmbedding(EMBEDDING)
                        .maxResults(6)
                        .minScore(0.7)
                        .filter(filter)
                        .build());
        verifyNoMoreInteractions(embeddingStore);
        verify(embeddingModel).embed(QUERY.text());
        verifyNoMoreInteractions(embeddingModel);
    }

    @Test
    void should_default_candidate_max_results_from_dynamic_max_results() {

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .dynamicMaxResults(query -> 2)
                .build();

        retriever.retrieve(QUERY);

        verify(embeddingStore)
                .search(EmbeddingSearchRequest.builder()
                        .query(QUERY.text())
                        .queryEmbedding(EMBEDDING)
                        .maxResults(6)
                        .minScore(HypotheticalQuestionContentRetriever.DEFAULT_MIN_SCORE)
                        .build());
        verifyNoMoreInteractions(embeddingStore);
        verify(embeddingModel).embed(QUERY.text());
        verifyNoMoreInteractions(embeddingModel);
    }
}
