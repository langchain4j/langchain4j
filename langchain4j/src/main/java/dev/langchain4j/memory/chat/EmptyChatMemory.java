package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.Collections;
import java.util.List;

/**
 * TODO
 */
public class EmptyChatMemory implements ChatMemory {

    private final Object id;

    private EmptyChatMemory(Builder builder) {
       this.id = builder.id;
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        // no-op
    }

    @Override
    public List<ChatMessage> messages() {
        return Collections.emptyList();
    }

    @Override
    public void clear() {
        // no-op
    }

    public static class Builder {

        private Object id = "default";

        public Builder id(Object id) {
            this.id = id;
            return this;
        }

        public EmptyChatMemory build() { return new EmptyChatMemory(this); }
    }

    public static ChatMemory build() {
        return (new Builder()).build();
    }
}
