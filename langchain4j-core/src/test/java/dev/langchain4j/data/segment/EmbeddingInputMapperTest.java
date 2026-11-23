package dev.langchain4j.data.segment;

import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingInputMapperTest {

    private static final EmbeddingInputMapper MAPPER = segment -> List.of(MappedSegment.same(segment));

    @Test
    void should_map_all_segments() {

        TextSegment first = textSegment("first");
        TextSegment second = textSegment("second");

        assertThat(MAPPER.mapAll(List.of(first, second)))
                .containsExactly(MappedSegment.same(first), MappedSegment.same(second));
    }

    @Test
    void should_filter_out_segment_when_map_returns_null() {

        EmbeddingInputMapper mapper =
                segment -> "filtered".equals(segment.text()) ? null : List.of(MappedSegment.same(segment));

        assertThat(mapper.mapAll(List.of(textSegment("kept"), textSegment("filtered"))))
                .containsExactly(MappedSegment.same(textSegment("kept")));
    }
}
