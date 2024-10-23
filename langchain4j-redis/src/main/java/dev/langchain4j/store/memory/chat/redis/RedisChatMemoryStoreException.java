package dev.langchain4j.store.memory.chat.redis;

public class RedisChatMemoryStoreException extends RuntimeException {

    public RedisChatMemoryStoreException(String message) {
        super(message);
    }

}
