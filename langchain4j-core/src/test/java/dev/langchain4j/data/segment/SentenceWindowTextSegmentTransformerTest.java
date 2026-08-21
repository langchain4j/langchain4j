package dev.langchain4j.data.segment;

import static dev.langchain4j.data.segment.SentenceWindowTextSegmentTransformer.SURROUNDING_CONTEXT_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class SentenceWindowTextSegmentTransformerTest {

    @Test
    void should_enrich_segments_with_surrounding_context() {

        // given
        SentenceWindowTextSegmentTransformer transformer = SentenceWindowTextSegmentTransformer.builder()
                .segmentsBefore(1)
                .segmentsAfter(1)
                .build();

        Metadata docMeta = Metadata.from("file_name", "doc1.txt");
        TextSegment s0 = TextSegment.from("Sentence 0.", docMeta.copy().put("index", "0"));
        TextSegment s1 = TextSegment.from("Sentence 1.", docMeta.copy().put("index", "1"));
        TextSegment s2 = TextSegment.from("Sentence 2.", docMeta.copy().put("index", "2"));

        // when
        List<TextSegment> result = transformer.transformAll(List.of(s0, s1, s2));

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("Sentence 0.\n\nSentence 1.");
        assertThat(result.get(1).metadata().getString(SURROUNDING_CONTEXT_KEY))
                .isEqualTo("Sentence 0.\n\nSentence 1.\n\nSentence 2.");
        assertThat(result.get(2).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("Sentence 1.\n\nSentence 2.");
    }

    @Test
    void should_handle_larger_window_size() {

        // given
        SentenceWindowTextSegmentTransformer transformer = SentenceWindowTextSegmentTransformer.builder()
                .segmentsBefore(2)
                .segmentsAfter(2)
                .build();

        Metadata docMeta = Metadata.from("file_name", "doc1.txt");
        TextSegment s0 = TextSegment.from("S0.", docMeta.copy().put("index", "0"));
        TextSegment s1 = TextSegment.from("S1.", docMeta.copy().put("index", "1"));
        TextSegment s2 = TextSegment.from("S2.", docMeta.copy().put("index", "2"));
        TextSegment s3 = TextSegment.from("S3.", docMeta.copy().put("index", "3"));
        TextSegment s4 = TextSegment.from("S4.", docMeta.copy().put("index", "4"));

        // when
        List<TextSegment> result = transformer.transformAll(List.of(s0, s1, s2, s3, s4));

        // then
        assertThat(result).hasSize(5);
        assertThat(result.get(0).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("S0.\n\nS1.\n\nS2.");
        assertThat(result.get(1).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("S0.\n\nS1.\n\nS2.\n\nS3.");
        assertThat(result.get(2).metadata().getString(SURROUNDING_CONTEXT_KEY))
                .isEqualTo("S0.\n\nS1.\n\nS2.\n\nS3.\n\nS4.");
        assertThat(result.get(3).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("S1.\n\nS2.\n\nS3.\n\nS4.");
        assertThat(result.get(4).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("S2.\n\nS3.\n\nS4.");
    }

    @Test
    void should_not_cross_document_boundary_when_index_resets() {

        // given
        SentenceWindowTextSegmentTransformer transformer = SentenceWindowTextSegmentTransformer.builder()
                .segmentsBefore(1)
                .segmentsAfter(1)
                .build();

        TextSegment doc1s0 = TextSegment.from("Doc 1 sentence 0.", Metadata.from("index", "0"));
        TextSegment doc1s1 = TextSegment.from("Doc 1 sentence 1.", Metadata.from("index", "1"));
        TextSegment doc2s0 = TextSegment.from("Doc 2 sentence 0.", Metadata.from("index", "0"));
        TextSegment doc2s1 = TextSegment.from("Doc 2 sentence 1.", Metadata.from("index", "1"));

        // when
        List<TextSegment> result = transformer.transformAll(List.of(doc1s0, doc1s1, doc2s0, doc2s1));

        // then
        assertThat(result.get(0).metadata().getString(SURROUNDING_CONTEXT_KEY))
                .isEqualTo("Doc 1 sentence 0.\n\nDoc 1 sentence 1.");
        assertThat(result.get(1).metadata().getString(SURROUNDING_CONTEXT_KEY))
                .isEqualTo("Doc 1 sentence 0.\n\nDoc 1 sentence 1.");
        assertThat(result.get(2).metadata().getString(SURROUNDING_CONTEXT_KEY))
                .isEqualTo("Doc 2 sentence 0.\n\nDoc 2 sentence 1.");
        assertThat(result.get(3).metadata().getString(SURROUNDING_CONTEXT_KEY))
                .isEqualTo("Doc 2 sentence 0.\n\nDoc 2 sentence 1.");
    }

    @Test
    void should_preserve_original_metadata() {

        // given
        SentenceWindowTextSegmentTransformer transformer = SentenceWindowTextSegmentTransformer.builder()
                .segmentsBefore(1)
                .segmentsAfter(1)
                .build();

        Metadata meta = new Metadata()
                .put("file_name", "doc.txt")
                .put("author", "Alice")
                .put("index", "0");
        TextSegment segment = TextSegment.from("Hello.", meta);

        // when
        List<TextSegment> result = transformer.transformAll(List.of(segment));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).metadata().getString("file_name")).isEqualTo("doc.txt");
        assertThat(result.get(0).metadata().getString("author")).isEqualTo("Alice");
        assertThat(result.get(0).metadata().getString("index")).isEqualTo("0");
        assertThat(result.get(0).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("Hello.");
    }

    @Test
    void should_return_empty_list_for_empty_input() {

        // given
        SentenceWindowTextSegmentTransformer transformer =
                SentenceWindowTextSegmentTransformer.builder().build();

        // when
        List<TextSegment> result = transformer.transformAll(List.of());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void should_return_empty_list_for_null_input() {

        // given
        SentenceWindowTextSegmentTransformer transformer =
                SentenceWindowTextSegmentTransformer.builder().build();

        // when
        List<TextSegment> result = transformer.transformAll((List<TextSegment>) null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void should_enrich_single_segment_when_transforming() {

        // given
        SentenceWindowTextSegmentTransformer transformer =
                SentenceWindowTextSegmentTransformer.builder().build();

        TextSegment segment = TextSegment.from("Hello world.");

        // when
        TextSegment result = transformer.transform(segment);

        // then
        assertThat(result.text()).isEqualTo("Hello world.");
        assertThat(result.metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("Hello world.");
    }

    @Test
    void should_handle_segmentsBefore_zero() {

        // given
        SentenceWindowTextSegmentTransformer transformer = SentenceWindowTextSegmentTransformer.builder()
                .segmentsBefore(0)
                .segmentsAfter(1)
                .build();

        Metadata docMeta = Metadata.from("file_name", "doc.txt");
        TextSegment s0 = TextSegment.from("S0.", docMeta.copy().put("index", "0"));
        TextSegment s1 = TextSegment.from("S1.", docMeta.copy().put("index", "1"));
        TextSegment s2 = TextSegment.from("S2.", docMeta.copy().put("index", "2"));

        // when
        List<TextSegment> result = transformer.transformAll(List.of(s0, s1, s2));

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("S0.\n\nS1.");
        assertThat(result.get(1).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("S1.\n\nS2.");
        assertThat(result.get(2).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("S2.");
    }

    @Test
    void should_handle_segmentsAfter_zero() {

        // given
        SentenceWindowTextSegmentTransformer transformer = SentenceWindowTextSegmentTransformer.builder()
                .segmentsBefore(1)
                .segmentsAfter(0)
                .build();

        Metadata docMeta = Metadata.from("file_name", "doc.txt");
        TextSegment s0 = TextSegment.from("S0.", docMeta.copy().put("index", "0"));
        TextSegment s1 = TextSegment.from("S1.", docMeta.copy().put("index", "1"));
        TextSegment s2 = TextSegment.from("S2.", docMeta.copy().put("index", "2"));

        // when
        List<TextSegment> result = transformer.transformAll(List.of(s0, s1, s2));

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("S0.");
        assertThat(result.get(1).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("S0.\n\nS1.");
        assertThat(result.get(2).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("S1.\n\nS2.");
    }

    @Test
    void should_reject_negative_segmentsBefore() {
        assertThatThrownBy(() -> SentenceWindowTextSegmentTransformer.builder()
                        .segmentsBefore(-1)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_negative_segmentsAfter() {
        assertThatThrownBy(() -> SentenceWindowTextSegmentTransformer.builder()
                        .segmentsAfter(-1)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_handle_segments_without_index_metadata() {

        // given
        SentenceWindowTextSegmentTransformer transformer = SentenceWindowTextSegmentTransformer.builder()
                .segmentsBefore(1)
                .segmentsAfter(1)
                .build();

        Metadata docMeta = Metadata.from("file_name", "doc.txt");
        TextSegment s0 = TextSegment.from("S0.", docMeta.copy());
        TextSegment s1 = TextSegment.from("S1.", docMeta.copy());

        // when
        List<TextSegment> result = transformer.transformAll(List.of(s0, s1));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("S0.\n\nS1.");
        assertThat(result.get(1).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("S0.\n\nS1.");
    }

    @Test
    void should_work_with_single_segment_in_transformAll() {

        // given
        SentenceWindowTextSegmentTransformer transformer = SentenceWindowTextSegmentTransformer.builder()
                .segmentsBefore(1)
                .segmentsAfter(1)
                .build();

        TextSegment segment = TextSegment.from("Only one.", Metadata.from("file_name", "doc.txt"));

        // when
        List<TextSegment> result = transformer.transformAll(List.of(segment));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).text()).isEqualTo("Only one.");
        assertThat(result.get(0).metadata().getString(SURROUNDING_CONTEXT_KEY)).isEqualTo("Only one.");
    }
}
