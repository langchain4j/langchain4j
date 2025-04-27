package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.model.ExampleTestTokenCountEstimator;
import dev.langchain4j.model.TokenCountEstimator;
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

        public ExampleImpl(int maxSegmentSizeInTokens, int maxOverlapSizeInTokens, TokenCountEstimator tokenCountEstimator) {
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
}
