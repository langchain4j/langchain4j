package dev.langchain4j.memory.chat;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenWindowChatMemorySetValidationTest {

    private static final TokenCountEstimator ONE_TOKEN_PER_MESSAGE = message -> 1;

    @Test
    void set_with_empty_list_should_reject_and_keep_existing_messages() {
        ChatMemory chatMemory = TokenWindowChatMemory.builder()
                .maxTokens(10, ONE_TOKEN_PER_MESSAGE)
                .build();
        chatMemory.add(userMessage("keep-me"));

        assertThatThrownBy(() -> chatMemory.set(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");

        assertThat(chatMemory.messages()).containsExactly(userMessage("keep-me"));
    }

    @Test
    void set_with_null_iterable_should_throw_illegal_argument_exception() {
        ChatMemory chatMemory = TokenWindowChatMemory.builder()
                .maxTokens(10, ONE_TOKEN_PER_MESSAGE)
                .build();
        chatMemory.add(userMessage("keep-me"));

        assertThatThrownBy(() -> chatMemory.set((Iterable<ChatMessage>) null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(chatMemory.messages()).containsExactly(userMessage("keep-me"));
    }
}
