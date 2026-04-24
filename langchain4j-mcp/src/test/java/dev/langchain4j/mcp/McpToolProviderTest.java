package dev.langchain4j.mcp;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpToolProviderTest {

    McpClient mcpClient = mock(McpClient.class);

    @BeforeEach
    void setUp() {
        when(mcpClient.listTools()).thenReturn(List.of(
                ToolSpecification.builder()
                        .name("tool_1")
                        .metadata(Map.of("one", 1))
                        .build(),
                ToolSpecification.builder()
                        .name("tool_2")
                        .metadata(Map.of("two", 2))
                        .build()
        ));
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
                                .build()
                );
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
                                .build()
                );
    }

    private static ToolProviderRequest toolProviderRequest() {
        return ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(UserMessage.from("does not matter"))
                .build();
    }
}