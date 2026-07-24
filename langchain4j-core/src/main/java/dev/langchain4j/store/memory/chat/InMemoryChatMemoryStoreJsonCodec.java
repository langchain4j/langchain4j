package dev.langchain4j.store.memory.chat;

import java.nio.charset.Charset;

public interface InMemoryChatMemoryStoreJsonCodec {
    InMemoryChatMemoryStore fromJson(String json);

    String toJson(InMemoryChatMemoryStore store);
}
