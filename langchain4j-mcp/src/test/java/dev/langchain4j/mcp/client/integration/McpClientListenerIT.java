package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpClientListener;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.mcp.protocol.McpClientMethod;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class McpClientListenerIT {

    static McpClient mcpClient;
    static TestListener testListener;

    @BeforeAll
    static void setup() {
        skipTestsIfJbangNotAvailable();
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(),
                        "--quiet",
                        "--fresh",
                        "run",
                        "-Dquarkus.http.port=8180",
                        getPathToScript("listener_mcp_server.java")))
                .logEvents(true)
                .build();
        testListener = new TestListener();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .listener(testListener)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        testListener.clear();
    }

    @Test
    public void successfulToolCall() {
        ToolExecutionResult result = mcpClient.executeTool(
                ToolExecutionRequest.builder().name("nothing").build());
        assertThat(result.isError()).isFalse();

        // check that the beforeToolCall callback was invoked
        assertThat(testListener.toolContext).isNotNull();
        assertThat(testListener.toolContext.message().method).isEqualTo(McpClientMethod.TOOLS_CALL);
        assertThat(testListener.toolContext.message().getId()).isNotNull();

        // check that the afterToolCall callback was invoked
        assertThat(testListener.toolResult).isNotNull();
        assertThat(testListener.toolResult.resultText()).isEqualTo("OK");
        assertThat(testListener.toolResultContext).isSameAs(testListener.toolContext);
    }

    @Test
    public void toolCallWithApplicationLevelError() {
        try {
            ToolExecutionResult result = mcpClient.executeTool(ToolExecutionRequest.builder()
                    .name("withApplicationLevelError")
                    .build());
            Assertions.fail("Should have thrown an exception");
        } catch (Exception e) {
            assertThat(testListener.toolContext).isNotNull();
            assertThat(testListener.toolContext.message().method).isEqualTo(McpClientMethod.TOOLS_CALL);
            assertThat(testListener.toolContext.message().getId()).isNotNull();
            assertThat(testListener.toolResultContext).isNotNull();
            assertThat(testListener.toolResultContext).isSameAs(testListener.toolContext);
            assertThat(testListener.toolResult).isNotNull();
            assertThat(testListener.toolResult.isError()).isTrue();
            assertThat(testListener.toolResult.resultText()).isEqualTo("Application-level error");
            assertThat(testListener.toolError).isNull();
        }
    }

    @Test
    public void toolCallWithProtocolError() {
        try {
            ToolExecutionResult result = mcpClient.executeTool(
                    ToolExecutionRequest.builder().name("withProtocolError").build());
            Assertions.fail("Should have thrown an exception");
        } catch (Exception e) {
            assertThat(testListener.toolContext).isNotNull();
            assertThat(testListener.toolContext.message().method).isEqualTo(McpClientMethod.TOOLS_CALL);
            assertThat(testListener.toolContext.message().getId()).isNotNull();
            assertThat(testListener.toolResultContext).isNull();
            assertThat(testListener.toolError).isNotNull();
            assertThat(testListener.toolError).isInstanceOf(ToolExecutionException.class);
            assertThat(testListener.toolErrorContext).isSameAs(testListener.toolContext);
        }
    }

    @Test
    public void toolTimeout() {
        ToolExecutionResult result = mcpClient.executeTool(
                ToolExecutionRequest.builder().name("longOperation").build());

        // check that the beforeToolCall callback was invoked
        assertThat(testListener.toolContext).isNotNull();
        assertThat(testListener.toolContext.message().method).isEqualTo(McpClientMethod.TOOLS_CALL);
        assertThat(testListener.toolContext.message().getId()).isNotNull();

        // check that the onToolCallError callback was invoked with TimeoutException
        assertThat(testListener.toolError).isNotNull();
        assertThat(testListener.toolError).isInstanceOf(java.util.concurrent.TimeoutException.class);
        assertThat(testListener.toolErrorContext).isSameAs(testListener.toolContext);

        // afterToolCall should NOT be invoked
        assertThat(testListener.toolResultContext).isNull();
    }

    @Test
    public void resourceGet() {
        McpReadResourceResult result = mcpClient.readResource("file:///test-resource");
        assertThat(result).isNotNull();

        // check that the beforeResourceGet callback was invoked
        assertThat(testListener.resourceContext).isNotNull();
        assertThat(testListener.resourceContext.message().method).isEqualTo(McpClientMethod.RESOURCES_READ);
        assertThat(testListener.resourceContext.message().getId()).isNotNull();

        // check that the afterResourceGet callback was invoked
        assertThat(testListener.resourceResult).isNotNull();
        assertThat(testListener.resourceResultContext).isSameAs(testListener.resourceContext);
    }

    @Test
    public void resourceGetError() {
        try {
            mcpClient.readResource("file:///test-resource-failing");
            Assertions.fail("Should have thrown an exception");
        } catch (Exception e) {
            // check that the beforeResourceGet callback was invoked
            assertThat(testListener.resourceContext).isNotNull();
            assertThat(testListener.resourceContext.message().method).isEqualTo(McpClientMethod.RESOURCES_READ);
            assertThat(testListener.resourceContext.message().getId()).isNotNull();

            // check that the onResourceGetError callback was invoked
            assertThat(testListener.resourceResult).isNull();
            assertThat(testListener.resourceError).isNotNull();
        }
    }

    @Test
    public void promptGet() {
        McpGetPromptResult result = mcpClient.getPrompt("testPrompt", Map.of());
        assertThat(result).isNotNull();

        // check that the beforePromptGet callback was invoked
        assertThat(testListener.prompt).isNotNull();
        assertThat(testListener.prompt.message().method).isEqualTo(McpClientMethod.PROMPTS_GET);
        assertThat(testListener.prompt.message().getId()).isNotNull();

        // check that the afterPromptGet callback was invoked
        assertThat(testListener.promptResult).isNotNull();
        assertThat(testListener.promptResultContext).isSameAs(testListener.prompt);
    }

    @Test
    public void promptGetError() {
        try {
            mcpClient.getPrompt("testPromptFailing", Map.of());
            Assertions.fail("Should have thrown an exception");
        } catch (Exception e) {
            // check that the beforePromptGet callback was invoked
            assertThat(testListener.prompt).isNotNull();
            assertThat(testListener.prompt.message().method).isEqualTo(McpClientMethod.PROMPTS_GET);
            assertThat(testListener.prompt.message().getId()).isNotNull();

            // check that the onPromptGetError callback was invoked
            assertThat(testListener.promptResult).isNull();
            assertThat(testListener.promptError).isNotNull();
        }
    }

    static class TestListener implements McpClientListener {

        volatile McpCallContext toolContext;
        volatile ToolExecutionResult toolResult;
        volatile McpCallContext toolResultContext;
        volatile McpCallContext toolErrorContext;
        volatile Throwable toolError;

        volatile McpCallContext resourceContext;
        volatile McpReadResourceResult resourceResult;
        volatile McpCallContext resourceResultContext;
        volatile McpCallContext resourceErrorContext;
        volatile Throwable resourceError;

        volatile McpCallContext prompt;
        volatile McpGetPromptResult promptResult;
        volatile McpCallContext promptResultContext;
        volatile McpCallContext promptErrorContext;
        volatile Throwable promptError;

        @Override
        public void beforeExecuteTool(McpCallContext context) {
            toolContext = context;
        }

        @Override
        public void afterExecuteTool(
                McpCallContext context, ToolExecutionResult result, Map<String, Object> rawResult) {
            toolResultContext = context;
            toolResult = result;
        }

        @Override
        public void onExecuteToolError(McpCallContext context, Throwable error) {
            toolErrorContext = context;
            toolError = error;
        }

        @Override
        public void beforeResourceGet(McpCallContext context) {
            resourceContext = context;
        }

        @Override
        public void afterResourceGet(
                McpCallContext context, McpReadResourceResult result, Map<String, Object> rawResult) {
            resourceResultContext = context;
            resourceResult = result;
        }

        @Override
        public void onResourceGetError(McpCallContext context, Throwable error) {
            resourceErrorContext = context;
            resourceError = error;
        }

        @Override
        public void beforePromptGet(McpCallContext context) {
            prompt = context;
        }

        @Override
        public void afterPromptGet(McpCallContext context, McpGetPromptResult result, Map<String, Object> rawResult) {
            promptResultContext = context;
            promptResult = result;
        }

        @Override
        public void onPromptGetError(McpCallContext context, Throwable error) {
            promptErrorContext = context;
            promptError = error;
        }

        void clear() {
            toolContext = null;
            toolResult = null;
            toolResultContext = null;
            toolErrorContext = null;
            toolError = null;

            resourceContext = null;
            resourceResult = null;
            resourceResultContext = null;
            resourceErrorContext = null;
            resourceError = null;

            prompt = null;
            promptResult = null;
            promptResultContext = null;
            promptErrorContext = null;
            promptError = null;
        }
    }
}
