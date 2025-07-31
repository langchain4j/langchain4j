package dev.langchain4j.store.memory.azure.cosmos.nosql;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class AzureCosmosDBNoSqlMemoryStoreTest {
    protected static final String DATABASE_NAME = "test_database_langchain_java";
    protected static final String CONTAINER_NAME = "test_memory_container";
    protected static AzureCosmosDBNoSqlMemoryStore memoryStore;

    @Test
    void shouldCreateMemoryStore() {
        memoryStore = createMemoryStore();
    }

    @Test
    void shouldInsertMessages() {
        // Given
        String chatSessionId = "chat-" + UUID.randomUUID();

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryStore(memoryStore)
                .maxMessages(100)
                .id(chatSessionId)
                .build();

        // When
        UserMessage userMessage = userMessage("I will ask you a few questions about Azure Cosmos DB.");
        chatMemory.add(userMessage);

        AiMessage aiMessage = aiMessage("Sure, go ahead! I can answer your questions about Azure Cosmos DB.");
        chatMemory.add(aiMessage);

        // Then
        assertThat(chatMemory.messages()).containsExactly(userMessage, aiMessage);
    }

    @Test
    void shouldRetrieveMessages() {
        // Given
        String chatSessionId = "chat-" + UUID.randomUUID();

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryStore(memoryStore)
                .maxMessages(100)
                .id(chatSessionId)
                .build();

        // When
        UserMessage userMessage1 = userMessage("What are the main features of Azure Cosmos DB?");
        chatMemory.add(userMessage1);

        AiMessage aiMessage1 = aiMessage(
                "Azure Cosmos DB is a fully managed NoSQL database service with multiple API compatibility, global distribution, automatic scaling, and comprehensive SLAs.");
        chatMemory.add(aiMessage1);

        UserMessage userMessage2 = userMessage("How does it handle partitioning?");
        chatMemory.add(userMessage2);

        AiMessage aiMessage2 = aiMessage(
                "Azure Cosmos DB uses logical partitions with partition keys to distribute data across physical partitions, enabling horizontal scaling and high throughput.");
        chatMemory.add(aiMessage2);

        // Then
        assertThat(chatMemory.messages()).containsExactly(userMessage1, aiMessage1, userMessage2, aiMessage2);
    }

    @Test
    void shouldUpdateMessages() {
        // Given
        String chatSessionId = "chat-" + UUID.randomUUID();

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryStore(memoryStore)
                .maxMessages(100)
                .id(chatSessionId)
                .build();

        // When - first add messages
        UserMessage userMessage1 = userMessage("What consistency levels does Azure Cosmos DB offer?");
        chatMemory.add(userMessage1);

        AiMessage aiMessage1 = aiMessage(
                "Azure Cosmos DB offers five consistency levels: strong, bounded staleness, session, consistent prefix, and eventual.");
        chatMemory.add(aiMessage1);

        // Then - verify initial messages
        assertThat(chatMemory.messages()).containsExactly(userMessage1, aiMessage1);

        // When - add more messages (which updates the existing ones)
        UserMessage userMessage2 = userMessage("Which one is best for my application?");
        chatMemory.add(userMessage2);

        // Then - verify updated messages
        assertThat(chatMemory.messages()).containsExactly(userMessage1, aiMessage1, userMessage2);
    }

    @Test
    void shouldDeleteMessages() {
        // Given
        String chatSessionId = "chat-" + UUID.randomUUID();

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryStore(memoryStore)
                .maxMessages(100)
                .id(chatSessionId)
                .build();

        // When - add messages
        UserMessage userMessage = userMessage("How much does Azure Cosmos DB cost?");
        chatMemory.add(userMessage);

        AiMessage aiMessage = aiMessage(
                "Azure Cosmos DB pricing is based on provisioned throughput (RU/s) and storage used. It offers serverless and provisioned capacity modes.");
        chatMemory.add(aiMessage);

        // Then - verify messages exist
        assertThat(chatMemory.messages()).containsExactly(userMessage, aiMessage);

        // When - clear memory
        chatMemory.clear();

        // Then - verify messages are deleted
        assertThat(chatMemory.messages()).isEmpty();
    }

    abstract AzureCosmosDBNoSqlMemoryStore createMemoryStore();
}
