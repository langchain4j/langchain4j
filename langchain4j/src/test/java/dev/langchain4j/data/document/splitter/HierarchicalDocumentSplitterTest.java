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

    @Test
    void overlapFrom_should_return_empty_when_max_overlap_is_zero() {
        ExampleImpl splitter = new ExampleImpl(1000, 0);

        assertThat(splitter.overlapFrom("some text here")).isEmpty();
    }

    @Test
    void overlapFrom_should_fall_back_to_character_level_for_text_without_sentence_delimiters() {
        // Text without any sentence delimiters (e.g. CJK written without punctuation) cannot be
        // broken by the sentence splitter, so the sentence-based overlap is empty. The splitter must
        // fall back to a character-level overlap taken from the end of the segment, otherwise
        // maxOverlapSizeInChars is silently ignored (issue #3345).
        ExampleImpl splitter = new ExampleImpl(1000, 4);
        String segment = "语言模型深度学习自然语言处理";

        String overlap = splitter.overlapFrom(segment);

        assertThat(overlap).isNotEmpty();
        assertThat(segment).endsWith(overlap);
        assertThat(overlap.length()).isLessThanOrEqualTo(4);
    }

    @Test
    void overlapFrom_should_not_fall_back_when_sentence_boundaries_are_present() {
        ExampleImpl splitter = new ExampleImpl(1000, 10);

        assertThat(splitter.overlapFrom("Short. This sentence is too long.")).isEmpty();
    }

    @Test
    void split_should_overlap_consecutive_cjk_segments() {
        // End-to-end: a long CJK document split by characters must produce overlapping segments.
        // Before the fix, consecutive segments did not overlap at all because overlapFrom returned
        // an empty string for text without sentence delimiters.
        DocumentSplitter splitter = new DocumentByCharacterSplitter(20, 5);
        String cjk = "语言模型深度学习自然语言处理人工智能大模型".repeat(10);

        List<TextSegment> segments = splitter.split(Document.from(cjk));

        assertThat(segments.size()).isGreaterThan(1);
        for (int i = 1; i < segments.size(); i++) {
            String previous = segments.get(i - 1).text();
            String current = segments.get(i).text();
            assertThat(current).startsWith(previous.substring(previous.length() - 5));
        }
    }
}
