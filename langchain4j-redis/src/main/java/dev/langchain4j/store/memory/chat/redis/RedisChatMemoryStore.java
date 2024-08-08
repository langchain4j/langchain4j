package dev.langchain4j.store.memory.chat.redis;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.Builder;
import redis.clients.jedis.JedisPooled;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class RedisChatMemoryStore implements ChatMemoryStore {

    private final JedisPooled client;

    @Builder
    public RedisChatMemoryStore(String host,
                                Integer port,
                                String user,
                                String password) {
        ensureNotBlank(host, "host");
        ensureNotNull(port, "port");
        if (password != null) {
            ensureNotBlank(password, "password");
            this.client = new JedisPooled(host, port, user, password);
        } else {
            this.client = new JedisPooled(host, port);
        }
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = client.get(toMemoryIdString(memoryId));
        return json == null ? new ArrayList<>() : ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = ChatMessageSerializer.messagesToJson(ensureNotEmpty(messages, "messages"));
        String res = client.set(toMemoryIdString(memoryId), json);
        if (!"OK".equals(res)) {
            throw new RedisChatMemoryStoreException("Set memory error, msg=" + res);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        client.del(toMemoryIdString(memoryId));
    }

    private static String toMemoryIdString(Object memoryId) {
        boolean isNullOrEmpty = memoryId == null || memoryId.toString().trim().isEmpty();
        if (isNullOrEmpty) {
            throw new IllegalArgumentException("memoryId cannot be null or empty");
        }
        return memoryId.toString();
    }
}
