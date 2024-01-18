package dev.langchain4j.model.language;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

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
        assertThat(estimator.estimateTokenCount(new Prompt("foo bar, baz"))).isEqualTo(3);
        assertThat(estimator.estimateTokenCount(TextSegment.from("foo bar, baz"))).isEqualTo(3);
    }

}