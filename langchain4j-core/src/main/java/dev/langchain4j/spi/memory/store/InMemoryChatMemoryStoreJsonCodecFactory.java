package dev.langchain4j.spi.memory.store;

import dev.langchain4j.Internal;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStoreJsonCodec;

@Internal
public interface InMemoryChatMemoryStoreJsonCodecFactory {
    InMemoryChatMemoryStoreJsonCodec create();
}
