package dev.langchain4j.memory.chat;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class TokenWindowChatMemorySetValidationTest {

    private static final TokenCountEstimator ONE_TOKEN_PER_MESSAGE = new TokenCountEstimator() {
        @Override
        public int estimateTokenCountInText(String text) {
            return 1;
        }

        @Override
        public int estimateTokenCountInMessage(ChatMessage message) {
            return 1;
        }

        @Override
        public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
            int count = 0;
            for (ChatMessage ignored : messages) {
                count++;
            }
            return count;
        }
    };

    @Test
    void set_with_empty_list_should_reject_and_keep_existing_messages() {
        ChatMemory chatMemory = TokenWindowChatMemory.builder()
                .maxTokens(10, ONE_TOKEN_PER_MESSAGE)
                .build();
        chatMemory.add(userMessage("keep-me"));

        assertThatThrownBy(() -> chatMemory.set(Collections.emptyList()))
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
