package dev.langchain4j.data.segment;

import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingInputMapperTest {

    private static final EmbeddingInputMapper MAPPER = new EmbeddingInputMapper() {
        @Override
        public List<TextSegment> map(TextSegment segment) {
            return List.of(segment);
        }
    };

    @Test
    void should_create_original_segment_list_for_single_segment() {

        TextSegment segment = textSegment("original");
        List<TextSegment> mapped = List.of(textSegment("mapped 1"), textSegment("mapped 2"));

        assertThat(MAPPER.originalSegmentList(segment, mapped)).containsExactly(segment, segment);
    }

    @Test
    void should_return_empty_original_segment_list_when_mapped_segments_is_empty_or_null() {

        TextSegment segment = textSegment("original");

        assertThat(MAPPER.originalSegmentList(segment, List.of())).isEmpty();
        assertThat(MAPPER.originalSegmentList(segment, null)).isEmpty();
    }

    @Test
    void should_create_original_segment_lists_for_batch() {

        TextSegment first = textSegment("first");
        TextSegment second = textSegment("second");
        List<List<TextSegment>> mapped = List.of(
                List.of(textSegment("mapped first 1"), textSegment("mapped first 2")),
                List.of(textSegment("mapped second")));

        assertThat(MAPPER.originalSegmentList(List.of(first, second), mapped))
                .containsExactly(List.of(first, first), List.of(second));
    }

    @Test
    void should_reject_batch_lists_with_different_sizes() {

        TextSegment first = textSegment("first");
        TextSegment second = textSegment("second");

        assertThatThrownBy(() -> MAPPER.originalSegmentList(
                        List.of(first, second), List.of(List.of(textSegment("mapped first")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");
    }
}
