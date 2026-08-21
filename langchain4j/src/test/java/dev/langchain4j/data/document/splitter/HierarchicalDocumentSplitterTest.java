package dev.langchain4j.data.document.splitter;

import static java.util.stream.Collectors.joining;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ExampleTestTokenCountEstimator;
import dev.langchain4j.model.TokenCountEstimator;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class HierarchicalDocumentSplitterTest implements WithAssertions {

    private static final String NBSP = "\u00A0"; // non-breaking space

    public static class ExampleImpl extends HierarchicalDocumentSplitter {
        public ExampleImpl(int maxSegmentSizeInChars, int maxOverlapSizeInChars) {
            super(maxSegmentSizeInChars, maxOverlapSizeInChars);
        }

        public ExampleImpl(
                int maxSegmentSizeInChars, int maxOverlapSizeInChars, HierarchicalDocumentSplitter subSplitter) {
            super(maxSegmentSizeInChars, maxOverlapSizeInChars, subSplitter);
        }

        public ExampleImpl(
                int maxSegmentSizeInTokens, int maxOverlapSizeInTokens, TokenCountEstimator tokenCountEstimator) {
            super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenCountEstimator);
        }

        public ExampleImpl(
                int maxSegmentSizeInTokens,
                int maxOverlapSizeInTokens,
                TokenCountEstimator tokenCountEstimator,
                HierarchicalDocumentSplitter subSplitter) {
            super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenCountEstimator, subSplitter);
        }

        @Override
        protected String[] split(String text) {
            return text.split("\\.");
        }

        @Override
        protected String joinDelimiter() {
            return " ";
        }

        @Override
        protected DocumentSplitter defaultSubSplitter() {
            return null;
        }
    }

    @Test
    void constructor() {
        {
            ExampleImpl splitter = new ExampleImpl(1, 1);
            assertThat(splitter.maxSegmentSize).isEqualTo(1);
            assertThat(splitter.maxOverlapSize).isEqualTo(1);
            assertThat(splitter.tokenCountEstimator).isNull();
            assertThat(splitter.subSplitter).isNull();

            assertThat(splitter.estimateSize("abc def")).isEqualTo(7);
        }
        {
            DocumentByWordSplitter subSplitter = new DocumentByWordSplitter(2, 2);
            ExampleImpl splitter = new ExampleImpl(1, 1, subSplitter);
            assertThat(splitter.maxSegmentSize).isEqualTo(1);
            assertThat(splitter.maxOverlapSize).isEqualTo(1);
            assertThat(splitter.tokenCountEstimator).isNull();
            assertThat(splitter.subSplitter).isSameAs(subSplitter);

            assertThat(splitter.estimateSize("abc def")).isEqualTo(7);
        }
        {
            TokenCountEstimator tokenCountEstimator = new ExampleTestTokenCountEstimator();
            ExampleImpl splitter = new ExampleImpl(1, 1, tokenCountEstimator);
            assertThat(splitter.maxSegmentSize).isEqualTo(1);
            assertThat(splitter.maxOverlapSize).isEqualTo(1);
            assertThat(splitter.tokenCountEstimator).isSameAs(tokenCountEstimator);
            assertThat(splitter.subSplitter).isNull();

            assertThat(splitter.estimateSize("abc def")).isEqualTo(2);
        }
        {
            DocumentByWordSplitter subSplitter = new DocumentByWordSplitter(2, 2);
            TokenCountEstimator tokenCountEstimator = new ExampleTestTokenCountEstimator();
            ExampleImpl splitter = new ExampleImpl(1, 1, tokenCountEstimator, subSplitter);
            assertThat(splitter.maxSegmentSize).isEqualTo(1);
            assertThat(splitter.maxOverlapSize).isEqualTo(1);
            assertThat(splitter.tokenCountEstimator).isSameAs(tokenCountEstimator);
            assertThat(splitter.subSplitter).isSameAs(subSplitter);

            assertThat(splitter.estimateSize("abc def")).isEqualTo(2);
        }
    }

    @Test
    void estimateSize_should_return_zero_for_null_and_blank_text() {
        {
            ExampleImpl splitter = new ExampleImpl(1, 1);

            assertThat(splitter.estimateSize(null)).isEqualTo(0);
            assertThat(splitter.estimateSize("")).isEqualTo(0);
            assertThat(splitter.estimateSize("   ")).isEqualTo(3);
            assertThat(splitter.estimateSize("\t\n")).isEqualTo(2);

            assertThat(splitter.estimateSize("abc def")).isEqualTo(7);
        }

        {
            TokenCountEstimator tokenCountEstimator = new ExampleTestTokenCountEstimator();
            ExampleImpl splitter = new ExampleImpl(1, 1, tokenCountEstimator);

            assertThat(splitter.estimateSize(null)).isEqualTo(0);
            assertThat(splitter.estimateSize("")).isEqualTo(0);
            assertThat(splitter.estimateSize("   ")).isEqualTo(3);
            assertThat(splitter.estimateSize("\t\n")).isEqualTo(2);

            assertThat(splitter.estimateSize("abc def")).isEqualTo(2);
        }
    }

    // A part with no recognizable sentence, such as the run of non-breaking spaces that PDF
    // extraction produces, used to make DocumentBySentenceSplitter return nothing, which either
    // threw IndexOutOfBoundsException or silently dropped the part. See issue #6085.
    @Test
    void split_should_split_a_part_without_any_sentence_into_segments_within_the_limit() {
        int maxSegmentSize = 50;
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(maxSegmentSize, 0);
        String lastParagraph = "A normal paragraph that follows the junk.";
        String text = NBSP.repeat(200) + "\n\n" + lastParagraph;

        List<TextSegment> segments = splitter.split(Document.from(text));

        assertThat(segments)
                .extracting(TextSegment::text)
                .allSatisfy(segmentText -> assertThat(segmentText).hasSizeLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments).extracting(TextSegment::text).last().isEqualTo(lastParagraph);
        for (int i = 0; i < segments.size(); i++) {
            assertThat(segments.get(i).metadata().getInteger("index")).isEqualTo(i);
        }
    }

    @Test
    void split_should_keep_a_part_without_any_sentence_that_sits_between_other_parts() {
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(50, 0);
        String junk = NBSP.repeat(200);
        String text = "A normal paragraph first.\n\n" + junk + "\n\nAnother normal paragraph.";

        List<TextSegment> segments = splitter.split(Document.from(text));

        String joined = segments.stream().map(TextSegment::text).collect(joining());
        assertThat(joined).contains("A normal paragraph first.").contains(junk).contains("Another normal paragraph.");
    }

    @Test
    void split_should_fail_with_a_clear_message_when_sub_splitter_returns_no_segments() {
        ExampleImpl splitter = new ExampleImpl(50, 0, new EmptyResultSplitter());
        String text = "a".repeat(100);

        assertThatThrownBy(() -> splitter.split(Document.from(text)))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageContaining("EmptyResultSplitter")
                .hasMessageContaining("returned no segments")
                .hasMessageContaining("100 characters long");
    }

    static class EmptyResultSplitter extends HierarchicalDocumentSplitter {

        EmptyResultSplitter() {
            super(1, 0);
        }

        @Override
        public List<TextSegment> split(Document document) {
            return List.of();
        }

        @Override
        protected String[] split(String text) {
            return new String[0];
        }

        @Override
        protected String joinDelimiter() {
            return " ";
        }

        @Override
        protected DocumentSplitter defaultSubSplitter() {
            return null;
        }
    }
}
