package dev.langchain4j.store.memory.chat.infinispan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.infinispan.testcontainers.InfinispanContainer.IMAGE_BASENAME;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import java.util.Map;
import org.infinispan.client.hotrod.RemoteCacheManager;
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
        assertThat(chatMemoryStore.getMessages("unknown")).isEmpty();
    }

    @Test
    void should_store_and_retrieve_simple_messages() {
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a helpful assistant"),
                UserMessage.from("Hello"),
                AiMessage.from("Hi there!"));

        chatMemoryStore.updateMessages("simple", messages);

        List<ChatMessage> retrieved = chatMemoryStore.getMessages("simple");
        assertThat(retrieved).hasSize(3);
        assertThat(((SystemMessage) retrieved.get(0)).text()).isEqualTo("You are a helpful assistant");
        assertThat(((UserMessage) retrieved.get(1)).singleText()).isEqualTo("Hello");
        assertThat(((AiMessage) retrieved.get(2)).text()).isEqualTo("Hi there!");
    }

    @Test
    void should_store_user_message_with_name() {
        chatMemoryStore.updateMessages("user-name", List.of(UserMessage.from("John", "Hello")));

        UserMessage msg = (UserMessage) chatMemoryStore.getMessages("user-name").get(0);
        assertThat(msg.name()).isEqualTo("John");
        assertThat(msg.singleText()).isEqualTo("Hello");
    }

    @Test
    void should_store_multimodal_user_message() {
        UserMessage message = UserMessage.from(
                TextContent.from("What is in this image?"), ImageContent.from("https://example.com/image.png"));

        chatMemoryStore.updateMessages("multimodal", List.of(message));

        UserMessage msg =
                (UserMessage) chatMemoryStore.getMessages("multimodal").get(0);
        assertThat(msg.contents()).hasSize(2);
        assertThat(((TextContent) msg.contents().get(0)).text()).isEqualTo("What is in this image?");
        assertThat(((ImageContent) msg.contents().get(1)).image().url().toString())
                .isEqualTo("https://example.com/image.png");
    }

    @Test
    void should_store_ai_message_with_tool_execution_requests() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call_123")
                .name("get_weather")
                .arguments("{\"city\":\"Paris\"}")
                .build();

        chatMemoryStore.updateMessages("tool-req", List.of(AiMessage.from(request)));

        AiMessage msg = (AiMessage) chatMemoryStore.getMessages("tool-req").get(0);
        assertThat(msg.toolExecutionRequests()).hasSize(1);
        assertThat(msg.toolExecutionRequests().get(0).id()).isEqualTo("call_123");
        assertThat(msg.toolExecutionRequests().get(0).name()).isEqualTo("get_weather");
    }

    @Test
    void should_store_tool_execution_result_with_image() {
        ToolExecutionResultMessage toolResult = ToolExecutionResultMessage.builder()
                .id("call_img")
                .toolName("screenshot")
                .contents(List.of(
                        TextContent.from("Screenshot captured"),
                        ImageContent.from("https://example.com/screenshot.png")))
                .build();

        chatMemoryStore.updateMessages("tool-img", List.of(toolResult));

        ToolExecutionResultMessage msg = (ToolExecutionResultMessage)
                chatMemoryStore.getMessages("tool-img").get(0);
        assertThat(msg.contents()).hasSize(2);
    }

    @Test
    void should_store_ai_message_with_thinking_and_attributes() {
        AiMessage aiMessage = AiMessage.builder()
                .text("The answer is 42")
                .thinking("Let me think...")
                .attributes(Map.of("thinking_signature", "sig123", "redacted_thinking", List.of("b1", "b2")))
                .build();

        chatMemoryStore.updateMessages("ai-full", List.of(aiMessage));

        AiMessage msg = (AiMessage) chatMemoryStore.getMessages("ai-full").get(0);
        assertThat(msg.text()).isEqualTo("The answer is 42");
        assertThat(msg.thinking()).isEqualTo("Let me think...");
        assertThat(msg.attributes()).containsEntry("thinking_signature", "sig123");
    }

    @Test
    void should_store_custom_message() {
        chatMemoryStore.updateMessages("custom", List.of(CustomMessage.from(Map.of("key1", "value1"))));

        CustomMessage msg =
                (CustomMessage) chatMemoryStore.getMessages("custom").get(0);
        assertThat(msg.attributes()).containsEntry("key1", "value1");
    }

    @Test
    void should_delete_messages() {
        chatMemoryStore.updateMessages("to-delete", List.of(UserMessage.from("Hello")));
        chatMemoryStore.deleteMessages("to-delete");
        assertThat(chatMemoryStore.getMessages("to-delete")).isEmpty();
    }

    @Test
    void should_overwrite_messages_on_update() {
        chatMemoryStore.updateMessages("overwrite", List.of(UserMessage.from("Original")));
        chatMemoryStore.updateMessages("overwrite", List.of(UserMessage.from("Updated")));
        assertThat(((UserMessage) chatMemoryStore.getMessages("overwrite").get(0)).singleText())
                .isEqualTo("Updated");
    }

    @Test
    void should_work_with_remote_cache_manager() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.uri(infinispan.getConnectionURI());
        RemoteCacheManager remoteCacheManager = new RemoteCacheManager(builder.build());

        InfinispanChatMemoryStore store = InfinispanChatMemoryStore.builder()
                .remoteCacheManager(remoteCacheManager)
                .cacheName("rcm-test")
                .build();

        store.updateMessages("rcm-1", List.of(UserMessage.from("Hello from RCM")));
        assertThat(((UserMessage) store.getMessages("rcm-1").get(0)).singleText())
                .isEqualTo("Hello from RCM");
    }

    @Test
    void should_work_with_custom_cache_config() {
        String customConfig = "<distributed-cache name=\"custom-chat-memory\" statistics=\"true\">\n"
                + "<encoding>\n"
                + "<key media-type=\"application/x-protostream\"/>\n"
                + "<value media-type=\"application/x-protostream\"/>\n"
                + "</encoding>\n"
                + "</distributed-cache>";

        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.uri(infinispan.getConnectionURI());
        InfinispanChatMemoryStore store = InfinispanChatMemoryStore.builder()
                .infinispanConfigBuilder(builder)
                .cacheName("custom-chat-memory")
                .cacheConfig(customConfig)
                .build();

        store.updateMessages("custom-1", List.of(UserMessage.from("Hello custom cache")));
        assertThat(((UserMessage) store.getMessages("custom-1").get(0)).singleText())
                .isEqualTo("Hello custom cache");
    }

    @Test
    void should_not_create_cache_when_disabled() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.uri(infinispan.getConnectionURI());
        RemoteCacheManager remoteCacheManager = new RemoteCacheManager(builder.build());

        assertThatThrownBy(() -> InfinispanChatMemoryStore.builder()
                        .remoteCacheManager(remoteCacheManager)
                        .cacheName("non-existent-cache")
                        .createCache(false)
                        .build())
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'non-existent-cache' does not exist");

        assertThat(remoteCacheManager.getCache("non-existent-cache")).isNull();
    }

    @Test
    void should_fail_when_cache_does_not_exist_and_creation_is_disabled_with_configuration_builder() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.uri(infinispan.getConnectionURI());

        assertThatThrownBy(() -> InfinispanChatMemoryStore.builder()
                        .infinispanConfigBuilder(builder)
                        .cacheName("non-existent-cache-2")
                        .createCache(false)
                        .build())
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'non-existent-cache-2' does not exist");
    }

    @Test
    void should_create_cache_by_default() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.uri(infinispan.getConnectionURI());
        RemoteCacheManager remoteCacheManager = new RemoteCacheManager(builder.build());

        InfinispanChatMemoryStore.builder()
                .remoteCacheManager(remoteCacheManager)
                .cacheName("cache-created")
                .build();

        assertThat(remoteCacheManager.getCache("cache-created")).isNotNull();
    }
}
