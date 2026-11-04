package dev.langchain4j.store.memory.chat.infinispan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.infinispan.testcontainers.InfinispanContainer.IMAGE_BASENAME;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.api.query.Query;
import org.infinispan.commons.util.Version;
import org.infinispan.testcontainers.InfinispanContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InfinispanChatMemoryStoreIndexedIT {

    static final String CACHE_NAME = "chat-memory-indexed";

    static final String INDEXED_CACHE_CONFIG = "<distributed-cache name=\"" + CACHE_NAME + "\">\n"
            + "  <indexing storage=\"local-heap\">\n"
            + "    <indexed-entities>\n"
            + "      <indexed-entity>dev.langchain4j.LangChainChatMemoryItem</indexed-entity>\n"
            + "    </indexed-entities>\n"
            + "  </indexing>\n"
            + "</distributed-cache>";

    static InfinispanContainer infinispan = new InfinispanContainer(IMAGE_BASENAME + ":" + Version.getVersion());
    static InfinispanChatMemoryStore chatMemoryStore;
    static RemoteCache<String, LangChainChatMemoryItem> remoteCache;

    @BeforeAll
    static void beforeAll() {
        infinispan.start();

        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.uri(infinispan.getConnectionURI());
        chatMemoryStore = InfinispanChatMemoryStore.builder()
                .infinispanConfigBuilder(builder)
                .cacheName(CACHE_NAME)
                .cacheConfig(INDEXED_CACHE_CONFIG)
                .build();

        remoteCache = chatMemoryStore.getRemoteCache();
    }

    @AfterAll
    static void afterAll() {
        infinispan.stop();
    }

    @BeforeEach
    void clearCache() {
        remoteCache.clear();
    }

    @Test
    void should_full_text_search_on_message_text() {
        chatMemoryStore.updateMessages(
                "conv-1",
                List.of(
                        SystemMessage.from("You are a vintage store assistant"),
                        UserMessage.from("Do you have any rare books about astronomy?"),
                        AiMessage.from("Yes, we have several rare astronomy books in our collection.")));

        chatMemoryStore.updateMessages(
                "conv-2",
                List.of(
                        UserMessage.from("What music albums do you recommend?"),
                        AiMessage.from("We have a great selection of vintage jazz and rock albums.")));

        Query<LangChainChatMemoryItem> query =
                remoteCache.query("FROM dev.langchain4j.LangChainChatMemoryItem m WHERE m.messages.text : 'astronomy'");
        List<LangChainChatMemoryItem> results = query.execute().list();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).memoryId()).isEqualTo("conv-1");
    }

    @Test
    void should_full_text_search_with_fuzzy_matching() {
        chatMemoryStore.updateMessages(
                "conv-fuzzy",
                List.of(
                        UserMessage.from("I want to buy a vintage book"),
                        AiMessage.from("We have many vintage books available.")));

        Query<LangChainChatMemoryItem> query =
                remoteCache.query("FROM dev.langchain4j.LangChainChatMemoryItem m WHERE m.messages.text : 'vintge'~2");
        List<LangChainChatMemoryItem> results = query.execute().list();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).memoryId()).isEqualTo("conv-fuzzy");
    }

    @Test
    void should_search_by_memory_id() {
        chatMemoryStore.updateMessages("lookup-id", List.of(UserMessage.from("Hello")));
        chatMemoryStore.updateMessages("other-id", List.of(UserMessage.from("World")));

        Query<LangChainChatMemoryItem> query =
                remoteCache.query("FROM dev.langchain4j.LangChainChatMemoryItem WHERE memoryId = :id");
        query.setParameter("id", "lookup-id");
        List<LangChainChatMemoryItem> results = query.execute().list();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).memoryId()).isEqualTo("lookup-id");
    }

    @Test
    void should_full_text_search_on_thinking_field() {
        chatMemoryStore.updateMessages(
                "conv-thinking",
                List.of(
                        UserMessage.from("What is the meaning of life?"),
                        AiMessage.builder()
                                .text("The answer is 42")
                                .thinking("The user is asking a philosophical question referencing Douglas Adams")
                                .build()));

        Query<LangChainChatMemoryItem> query = remoteCache.query(
                "FROM dev.langchain4j.LangChainChatMemoryItem m WHERE m.messages.thinking : 'philosophical'");
        List<LangChainChatMemoryItem> results = query.execute().list();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).memoryId()).isEqualTo("conv-thinking");
    }

    @Test
    void should_search_conversations_by_tool_name_in_result() {
        ToolExecutionRequest weatherRequest = ToolExecutionRequest.builder()
                .id("call_1")
                .name("get_weather")
                .arguments("{\"city\":\"Paris\"}")
                .build();

        chatMemoryStore.updateMessages(
                "conv-weather",
                List.of(
                        UserMessage.from("What is the weather in Paris?"),
                        AiMessage.from(weatherRequest),
                        ToolExecutionResultMessage.from("call_1", "get_weather", "Sunny, 25C")));

        chatMemoryStore.updateMessages("conv-no-tool", List.of(UserMessage.from("Hello"), AiMessage.from("Hi there!")));

        Query<LangChainChatMemoryItem> query = remoteCache.query(
                "FROM dev.langchain4j.LangChainChatMemoryItem m WHERE m.messages.toolName = 'get_weather'");
        List<LangChainChatMemoryItem> results = query.execute().list();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).memoryId()).isEqualTo("conv-weather");
    }

    @Test
    void should_project_memory_ids_from_full_text_search() {
        chatMemoryStore.updateMessages(
                "proj-1", List.of(UserMessage.from("Tell me about Infinispan distributed caching")));

        chatMemoryStore.updateMessages("proj-2", List.of(UserMessage.from("How does Redis work?")));

        Query<Object[]> query = remoteCache.query("SELECT m.memoryId FROM dev.langchain4j.LangChainChatMemoryItem m "
                + "WHERE m.messages.text : 'Infinispan'");
        List<Object[]> results = query.execute().list();

        assertThat(results).hasSize(1);
        assertThat(results.get(0)[0]).isEqualTo("proj-1");
    }
}
