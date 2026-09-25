package dev.langchain4j.mcp;

import static dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpToolProviderTest {

    McpClient mcpClient = mock(McpClient.class);

    @BeforeEach
    void setUp() {
        when(mcpClient.listTools())
                .thenReturn(List.of(
                        ToolSpecification.builder()
                                .name("tool_1")
                                .metadata(Map.of("one", 1))
                                .build(),
                        ToolSpecification.builder()
                                .name("tool_2")
                                .metadata(Map.of("two", 2))
                                .build()));
    }

    @Test
    void should_configure_always_visible_tools() {

        // given
        McpToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .alwaysVisibleToolNames("tool_1")
                .build();

        // when
        ToolProviderResult toolProviderResult = toolProvider.provideTools(toolProviderRequest());

        // then
        assertThat(toolProviderResult.tools())
                .hasSize(2)
                .containsKeys(
                        ToolSpecification.builder()
                                .name("tool_1")
                                .metadata(Map.of("one", 1, "searchBehavior", ALWAYS_VISIBLE))
                                .build(),
                        ToolSpecification.builder()
                                .name("tool_2")
                                .metadata(Map.of("two", 2))
                                .build());
    }

    @Test
    void should_configure_always_visible_tools_with_tool_name_mapper() {

        // given
        McpToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .alwaysVisibleToolNames("my_tool_1")
                .toolNameMapper((client, spec) -> "my_" + spec.name())
                .build();

        // when
        ToolProviderResult toolProviderResult = toolProvider.provideTools(toolProviderRequest());

        // then
        assertThat(toolProviderResult.tools())
                .hasSize(2)
                .containsKeys(
                        ToolSpecification.builder()
                                .name("my_tool_1")
                                .metadata(Map.of("one", 1, "searchBehavior", ALWAYS_VISIBLE))
                                .build(),
                        ToolSpecification.builder()
                                .name("my_tool_2")
                                .metadata(Map.of("two", 2))
                                .build());
    }

    @Test
    void should_be_static_by_default() {
        McpToolProvider toolProvider =
                McpToolProvider.builder().mcpClients(mcpClient).build();

        assertThat(toolProvider.isDynamic()).isFalse();
    }

    @Test
    void should_reapply_filter_between_tool_execution_rounds_when_dynamic() {
        // given
        ToolSpecification searchTools =
                ToolSpecification.builder().name("searchTools").build();
        ToolSpecification getWeather =
                ToolSpecification.builder().name("getWeather").build();
        when(mcpClient.listTools()).thenReturn(List.of(searchTools, getWeather));

        AtomicBoolean weatherToolEnabled = new AtomicBoolean();
        when(mcpClient.executeTool(any(ToolExecutionRequest.class), any(InvocationContext.class)))
                .thenAnswer(invocation -> {
                    weatherToolEnabled.set(true);
                    return ToolExecutionResult.builder()
                            .resultText("getWeather enabled")
                            .build();
                });

        AtomicInteger chatCallCount = new AtomicInteger();
        ChatModel chatModel = ChatModelMock.thatResponds(request -> {
            if (chatCallCount.getAndIncrement() == 0) {
                assertThat(request.toolSpecifications())
                        .extracting(ToolSpecification::name)
                        .containsExactly("searchTools");
                return AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("searchTools")
                        .arguments("{}")
                        .build());
            }
            assertThat(request.toolSpecifications())
                    .extracting(ToolSpecification::name)
                    .containsExactly("searchTools", "getWeather");
            return AiMessage.from("The weather tool is now available");
        });

        McpToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .filter((client, tool) -> tool.name().equals("searchTools") || weatherToolEnabled.get())
                .dynamic(true)
                .build();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .toolProvider(toolProvider)
                .build();

        // when
        String answer = assistant.chat("Find a tool that can check the weather");

        // then
        assertThat(answer).isEqualTo("The weather tool is now available");
        assertThat(toolProvider.isDynamic()).isTrue();
        verify(mcpClient, times(2)).listTools();
    }

    private static ToolProviderRequest toolProviderRequest() {
        return ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(UserMessage.from("does not matter"))
                .build();
    }

    private interface Assistant {
        String chat(String userMessage);
    }
}
