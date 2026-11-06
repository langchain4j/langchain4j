package dev.langchain4j.store.memory.chat.infinispan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.infinispan.testcontainers.InfinispanContainer.IMAGE_BASENAME;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.util.Version;
import org.infinispan.testcontainers.InfinispanContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InfinispanChatMemoryStoreIT {

    static InfinispanContainer infinispan = new InfinispanContainer(IMAGE_BASENAME + ":" + Version.getVersion());
    InfinispanChatMemoryStore chatMemoryStore;

    @BeforeAll
    static void beforeAll() {
        infinispan.start();
    }

    @AfterAll
    static void afterAll() {
        infinispan.stop();
    }

    @BeforeEach
    void setUp() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.uri(infinispan.getConnectionURI());
        chatMemoryStore = InfinispanChatMemoryStore.builder()
                .infinispanConfigBuilder(builder)
                .build();
    }

    @Test
    void should_get_empty_messages_for_unknown_memory_id() {
        List<ChatMessage> messages = chatMemoryStore.getMessages("unknown");
        assertThat(messages).isEmpty();
    }

    @Test
    void should_store_and_retrieve_simple_messages() {
        String memoryId = "simple-messages";
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a helpful assistant"),
                UserMessage.from("Hello"),
                AiMessage.from("Hi there!"));

        chatMemoryStore.updateMessages(memoryId, messages);

        List<ChatMessage> retrieved = chatMemoryStore.getMessages(memoryId);
        assertThat(retrieved).hasSize(3);

        assertThat(retrieved.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) retrieved.get(0)).text()).isEqualTo("You are a helpful assistant");

        assertThat(retrieved.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) retrieved.get(1)).singleText()).isEqualTo("Hello");

        assertThat(retrieved.get(2)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) retrieved.get(2)).text()).isEqualTo("Hi there!");
    }

    @Test
    void should_store_user_message_with_name() {
        String memoryId = "user-name";
        chatMemoryStore.updateMessages(memoryId, List.of(UserMessage.from("John", "Hello")));

        List<ChatMessage> retrieved = chatMemoryStore.getMessages(memoryId);
        assertThat(retrieved).hasSize(1);
        UserMessage userMessage = (UserMessage) retrieved.get(0);
        assertThat(userMessage.name()).isEqualTo("John");
        assertThat(userMessage.singleText()).isEqualTo("Hello");
    }

    @Test
    void should_store_ai_message_with_tool_execution_requests() {
        String memoryId = "tool-requests";
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call_123")
                .name("get_weather")
                .arguments("{\"city\":\"Paris\"}")
                .build();
        AiMessage aiMessage = AiMessage.from(request);

        chatMemoryStore.updateMessages(memoryId, List.of(aiMessage));

        List<ChatMessage> retrieved = chatMemoryStore.getMessages(memoryId);
        assertThat(retrieved).hasSize(1);
        assertThat(retrieved.get(0)).isInstanceOf(AiMessage.class);
        AiMessage retrievedAi = (AiMessage) retrieved.get(0);
        assertThat(retrievedAi.hasToolExecutionRequests()).isTrue();
        assertThat(retrievedAi.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest retrievedRequest =
                retrievedAi.toolExecutionRequests().get(0);
        assertThat(retrievedRequest.id()).isEqualTo("call_123");
        assertThat(retrievedRequest.name()).isEqualTo("get_weather");
        assertThat(retrievedRequest.arguments()).isEqualTo("{\"city\":\"Paris\"}");
    }

    @Test
    void should_store_tool_execution_result() {
        String memoryId = "tool-result";
        ToolExecutionResultMessage toolResult =
                ToolExecutionResultMessage.from("call_123", "get_weather", "Sunny, 25°C");

        chatMemoryStore.updateMessages(memoryId, List.of(toolResult));

        List<ChatMessage> retrieved = chatMemoryStore.getMessages(memoryId);
        assertThat(retrieved).hasSize(1);
        assertThat(retrieved.get(0)).isInstanceOf(ToolExecutionResultMessage.class);
        ToolExecutionResultMessage retrievedResult = (ToolExecutionResultMessage) retrieved.get(0);
        assertThat(retrievedResult.id()).isEqualTo("call_123");
        assertThat(retrievedResult.toolName()).isEqualTo("get_weather");
        assertThat(retrievedResult.text()).isEqualTo("Sunny, 25°C");
    }

    @Test
    void should_store_ai_message_with_thinking() {
        String memoryId = "thinking";
        AiMessage aiMessage = AiMessage.builder()
                .text("The answer is 42")
                .thinking("Let me think about this...")
                .build();

        chatMemoryStore.updateMessages(memoryId, List.of(aiMessage));

        List<ChatMessage> retrieved = chatMemoryStore.getMessages(memoryId);
        assertThat(retrieved).hasSize(1);
        assertThat(retrieved.get(0)).isInstanceOf(AiMessage.class);
        AiMessage retrievedAi = (AiMessage) retrieved.get(0);
        assertThat(retrievedAi.text()).isEqualTo("The answer is 42");
        assertThat(retrievedAi.thinking()).isEqualTo("Let me think about this...");
    }

    @Test
    void should_store_full_conversation_with_tool_use() {
        String memoryId = "full-conversation";
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call_456")
                .name("search")
                .arguments("{\"query\":\"langchain4j\"}")
                .build();

        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a helpful assistant with tools"),
                UserMessage.from("Search for langchain4j"),
                AiMessage.from(request),
                ToolExecutionResultMessage.from("call_456", "search", "LangChain4j is a Java framework"));

        chatMemoryStore.updateMessages(memoryId, messages);

        List<ChatMessage> retrieved = chatMemoryStore.getMessages(memoryId);
        assertThat(retrieved).hasSize(4);
        assertThat(retrieved.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(retrieved.get(1)).isInstanceOf(UserMessage.class);
        assertThat(retrieved.get(2)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) retrieved.get(2)).hasToolExecutionRequests()).isTrue();
        assertThat(retrieved.get(3)).isInstanceOf(ToolExecutionResultMessage.class);
    }

    @Test
    void should_delete_messages() {
        String memoryId = "to-delete";
        chatMemoryStore.updateMessages(memoryId, List.of(UserMessage.from("Hello")));

        chatMemoryStore.deleteMessages(memoryId);

        List<ChatMessage> messages = chatMemoryStore.getMessages(memoryId);
        assertThat(messages).isEmpty();
    }

    @Test
    void should_handle_multiple_memory_ids() {
        chatMemoryStore.updateMessages("memory-1", List.of(UserMessage.from("First conversation")));
        chatMemoryStore.updateMessages("memory-2", List.of(UserMessage.from("Second conversation")));

        assertThat(chatMemoryStore.getMessages("memory-1")).hasSize(1);
        assertThat(((UserMessage) chatMemoryStore.getMessages("memory-1").get(0)).singleText())
                .isEqualTo("First conversation");

        assertThat(chatMemoryStore.getMessages("memory-2")).hasSize(1);
        assertThat(((UserMessage) chatMemoryStore.getMessages("memory-2").get(0)).singleText())
                .isEqualTo("Second conversation");
    }

    @Test
    void should_overwrite_messages_on_update() {
        String memoryId = "overwrite-test";
        chatMemoryStore.updateMessages(memoryId, List.of(UserMessage.from("Original")));

        chatMemoryStore.updateMessages(memoryId, List.of(UserMessage.from("Updated"), AiMessage.from("Response")));

        List<ChatMessage> messages = chatMemoryStore.getMessages(memoryId);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo("Updated");
    }
}
