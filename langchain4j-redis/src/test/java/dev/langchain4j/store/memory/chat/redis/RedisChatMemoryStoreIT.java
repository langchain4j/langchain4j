package dev.langchain4j.store.memory.chat.redis;

import com.redis.testcontainers.RedisContainer;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.redis.testcontainers.RedisStackContainer.DEFAULT_IMAGE_NAME;
import static com.redis.testcontainers.RedisStackContainer.DEFAULT_TAG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisChatMemoryStoreIT {

    static RedisContainer redis = new RedisContainer(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));

    private final String userId = "someUserId";
    private RedisChatMemoryStore memoryStore;

    @BeforeAll
    static void beforeAll() {
        redis.start();
    }

    @AfterAll
    static void afterAll() {
        redis.stop();
    }

    @BeforeEach
    void setUp() {
        this.memoryStore = RedisChatMemoryStore.builder()
                .port(redis.getFirstMappedPort())
                .host(redis.getHost())
                .build();
        memoryStore.deleteMessages(userId);
        List<ChatMessage> messages = memoryStore.getMessages(userId);
        assertThat(messages).isEmpty();
    }

    @Test
    void should_set_messages_into_redis() {
        // given
        List<ChatMessage> messages = memoryStore.getMessages(userId);
        assertThat(messages).isEmpty();

        // when
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        List<Content> userMsgContents = new ArrayList<>();
        userMsgContents.add(new ImageContent("someCatImageUrl"));
        chatMessages.add(new UserMessage("What do you see in this image?", userMsgContents));
        memoryStore.updateMessages(userId, chatMessages);

        // then
        messages = memoryStore.getMessages(userId);
        assertThat(messages).hasSize(2);
    }

    @Test
    void should_delete_messages_from_redis() {
        // given
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        memoryStore.updateMessages(userId, chatMessages);
        List<ChatMessage> messages = memoryStore.getMessages(userId);
        assertThat(messages).hasSize(1);

        // when
        memoryStore.deleteMessages(userId);

        // then
        messages = memoryStore.getMessages(userId);
        assertThat(messages).isEmpty();
    }

    @Test
    void getMessages_memoryId_null() {
        assertThatThrownBy(() -> memoryStore.getMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void getMessages_memoryId_empty() {
        assertThatThrownBy(() -> memoryStore.getMessages("   "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void updateMessages_messages_null() {
        assertThatThrownBy(() -> memoryStore.updateMessages(userId, null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void updateMessages_messages_empty() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        assertThatThrownBy(() -> memoryStore.updateMessages(userId, chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void updateMessages_memoryId_null() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        assertThatThrownBy(() -> memoryStore.updateMessages(null, chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void updateMessages_memoryId_empty() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        assertThatThrownBy(() -> memoryStore.updateMessages("   ", chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void deleteMessages_memoryId_null() {
        assertThatThrownBy(() -> memoryStore.deleteMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void deleteMessages_memoryId_empty() {
        assertThatThrownBy(() -> memoryStore.deleteMessages("   "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void constructor_port_null() {
        assertThatThrownBy(() -> RedisChatMemoryStore.builder()
                .port(null)
                .host(redis.getHost())
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("port cannot be null");
    }

    @Test
    void constructor_host_null() {
        assertThatThrownBy(() -> RedisChatMemoryStore.builder()
                .port(redis.getFirstMappedPort())
                .host(null)
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("host cannot be null or blank");
    }

    @Test
    void constructor_host_empty() {
        assertThatThrownBy(() -> RedisChatMemoryStore.builder()
                .port(redis.getFirstMappedPort())
                .host("   ")
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("host cannot be null or blank");
    }

    @Test
    void constructor_user_empty() {
        assertThatThrownBy(() -> RedisChatMemoryStore.builder()
                .port(redis.getFirstMappedPort())
                .host(redis.getHost())
                .user("  ")
                .password("123456")
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("user cannot be null or blank");
    }

    @Test
    void constructor_password_empty() {
        assertThatThrownBy(() -> RedisChatMemoryStore.builder()
                .port(redis.getFirstMappedPort())
                .host(redis.getHost())
                .user("redisUser")
                .password("   ")
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("password cannot be null or blank");
    }

    @Test
    void constructor_password_null() {
        assertThatThrownBy(() -> RedisChatMemoryStore.builder()
                .port(redis.getFirstMappedPort())
                .host(redis.getHost())
                .user("redisUser")
                .password(null)
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("password cannot be null or blank");
    }
}
