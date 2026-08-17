package dev.langchain4j.data.document.splitter;

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

    // A run of non-breaking spaces is not blank, so it reaches the sub-splitter, but
    // DocumentBySentenceSplitter finds no sentence in it and returns nothing. The hierarchy is meant
    // to bottom out at DocumentByCharacterSplitter, which always produces at least one segment, so
    // such a part still has to be kept rather than dropped. See issue #6085.
    @Test
    void split_should_not_throw_when_sub_splitter_returns_nothing_for_the_first_part() {
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(50, 0);
        String text = NBSP.repeat(200) + "\n\nA normal paragraph that follows the junk.";

        List<TextSegment> segments = splitter.split(Document.from(text));

        assertThat(segments).isNotEmpty();
        assertThat(segments)
                .extracting(TextSegment::text)
                .anySatisfy(
                        segmentText -> assertThat(segmentText).contains("A normal paragraph that follows the junk."));
    }

    @Test
    void split_should_keep_a_part_the_sub_splitter_cannot_split() {
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(50, 0);
        String junk = NBSP.repeat(200);
        String text = "A normal paragraph first.\n\n" + junk + "\n\nAnother normal paragraph.";

        List<TextSegment> segments = splitter.split(Document.from(text));

        assertThat(segments).extracting(TextSegment::text).contains(junk);
        assertThat(String.join("", segments.stream().map(TextSegment::text).toList()))
                .contains("A normal paragraph first.")
                .contains("Another normal paragraph.");
    }

    @Test
    void split_should_index_segments_consecutively_when_sub_splitter_returns_nothing() {
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(50, 0);
        String text = NBSP.repeat(200) + "\n\nA normal paragraph that follows the junk.";

        List<TextSegment> segments = splitter.split(Document.from(text));

        for (int i = 0; i < segments.size(); i++) {
            assertThat(segments.get(i).metadata().getInteger("index")).isEqualTo(i);
        }
    }
}
