package dev.langchain4j.memory.chat;

import java.util.function.Consumer;

interface RemovalAwareChatMemory {

    void onChatMemoryRemove(Consumer<Object> remover);
}
