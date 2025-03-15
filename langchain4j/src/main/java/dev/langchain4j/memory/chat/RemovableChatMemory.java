package dev.langchain4j.memory.chat;

import java.util.function.Consumer;

interface RemovableChatMemory {

    void onChatMemoryRemove(Consumer<Object> remover);
}
