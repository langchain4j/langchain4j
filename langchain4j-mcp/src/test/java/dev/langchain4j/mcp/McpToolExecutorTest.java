package dev.langchain4j.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class McpToolExecutorTest {

    private final ToolExecutionRequest request =
            ToolExecutionRequest.builder().name("getWeather").build();
    private final InvocationContext invocationContext =
            InvocationContext.builder().build();

    private McpClient mcpClientReturning(ToolExecutionResult result) {
        McpClient mcpClient = mock(McpClient.class);
        when(mcpClient.executeTool(any(), any())).thenReturn(result);
        return mcpClient;
    }

    private McpClient asyncMcpClientReturning(ToolExecutionResult result) {
        McpClient mcpClient = mock(McpClient.class);
        when(mcpClient.executeToolAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(result));
        return mcpClient;
    }

    @Test
    void should_drop_attributes_by_default() {
        McpClient mcpClient = mcpClientReturning(ToolExecutionResult.builder()
                .resultText("Sunny, 22 degrees")
                .attributes(Map.of("example.org/traceId", "abc-123"))
                .build());

        McpToolExecutor executor = new McpToolExecutor(mcpClient);

        ToolExecutionResult result = executor.executeWithContext(request, invocationContext);

        assertThat(result.resultText()).isEqualTo("Sunny, 22 degrees");
        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void should_drop_attributes_by_default_when_executing_asynchronously() {
        McpClient mcpClient = asyncMcpClientReturning(ToolExecutionResult.builder()
                .resultText("Sunny, 22 degrees")
                .attributes(Map.of("example.org/traceId", "abc-123"))
                .build());

        McpToolExecutor executor = new McpToolExecutor(mcpClient);

        ToolExecutionResult result =
                executor.executeAsync(request, invocationContext).join();

        assertThat(result.resultText()).isEqualTo("Sunny, 22 degrees");
        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void should_return_attributes_when_enabled() {
        McpClient mcpClient = mcpClientReturning(ToolExecutionResult.builder()
                .resultText("Sunny, 22 degrees")
                .attributes(Map.of("example.org/traceId", "abc-123"))
                .build());

        McpToolExecutor executor = new McpToolExecutor(mcpClient, null, true);

        ToolExecutionResult result = executor.executeWithContext(request, invocationContext);

        assertThat(result.resultText()).isEqualTo("Sunny, 22 degrees");
        assertThat(result.attributes()).containsExactly(Map.entry("example.org/traceId", "abc-123"));
    }

    @Test
    void should_return_attributes_when_enabled_when_executing_asynchronously() {
        McpClient mcpClient = asyncMcpClientReturning(ToolExecutionResult.builder()
                .resultText("Sunny, 22 degrees")
                .attributes(Map.of("example.org/traceId", "abc-123"))
                .build());

        McpToolExecutor executor = new McpToolExecutor(mcpClient, null, true);

        ToolExecutionResult result =
                executor.executeAsync(request, invocationContext).join();

        assertThat(result.resultText()).isEqualTo("Sunny, 22 degrees");
        assertThat(result.attributes()).containsExactly(Map.entry("example.org/traceId", "abc-123"));
    }

    @Test
    void should_return_result_as_is_when_there_are_no_attributes() {
        ToolExecutionResult originalResult =
                ToolExecutionResult.builder().resultText("Sunny, 22 degrees").build();
        McpToolExecutor executor = new McpToolExecutor(mcpClientReturning(originalResult));

        assertThat(executor.executeWithContext(request, invocationContext)).isSameAs(originalResult);
    }

    @Test
    void should_return_result_as_is_when_there_are_no_attributes_when_executing_asynchronously() {
        ToolExecutionResult originalResult =
                ToolExecutionResult.builder().resultText("Sunny, 22 degrees").build();
        McpToolExecutor executor = new McpToolExecutor(asyncMcpClientReturning(originalResult));

        assertThat(executor.executeAsync(request, invocationContext).join()).isSameAs(originalResult);
    }

    @Test
    void should_propagate_cancellation_to_underlying_client_future() {
        McpClient mcpClient = mock(McpClient.class);
        CompletableFuture<ToolExecutionResult> clientFuture = new CompletableFuture<>();
        when(mcpClient.executeToolAsync(any(), any())).thenReturn(clientFuture);

        McpToolExecutor executor = new McpToolExecutor(mcpClient);

        CompletableFuture<ToolExecutionResult> executorFuture = executor.executeAsync(request, invocationContext);
        assertThat(executorFuture.cancel(true)).isTrue();

        assertThat(clientFuture).isCancelled();
        assertThatThrownBy(executorFuture::join).isInstanceOf(CancellationException.class);
    }
}
