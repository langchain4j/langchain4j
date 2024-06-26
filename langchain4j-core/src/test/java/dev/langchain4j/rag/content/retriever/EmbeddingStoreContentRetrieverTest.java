package dev.langchain4j.rag.content.retriever;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EmbeddingStoreContentRetrieverTest {

    private static EmbeddingStore<TextSegment> EMBEDDING_STORE;

    private static EmbeddingModel EMBEDDING_MODEL;
    private static final Embedding EMBEDDING = Embedding.from(asList(1f, 2f, 3f));

    private static final Query QUERY = Query.from("query");

    private static final int DEFAULT_MAX_RESULTS = 3;
    private static final int CUSTOM_MAX_RESULTS = 1;

    private static final double CUSTOM_MIN_SCORE = 0.7;
    public static final double DEFAULT_MIN_SCORE = 0.0;

    @BeforeEach
    void beforeEach() {
        EMBEDDING_STORE = mock(EmbeddingStore.class);
        when(EMBEDDING_STORE.search(any())).thenReturn(new EmbeddingSearchResult<>(asList(
                new EmbeddingMatch<>(0.9, "id 1", null, TextSegment.from("content 1")),
                new EmbeddingMatch<>(0.7, "id 2", null, TextSegment.from("content 2"))
        )));

        EMBEDDING_MODEL = mock(EmbeddingModel.class);
        when(EMBEDDING_MODEL.embed(anyString())).thenReturn(Response.from(EMBEDDING));
    }
    
    @Test
    void should_retrieve() {

        // given
        ContentRetriever contentRetriever = new EmbeddingStoreContentRetriever(EMBEDDING_STORE, EMBEDDING_MODEL);

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE).search(EmbeddingSearchRequest.builder()
                .queryEmbedding(EMBEDDING)
                .maxResults(DEFAULT_MAX_RESULTS)
                .minScore(DEFAULT_MIN_SCORE)
                .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_builder() {

        // given
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE).search(EmbeddingSearchRequest.builder()
                .queryEmbedding(EMBEDDING)
                .maxResults(DEFAULT_MAX_RESULTS)
                .minScore(DEFAULT_MIN_SCORE)
                .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_maxResults() {

        // given
        ContentRetriever contentRetriever = new EmbeddingStoreContentRetriever(
                EMBEDDING_STORE,
                EMBEDDING_MODEL,
                CUSTOM_MAX_RESULTS
        );

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE).search(EmbeddingSearchRequest.builder()
                .queryEmbedding(EMBEDDING)
                .maxResults(CUSTOM_MAX_RESULTS)
                .minScore(DEFAULT_MIN_SCORE)
                .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_maxResults_builder() {

        // given
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .maxResults(CUSTOM_MAX_RESULTS)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE).search(EmbeddingSearchRequest.builder()
                .queryEmbedding(EMBEDDING)
                .maxResults(CUSTOM_MAX_RESULTS)
                .minScore(DEFAULT_MIN_SCORE)
                .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_dynamicMaxResults_builder() {

        // given
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .dynamicMaxResults((query) -> CUSTOM_MAX_RESULTS)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE).search(EmbeddingSearchRequest.builder()
                .queryEmbedding(EMBEDDING)
                .maxResults(CUSTOM_MAX_RESULTS)
                .minScore(DEFAULT_MIN_SCORE)
                .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_minScore_ctor() {

        // given
        ContentRetriever contentRetriever = new EmbeddingStoreContentRetriever(
                EMBEDDING_STORE,
                EMBEDDING_MODEL,
                null,
                CUSTOM_MIN_SCORE
        );

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE).search(EmbeddingSearchRequest.builder()
                .queryEmbedding(EMBEDDING)
                .maxResults(DEFAULT_MAX_RESULTS)
                .minScore(CUSTOM_MIN_SCORE)
                .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_minScore_builder() {

        // given
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .minScore(CUSTOM_MIN_SCORE)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE).search(EmbeddingSearchRequest.builder()
                .queryEmbedding(EMBEDDING)
                .maxResults(DEFAULT_MAX_RESULTS)
                .minScore(CUSTOM_MIN_SCORE)
                .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_dynamicMinScore_builder() {

        // given
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .dynamicMinScore((query) -> CUSTOM_MIN_SCORE)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE).search(EmbeddingSearchRequest.builder()
                .queryEmbedding(EMBEDDING)
                .maxResults(DEFAULT_MAX_RESULTS)
                .minScore(CUSTOM_MIN_SCORE)
                .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_filter() {

        // given
        Filter metadataFilter = metadataKey("key").isEqualTo("value");

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .filter(metadataFilter)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE).search(EmbeddingSearchRequest.builder()
                .queryEmbedding(EMBEDDING)
                .maxResults(DEFAULT_MAX_RESULTS)
                .minScore(DEFAULT_MIN_SCORE)
                .filter(metadataFilter)
                .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_dynamicFilter() {

        // given
        Filter metadataFilter = metadataKey("key").isEqualTo("value");

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .dynamicFilter((query) -> metadataFilter)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE).search(EmbeddingSearchRequest.builder()
                .queryEmbedding(EMBEDDING)
                .maxResults(DEFAULT_MAX_RESULTS)
                .minScore(DEFAULT_MIN_SCORE)
                .filter(metadataFilter)
                .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_include_explicit_display_name_in_to_string() {

        // given
        double minScore = 0.7;
        String displayName = "MyName";
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .displayName(displayName)
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .minScore(minScore)
                .build();

        // when
        String result = contentRetriever.toString();

        // then
        assertThat(result).contains(displayName);
    }

    @Test
    void should_include_implicit_display_name_in_to_string() {

        // given
        double minScore = 0.7;
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .minScore(minScore)
                .build();

        // when
        String result = contentRetriever.toString();

        // then
        assertThat(result).contains(EmbeddingStoreContentRetriever.DEFAULT_DISPLAY_NAME);
    }
}