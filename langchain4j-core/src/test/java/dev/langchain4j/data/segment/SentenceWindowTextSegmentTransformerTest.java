package dev.langchain4j.data.segment;

import static dev.langchain4j.data.segment.SentenceWindowTextSegmentTransformer.SURROUNDING_CONTEXT_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class SentenceWindowTextSegmentTransformerTest {

    @Test
    void should_add_surrounding_context_and_preserve_metadata() {
        SentenceWindowTextSegmentTransformer transformer = new SentenceWindowTextSegmentTransformer(1, 1);
        Metadata metadata = Metadata.from("source", "doc.txt");
        TextSegment first = TextSegment.from("First", metadata.copy().put("index", "0"));
        TextSegment second = TextSegment.from("Second", metadata.copy().put("index", "1"));
        TextSegment third = TextSegment.from("Third", metadata.copy().put("index", "2"));

        List<TextSegment> result = transformer.transformAll(List.of(first, second, third));

        assertThat(result)
                .extracting(it -> it.metadata().getString(SURROUNDING_CONTEXT_KEY))
                .containsExactly("First\n\nSecond", "First\n\nSecond\n\nThird", "Second\n\nThird");
        assertThat(result)
                .allSatisfy(it -> assertThat(it.metadata().getString("source")).isEqualTo("doc.txt"));
    }

    @Test
    void should_not_cross_index_reset() {
        SentenceWindowTextSegmentTransformer transformer = new SentenceWindowTextSegmentTransformer(1, 1);
        TextSegment first = TextSegment.from("Doc 1", Metadata.from("index", "0"));
        TextSegment second = TextSegment.from("Doc 2", Metadata.from("index", "0"));

        List<TextSegment> result = transformer.transformAll(List.of(first, second));

        assertThat(result)
                .extracting(it -> it.metadata().getString(SURROUNDING_CONTEXT_KEY))
                .containsExactly("Doc 1", "Doc 2");
    }

    @Test
    void should_handle_single_and_empty_inputs() {
        SentenceWindowTextSegmentTransformer transformer =
                SentenceWindowTextSegmentTransformer.builder().build();
        TextSegment segment = TextSegment.from("Only");

        assertThat(transformer.transform(segment).metadata().getString(SURROUNDING_CONTEXT_KEY))
                .isEqualTo("Only");
        assertThat(transformer.transformAll(List.of())).isEmpty();
    }

    @Test
    void should_reject_negative_window_sizes() {
        assertThatThrownBy(() -> new SentenceWindowTextSegmentTransformer(-1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SentenceWindowTextSegmentTransformer(0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
