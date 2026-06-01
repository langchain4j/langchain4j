package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        when(EMBEDDING_STORE.search(any()))
                .thenReturn(new EmbeddingSearchResult<>(asList(
                        new EmbeddingMatch<>(0.9, "id 1", null, TextSegment.from("content 1")),
                        new EmbeddingMatch<>(0.7, "id 2", null, TextSegment.from("content 2")))));

        EMBEDDING_MODEL = mock(EmbeddingModel.class);
        when(EMBEDDING_MODEL.embed(anyString())).thenReturn(Response.from(EMBEDDING));
    }

    @Test
    void should_retrieve_with_default_settings() {
        // given
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .query(QUERY.text())
                        .queryEmbedding(EMBEDDING)
                        .maxResults(DEFAULT_MAX_RESULTS)
                        .minScore(DEFAULT_MIN_SCORE)
                        .build());
    }

    @Test
    void should_retrieve_with_custom_max_results() {
        // given
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .maxResults(CUSTOM_MAX_RESULTS)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .query(QUERY.text())
                        .queryEmbedding(EMBEDDING)
                        .maxResults(CUSTOM_MAX_RESULTS)
                        .minScore(DEFAULT_MIN_SCORE)
                        .build());
    }

    @Test
    void should_retrieve_with_custom_min_score() {
        // given
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .minScore(CUSTOM_MIN_SCORE)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .query(QUERY.text())
                        .queryEmbedding(EMBEDDING)
                        .maxResults(DEFAULT_MAX_RESULTS)
                        .minScore(CUSTOM_MIN_SCORE)
                        .build());
    }

    @Test
    void should_retrieve_with_custom_filter() {
        // given
        Filter filter = metadataKey("key").isEqualTo("value");
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .filter(filter)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .query(QUERY.text())
                        .queryEmbedding(EMBEDDING)
                        .maxResults(DEFAULT_MAX_RESULTS)
                        .minScore(DEFAULT_MIN_SCORE)
                        .filter(filter)
                        .build());
    }

    @Test
    void should_retrieve_with_dynamic_max_results_and_min_score() {
        // given
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .dynamicMaxResults(query -> 1)
                .dynamicMinScore(query -> 0.1)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .query(QUERY.text())
                        .queryEmbedding(EMBEDDING)
                        .maxResults(1)
                        .minScore(0.1)
                        .build());
    }

    @Test
    void should_validate_required_fields_in_builder() {
        // when/then - missing embedding store
        assertThatThrownBy(() -> EmbeddingStoreContentRetriever.builder()
                        .embeddingModel(EMBEDDING_MODEL)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);

        // when/then - missing embedding model
        assertThatThrownBy(() -> EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(EMBEDDING_STORE)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== deduplicateOverlap tests ====================

    @Test
    void deduplicateOverlap_shouldRemoveOverlapFromAdjacentSegments() {
        // Segment 0: "the quick brown fox"
        // Segment 1: "brown fox jumps over" <- "brown fox" is the overlap
        dev.langchain4j.data.document.Metadata meta0 = dev.langchain4j.data.document.Metadata.from("index", "0");
        dev.langchain4j.data.document.Metadata meta1 = dev.langchain4j.data.document.Metadata.from("index", "1");

        Content c0 = Content.from(TextSegment.from("the quick brown fox", meta0));
        Content c1 = Content.from(TextSegment.from("brown fox jumps over", meta1));

        List<Content> result = EmbeddingStoreContentRetriever.deduplicateOverlap(asList(c0, c1));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).textSegment().text()).isEqualTo("the quick brown fox");
        assertThat(result.get(1).textSegment().text()).isEqualTo(" jumps over");
    }

    @Test
    void deduplicateOverlap_shouldNotModifyNonAdjacentSegments() {
        // Segments 0 and 2 are not adjacent — no deduplication should occur
        dev.langchain4j.data.document.Metadata meta0 = dev.langchain4j.data.document.Metadata.from("index", "0");
        dev.langchain4j.data.document.Metadata meta2 = dev.langchain4j.data.document.Metadata.from("index", "2");

        Content c0 = Content.from(TextSegment.from("the quick brown fox", meta0));
        Content c2 = Content.from(TextSegment.from("brown fox jumps over", meta2));

        List<Content> result = EmbeddingStoreContentRetriever.deduplicateOverlap(asList(c0, c2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).textSegment().text()).isEqualTo("the quick brown fox");
        assertThat(result.get(1).textSegment().text()).isEqualTo("brown fox jumps over");
    }

    @Test
    void deduplicateOverlap_shouldNotModifySegmentsFromDifferentDocuments() {
        // Same index values but different documents — no deduplication
        dev.langchain4j.data.document.Metadata meta0 = new dev.langchain4j.data.document.Metadata();
        meta0.put("index", "0");
        meta0.put("file_name", "doc1.txt");

        dev.langchain4j.data.document.Metadata meta1 = new dev.langchain4j.data.document.Metadata();
        meta1.put("index", "1");
        meta1.put("file_name", "doc2.txt");

        Content c0 = Content.from(TextSegment.from("the quick brown fox", meta0));
        Content c1 = Content.from(TextSegment.from("brown fox jumps over", meta1));

        List<Content> result = EmbeddingStoreContentRetriever.deduplicateOverlap(asList(c0, c1));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).textSegment().text()).isEqualTo("the quick brown fox");
        assertThat(result.get(1).textSegment().text()).isEqualTo("brown fox jumps over");
    }

    @Test
    void deduplicateOverlap_shouldNotModifySegmentsWithNoIndexMetadata() {
        // Segments have no "index" metadata — cannot determine adjacency, leave unchanged
        Content c0 = Content.from(TextSegment.from("the quick brown fox"));
        Content c1 = Content.from(TextSegment.from("brown fox jumps over"));

        List<Content> result = EmbeddingStoreContentRetriever.deduplicateOverlap(asList(c0, c1));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).textSegment().text()).isEqualTo("the quick brown fox");
        assertThat(result.get(1).textSegment().text()).isEqualTo("brown fox jumps over");
    }

    @Test
    void deduplicateOverlap_shouldReturnSingleContentUnchanged() {
        dev.langchain4j.data.document.Metadata meta0 = dev.langchain4j.data.document.Metadata.from("index", "0");
        Content c0 = Content.from(TextSegment.from("the quick brown fox", meta0));

        List<Content> result = EmbeddingStoreContentRetriever.deduplicateOverlap(asList(c0));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).textSegment().text()).isEqualTo("the quick brown fox");
    }

    @Test
    void deduplicateOverlap_shouldHandleNoOverlap() {
        dev.langchain4j.data.document.Metadata meta0 = dev.langchain4j.data.document.Metadata.from("index", "0");
        dev.langchain4j.data.document.Metadata meta1 = dev.langchain4j.data.document.Metadata.from("index", "1");

        Content c0 = Content.from(TextSegment.from("the quick brown fox", meta0));
        Content c1 = Content.from(TextSegment.from("jumps over the lazy dog", meta1));

        List<Content> result = EmbeddingStoreContentRetriever.deduplicateOverlap(asList(c0, c1));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).textSegment().text()).isEqualTo("the quick brown fox");
        assertThat(result.get(1).textSegment().text()).isEqualTo("jumps over the lazy dog");
    }

    @Test
    void deduplicateOverlap_shouldPreserveMetadataAfterDedup() {
        dev.langchain4j.data.document.Metadata meta0 = new dev.langchain4j.data.document.Metadata();
        meta0.put("index", "0");
        meta0.put("source", "wiki");

        dev.langchain4j.data.document.Metadata meta1 = new dev.langchain4j.data.document.Metadata();
        meta1.put("index", "1");
        meta1.put("source", "wiki");

        Content c0 = Content.from(TextSegment.from("the quick brown fox", meta0));
        Content c1 = Content.from(TextSegment.from("brown fox jumps over", meta1));

        List<Content> result = EmbeddingStoreContentRetriever.deduplicateOverlap(asList(c0, c1));

        assertThat(result.get(1).textSegment().metadata().getString("source")).isEqualTo("wiki");
        assertThat(result.get(1).textSegment().metadata().getString("index")).isEqualTo("1");
    }

    // ==================== longestSuffixPrefixOverlap tests ====================

    @Test
    void longestSuffixPrefixOverlap_shouldFindOverlap() {
        assertThat(EmbeddingStoreContentRetriever.longestSuffixPrefixOverlap(
                        "the quick brown fox", "brown fox jumps over"))
                .isEqualTo(9); // "brown fox"
    }

    @Test
    void longestSuffixPrefixOverlap_shouldReturnZeroWhenNoOverlap() {
        assertThat(EmbeddingStoreContentRetriever.longestSuffixPrefixOverlap("hello world", "foo bar"))
                .isEqualTo(0);
    }

    @Test
    void longestSuffixPrefixOverlap_shouldHandleFullOverlap() {
        assertThat(EmbeddingStoreContentRetriever.longestSuffixPrefixOverlap("hello", "hello"))
                .isEqualTo(5);
    }
}
