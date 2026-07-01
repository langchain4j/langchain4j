package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class ChatMemoryStoreTest implements WithAssertions {

    /**
     * A store that implements only the blocking SPI methods, leaving the non-blocking
     * counterparts at their default (throwing) implementations.
     */
    static class BlockingOnlyStore implements ChatMemoryStore {

        private final Map<Object, List<ChatMessage>> messages = new ConcurrentHashMap<>();

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return messages.computeIfAbsent(memoryId, ignored -> new ArrayList<>());
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            this.messages.put(memoryId, messages);
        }

        @Override
        public void deleteMessages(Object memoryId) {
            messages.remove(memoryId);
        }
    }

    @Test
    void async_methods_throw_by_default() {
        ChatMemoryStore store = new BlockingOnlyStore();

        assertThatThrownBy(() -> store.getMessagesAsync("foo"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("getMessagesAsync");

        assertThatThrownBy(() -> store.updateMessagesAsync("foo", List.of()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("updateMessagesAsync");

        assertThatThrownBy(() -> store.deleteMessagesAsync("foo"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("deleteMessagesAsync");
    }
}
