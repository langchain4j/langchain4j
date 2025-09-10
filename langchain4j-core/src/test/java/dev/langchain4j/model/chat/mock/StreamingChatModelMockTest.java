package dev.langchain4j.model.chat.mock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Test;

public class StreamingChatModelMockTest {

    @Test
    void test_toTokens() {

        AiMessage aiMessage = AiMessage.from("Hello");

        List<String> tokens = StreamingChatModelMock.toTokens(aiMessage);

        assertThat(tokens).containsExactly("H", "e", "l", "l", "o");
    }
}
