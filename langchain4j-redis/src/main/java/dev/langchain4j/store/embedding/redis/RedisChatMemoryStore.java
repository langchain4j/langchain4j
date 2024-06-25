package dev.langchain4j.store.embedding.redis;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.Builder;
import redis.clients.jedis.JedisPooled;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.*;

public class RedisChatMemoryStore implements ChatMemoryStore {

    private final JedisPooled client;

    @Builder
    public RedisChatMemoryStore(String host,
                                Integer port,
                                String user,
                                String password) {
        String finalHost = ensureNotBlank(host, "host");
        int finalPort = ensureNotNull(port, "port");
        if (user != null) {
            String finalUser = ensureNotBlank(user, "user");
            String finalPassword = ensureNotBlank(password, "password");
            this.client = new JedisPooled(finalHost, finalPort, finalUser, finalPassword);
        } else {
            this.client = new JedisPooled(finalHost, finalPort);
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
            throw new RedisRequestFailedException("Set memory error, msg=" + res);
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
