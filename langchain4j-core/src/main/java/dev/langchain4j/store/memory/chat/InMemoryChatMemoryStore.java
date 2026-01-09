package dev.langchain4j.store.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.spi.memory.store.InMemoryChatMemoryStoreJsonCodecFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Implementation of {@link ChatMemoryStore} that stores state of {@link dev.langchain4j.memory.ChatMemory} (chat messages) in-memory.
 * <p>
 * This storage mechanism is transient and does not persist data across application restarts.
 */
public class InMemoryChatMemoryStore implements ChatMemoryStore {

    private final Map<Object, List<ChatMessage>> messagesByMemoryId = new ConcurrentHashMap<>();

    /**
     * Constructs a new {@link InMemoryChatMemoryStore}.
     */
    public InMemoryChatMemoryStore() {}

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return messagesByMemoryId.computeIfAbsent(memoryId, ignored -> new ArrayList<>());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        messagesByMemoryId.put(memoryId, messages);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        messagesByMemoryId.remove(memoryId);
    }

    public String serializeToJson() {
        return loadCodec().toJson(this);
    }

    public static InMemoryChatMemoryStore fromJson(String json) {
        return loadCodec().fromJson(json);
    }

    private static InMemoryChatMemoryStoreJsonCodec loadCodec() {
        for (InMemoryChatMemoryStoreJsonCodecFactory factory : loadFactories(InMemoryChatMemoryStoreJsonCodecFactory.class)) {
            return factory.create();
        }
        return new JacksonInMemoryChatMemoryStoreJsonCodec();
    }
}
