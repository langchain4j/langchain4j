package dev.langchain4j.model.embedding;

import dev.langchain4j.data.segment.TextSegment;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;


class TokenCountEstimatorTest implements WithAssertions {
    public static class WhitespaceSplitTokenCountEstimator implements TokenCountEstimator {
        @Override
        public int estimateTokenCount(String text) {
            return text.split("\\s+").length;
        }
    }

    @Test
    public void test() {
        TokenCountEstimator estimator = new WhitespaceSplitTokenCountEstimator();

        assertThat(estimator.estimateTokenCount("foo bar, baz")).isEqualTo(3);

        assertThat(estimator.estimateTokenCount(TextSegment.textSegment("foo bar, baz"))).isEqualTo(3);

        {
            List<TextSegment> segments = new ArrayList<>();
            segments.add(TextSegment.textSegment("Hello, world!"));
            segments.add(TextSegment.textSegment("How are you?"));

            assertThat(estimator.estimateTokenCount(segments)).isEqualTo(5);
        }
    }
}