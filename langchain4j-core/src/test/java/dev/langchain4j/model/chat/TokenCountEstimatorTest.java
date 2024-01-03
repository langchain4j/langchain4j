package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class TokenCountEstimatorTest implements WithAssertions {
    public static class WhitespaceSplitTokenCountEstimator implements TokenCountEstimator {
        @Override
        public int estimateTokenCount(List<ChatMessage> messages) {
            return messages.stream().mapToInt(message -> message.text().split("\\s+").length).sum();
        }
    }

    @Test
    public void test() {
        TokenCountEstimator estimator = new WhitespaceSplitTokenCountEstimator();

        assertThat(estimator.estimateTokenCount("foo bar, baz")).isEqualTo(3);

        assertThat(estimator.estimateTokenCount(new UserMessage("foo bar, baz"))).isEqualTo(3);

        assertThat(estimator.estimateTokenCount(new Prompt("foo bar, baz"))).isEqualTo(3);

        assertThat(estimator.estimateTokenCount(TextSegment.from("foo bar, baz"))).isEqualTo(3);

        {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("Hello, world!"));
            messages.add(new AiMessage("How are you?"));

            assertThat(estimator.estimateTokenCount(messages)).isEqualTo(5);
        }
    }
}