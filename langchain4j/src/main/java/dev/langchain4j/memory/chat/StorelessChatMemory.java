package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.util.List;

public interface StorelessChatMemory extends ChatMemory {
    ChatMemory withStore(ChatMemoryStore store);

    class Impl implements StorelessChatMemory {
        private final ChatMemoryBuilder builder;

        private ChatMemory delegate;

        Impl(ChatMemoryBuilder builder) {
            this.builder = builder;
        }

        @Override
        public ChatMemory withStore(ChatMemoryStore store) {
            if (delegate != null) {
                throw new IllegalStateException("Store already initialized");
            }
            initDelegate(store);
            return this;
        }

        private void initDelegate(ChatMemoryStore store) {
            builder.chatMemoryStore(store);
            delegate = builder.build();
        }

        @Override
        public Object id() {
            return delegate().id();
        }

        @Override
        public void add(final ChatMessage message) {
            delegate().add(message);
        }

        @Override
        public List<ChatMessage> messages() {
            return delegate().messages();
        }

        @Override
        public void clear() {
            delegate().clear();
        }

        private ChatMemory delegate() {
            if (delegate == null) {
                // This should happen only when testing the chat memory directly but not in real world use cases
                initDelegate(new InMemoryChatMemoryStore());
            }
            return delegate;
        }
    }
}
