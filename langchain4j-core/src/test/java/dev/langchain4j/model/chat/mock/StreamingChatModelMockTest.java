package dev.langchain4j.model.chat.mock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

public class StreamingChatModelMockTest {

    @Test
    void test_toTokens() {

        AiMessage aiMessage = AiMessage.from("Hello");

        List<String> tokens = StreamingChatModelMock.toTokens(aiMessage);

        assertThat(tokens).containsExactly("H", "e", "l", "l", "o");
    }

    @Test
    void test_toTokens_with_empty_string() {
        AiMessage aiMessage = AiMessage.from("");

        List<String> tokens = StreamingChatModelMock.toTokens(aiMessage);

        assertThat(tokens).isEmpty();
    }

    @Test
    void test_toTokens_preserves_consecutive_spaces() {
        AiMessage aiMessage = AiMessage.from("a  b");

        List<String> tokens = StreamingChatModelMock.toTokens(aiMessage);

        assertThat(tokens).containsExactly("a", " ", " ", "b");
    }
}
