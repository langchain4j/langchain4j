package dev.langchain4j.store.memory.chat.tablestore;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alicloud.openservices.tablestore.SyncClient;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TABLESTORE_ENDPOINT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TABLESTORE_INSTANCE_NAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TABLESTORE_ACCESS_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TABLESTORE_ACCESS_KEY_SECRET", matches = ".+")
class TablestoreChatMemoryStoreIT {

    private final TablestoreChatMemoryStore chatMemoryStore;
    private static final String USER_ID = "someUserId";

    public TablestoreChatMemoryStoreIT() {
        String endpoint = System.getenv("TABLESTORE_ENDPOINT");
        String instanceName = System.getenv("TABLESTORE_INSTANCE_NAME");
        String accessKeyId = System.getenv("TABLESTORE_ACCESS_KEY_ID");
        String accessKeySecret = System.getenv("TABLESTORE_ACCESS_KEY_SECRET");

        chatMemoryStore =
                new TablestoreChatMemoryStore(new SyncClient(endpoint, accessKeyId, accessKeySecret, instanceName));

        chatMemoryStore.init();
    }

    @BeforeEach
    @AfterEach
    void setUp() {
        chatMemoryStore.clear();
        List<ChatMessage> messages = chatMemoryStore.getMessages(USER_ID);
        assertThat(messages).isEmpty();
    }

    @Test
    void should_insert_items() {
        // When
        String chatSessionId = "chat-" + UUID.randomUUID();

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryStore(chatMemoryStore)
                .maxMessages(100)
                .id(chatSessionId)
                .build();

        // When
        UserMessage userMessage = userMessage("How are you?");
        chatMemory.add(userMessage);

        AiMessage aiMessage = aiMessage("I am fine! Thank you!");
        chatMemory.add(aiMessage);

        // Then
        assertThat(chatMemory.messages()).containsExactly(userMessage, aiMessage);
    }

    @Test
    void should_set_messages_into_tablestore() {
        // given
        List<ChatMessage> messages = chatMemoryStore.getMessages(USER_ID);
        assertThat(messages).isEmpty();

        // when
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with LangChain4j"));
        List<Content> userMsgContents = new ArrayList<>();
        userMsgContents.add(new ImageContent("someCatImageUrl"));
        chatMessages.add(new UserMessage("What do you see in this image?", userMsgContents));
        chatMemoryStore.updateMessages(USER_ID, chatMessages);

        // then
        messages = chatMemoryStore.getMessages(USER_ID);
        assertThat(messages).hasSize(2);
    }

    @Test
    void should_delete_messages_from_tablestore() {
        // given
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with LangChain4j"));
        chatMemoryStore.updateMessages(USER_ID, chatMessages);
        List<ChatMessage> messages = chatMemoryStore.getMessages(USER_ID);
        assertThat(messages).hasSize(1);

        // when
        chatMemoryStore.deleteMessages(USER_ID);

        // then
        messages = chatMemoryStore.getMessages(USER_ID);
        assertThat(messages).isEmpty();
    }

    @Test
    void getMessages_memoryId_null() {
        assertThatThrownBy(() -> chatMemoryStore.getMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void getMessages_memoryId_empty() {
        assertThatThrownBy(() -> chatMemoryStore.getMessages("   "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void updateMessages_messages_null() {
        assertThatThrownBy(() -> chatMemoryStore.updateMessages(USER_ID, null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void updateMessages_messages_empty() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        assertThatThrownBy(() -> chatMemoryStore.updateMessages(USER_ID, chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void updateMessages_memoryId_null() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with LangChain4j"));
        assertThatThrownBy(() -> chatMemoryStore.updateMessages(null, chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void updateMessages_memoryId_empty() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with LangChain4j"));
        assertThatThrownBy(() -> chatMemoryStore.updateMessages("   ", chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void deleteMessages_memoryId_null() {
        assertThatThrownBy(() -> chatMemoryStore.deleteMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void deleteMessages_memoryId_empty() {
        assertThatThrownBy(() -> chatMemoryStore.deleteMessages("   "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }
}
