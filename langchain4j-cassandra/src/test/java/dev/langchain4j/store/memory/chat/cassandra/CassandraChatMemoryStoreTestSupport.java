package dev.langchain4j.store.memory.chat.cassandra;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class CassandraChatMemoryStoreTestSupport {
    protected final String KEYSPACE = "langchain4j";
    protected static CassandraChatMemoryStore chatMemoryStore;

    @Test
    @Order(1)
    @DisplayName("1. Should create a database")
    void shouldInitializeDatabase() {
        createDatabase();
    }

    @Test
    @Order(2)
    @DisplayName("2. Connection to the database")
    void shouldConnectToDatabase() {
        chatMemoryStore = createChatMemoryStore();
        // Connection to Cassandra is established
        Assertions.assertTrue(chatMemoryStore.getCassandraSession()
                .getMetadata()
                .getKeyspace(KEYSPACE)
                .isPresent());
    }

    @Test
    @Order(3)
    @DisplayName("3. ChatMemoryStore initialization (table)")
    void shouldCreateChatMemoryStore() {
        chatMemoryStore.create();
        // Table exists
        Assertions.assertTrue(chatMemoryStore.getCassandraSession()
                .refreshSchema()
                .getKeyspace(KEYSPACE).get()
                .getTable(CassandraChatMemoryStore.DEFAULT_TABLE_NAME).isPresent());
        chatMemoryStore.clear();
    }

    @Test
    @Order(4)
    @DisplayName("4. Insert items")
    void shouldInsertItems() {
        // When
        String chatSessionId = "chat-" + UUID.randomUUID();
        ChatMemory chatMemory = TokenWindowChatMemory.builder()
                .chatMemoryStore(chatMemoryStore)
                .id(chatSessionId)
                .maxTokens(300, new OpenAiTokenizer(GPT_3_5_TURBO))
                .build();

        // When
        UserMessage userMessage = userMessage("I will ask you a few question about ff4j.");
        chatMemory.add(userMessage);

        AiMessage aiMessage = aiMessage("Sure, go ahead!");
        chatMemory.add(aiMessage);

        // Then
        assertThat(chatMemory.messages()).containsExactly(userMessage, aiMessage);
    }

    abstract void createDatabase();

    abstract CassandraChatMemoryStore createChatMemoryStore();

}
