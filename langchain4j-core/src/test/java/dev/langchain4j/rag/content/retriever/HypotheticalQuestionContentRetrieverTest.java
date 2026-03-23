package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.data.segment.HypotheticalQuestionTextSegmentTransformer.ORIGINAL_TEXT_METADATA_KEY;
import static dev.langchain4j.rag.content.ContentMetadata.EMBEDDING_ID;
import static dev.langchain4j.rag.content.ContentMetadata.SCORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HypotheticalQuestionContentRetrieverTest {

    @Test
    void should_restore_deduplicate_and_limit_original_segments() {

        // given
        Query query = Query.from("query");
        Content first = question("Question 1?", "Original text", "a.txt", 0.9, "id-1");
        Content duplicate = question("Question 2?", "Original text", "a.txt", 0.8, "id-2");
        Content sameTextFromAnotherSource = question("Question 3?", "Original text", "b.txt", 0.7, "id-3");
        Content ignoredByLimit = question("Question 4?", "Another text", "c.txt", 0.6, "id-4");
        ContentRetriever delegate = mock(ContentRetriever.class);
        when(delegate.retrieve(query)).thenReturn(List.of(first, duplicate, sameTextFromAnotherSource, ignoredByLimit));
        ContentRetriever retriever = new HypotheticalQuestionContentRetriever(delegate, 2);

        // when
        List<Content> result = retriever.retrieve(query);

        // then
        assertThat(result)
                .containsExactly(
                        Content.from(
                                TextSegment.from("Original text", Metadata.from("source", "a.txt")),
                                Map.of(SCORE, 0.9, EMBEDDING_ID, "id-1")),
                        Content.from(
                                TextSegment.from("Original text", Metadata.from("source", "b.txt")),
                                Map.of(SCORE, 0.7, EMBEDDING_ID, "id-3")));
        assertThat(result.get(0).metadata()).containsEntry(SCORE, 0.9).containsEntry(EMBEDDING_ID, "id-1");
        verify(delegate).retrieve(query);
    }

    @Test
    void should_reject_segment_without_original_text() {

        // given
        ContentRetriever delegate = query -> List.of(Content.from("not an HQE segment"));
        ContentRetriever retriever = new HypotheticalQuestionContentRetriever(delegate);

        // when / then
        assertThatThrownBy(() -> retriever.retrieve(Query.from("query")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ORIGINAL_TEXT_METADATA_KEY);
    }

    private static Content question(
            String question, String originalText, String source, double score, String embeddingId) {
        TextSegment segment = TextSegment.from(
                question,
                new Metadata().put(ORIGINAL_TEXT_METADATA_KEY, originalText).put("source", source));
        return Content.from(segment, Map.of(SCORE, score, EMBEDDING_ID, embeddingId));
    }
}
