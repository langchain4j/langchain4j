package dev.langchain4j.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ChatMemoryTest {

    @Test
    void should_add_multiple_messages() {

        // given
        ChatMemory chatMemory = spy(new DummyChatMemory());
        List<ChatMessage> messages = List.of(UserMessage.from("1"), AiMessage.from("2"));

        // when
        chatMemory.add(messages);

        // then
        verify(chatMemory).add(messages);
        verify(chatMemory).add(messages.get(0));
        verify(chatMemory).add(messages.get(1));
        verifyNoMoreInteractions(chatMemory);
    }

    @Test
    void should_add_multiple_messages_2() {

        // given
        ChatMemory chatMemory = spy(new DummyChatMemory());

        // when
        chatMemory.add(List.of(UserMessage.from("1"), AiMessage.from("2")));

        // then
        verify(chatMemory).add(List.of(UserMessage.from("1"), AiMessage.from("2")));
        verify(chatMemory).add(UserMessage.from("1"));
        verify(chatMemory).add(AiMessage.from("2"));
        verifyNoMoreInteractions(chatMemory);
    }

    static class DummyChatMemory implements ChatMemory {

        @Override
        public Object id() {
            return null;
        }

        @Override
        public void add(ChatMessage message) {
        }

        @Override
        public List<ChatMessage> messages() {
            return List.of();
        }

        @Override
        public void clear() {
        }
    }
}