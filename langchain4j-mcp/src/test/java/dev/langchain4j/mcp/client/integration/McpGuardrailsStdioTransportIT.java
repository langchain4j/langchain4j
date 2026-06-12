package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.guardrail.McpToolInputGuardrail;
import dev.langchain4j.mcp.client.guardrail.McpToolInputGuardrailResult;
import dev.langchain4j.mcp.client.guardrail.McpToolOutputGuardrail;
import dev.langchain4j.mcp.client.guardrail.McpToolOutputGuardrailResult;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class McpGuardrailsStdioTransportIT {

    private McpClient mcpClient;

    @AfterEach
    void teardown() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
    }

    private McpClient createClient(
            List<McpToolInputGuardrail> inputGuardrails, List<McpToolOutputGuardrail> outputGuardrails) {
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(), "--quiet", "--fresh", "run", getPathToScript("tools_mcp_server.java")))
                .logEvents(true)
                .build();
        return new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .inputGuardrails(inputGuardrails)
                .outputGuardrails(outputGuardrails)
                .build();
    }

    @Test
    void inputGuardrailRejectsOnForbiddenWord() {
        McpToolInputGuardrail forbiddenWordGuardrail = request -> {
            String args = request.toolExecutionRequest().arguments();
            if (args != null && args.contains("FORBIDDEN")) {
                return McpToolInputGuardrailResult.failure("Input rejected: contains forbidden content");
            }
            return McpToolInputGuardrailResult.success();
        };

        mcpClient = createClient(List.of(forbiddenWordGuardrail), List.of());

        ToolExecutionResult rejectedResult = mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"FORBIDDEN\"}")
                .build());
        assertThat(rejectedResult.isError()).isTrue();
        assertThat(rejectedResult.resultText()).isEqualTo("Input rejected: contains forbidden content");

        ToolExecutionResult allowedResult = mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"hello\"}")
                .build());
        assertThat(allowedResult.isError()).isFalse();
        assertThat(allowedResult.resultText()).isEqualTo("hello");
    }

    @Test
    void outputGuardrailRejectsMalformedDocument() {
        McpToolOutputGuardrail validator = request -> {
            String text = request.toolExecutionResult().resultText();
            if (text == null || !text.startsWith("{")) {
                return McpToolOutputGuardrailResult.failure("Output rejected: expected JSON document");
            }
            return McpToolOutputGuardrailResult.success(request.toolExecutionResult());
        };

        mcpClient = createClient(List.of(), List.of(validator));

        ToolExecutionResult rejectedResult = mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"not-json\"}")
                .build());
        assertThat(rejectedResult.isError()).isTrue();
        assertThat(rejectedResult.resultText()).isEqualTo("Output rejected: expected JSON document");

        ToolExecutionResult allowedResult = mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"{\\\"key\\\": \\\"value\\\"}\"}")
                .build());
        assertThat(allowedResult.isError()).isFalse();
        assertThat(allowedResult.resultText()).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void outputGuardrailTransformsResult() {
        McpToolOutputGuardrail uppercaser = request -> {
            String text = request.toolExecutionResult().resultText();
            ToolExecutionResult transformed =
                    ToolExecutionResult.builder().resultText(text.toUpperCase()).build();
            return McpToolOutputGuardrailResult.success(transformed);
        };

        mcpClient = createClient(List.of(), List.of(uppercaser));

        ToolExecutionResult result = mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"hello world\"}")
                .build());
        assertThat(result.isError()).isFalse();
        assertThat(result.resultText()).isEqualTo("HELLO WORLD");
    }

    @Test
    void multipleInputGuardrailsExecuteInOrder() {
        List<String> executionOrder = new ArrayList<>();

        McpToolInputGuardrail first = request -> {
            executionOrder.add("first");
            String args = request.toolExecutionRequest().arguments();
            if (args != null && args.contains("BLOCK_FIRST")) {
                return McpToolInputGuardrailResult.failure("Blocked by first guardrail");
            }
            return McpToolInputGuardrailResult.success();
        };

        McpToolInputGuardrail second = request -> {
            executionOrder.add("second");
            String args = request.toolExecutionRequest().arguments();
            if (args != null && args.contains("BLOCK_SECOND")) {
                return McpToolInputGuardrailResult.failure("Blocked by second guardrail");
            }
            return McpToolInputGuardrailResult.success();
        };

        mcpClient = createClient(List.of(first, second), List.of());

        // Both guardrails pass
        executionOrder.clear();
        ToolExecutionResult successResult = mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"ok\"}")
                .build());
        assertThat(successResult.isError()).isFalse();
        assertThat(executionOrder).containsExactly("first", "second");

        // First guardrail blocks — second should not execute
        executionOrder.clear();
        ToolExecutionResult blockedByFirst = mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"BLOCK_FIRST\"}")
                .build());
        assertThat(blockedByFirst.isError()).isTrue();
        assertThat(blockedByFirst.resultText()).isEqualTo("Blocked by first guardrail");
        assertThat(executionOrder).containsExactly("first");

        // Second guardrail blocks — first passes
        executionOrder.clear();
        ToolExecutionResult blockedBySecond = mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"BLOCK_SECOND\"}")
                .build());
        assertThat(blockedBySecond.isError()).isTrue();
        assertThat(blockedBySecond.resultText()).isEqualTo("Blocked by second guardrail");
        assertThat(executionOrder).containsExactly("first", "second");
    }

    @Test
    void outputGuardrailChainThreadsTransformedResult() {
        McpToolOutputGuardrail addPrefix = request -> {
            String text = request.toolExecutionResult().resultText();
            return McpToolOutputGuardrailResult.success(ToolExecutionResult.builder()
                    .resultText("[PREFIXED] " + text)
                    .build());
        };

        McpToolOutputGuardrail addSuffix = request -> {
            String text = request.toolExecutionResult().resultText();
            return McpToolOutputGuardrailResult.success(ToolExecutionResult.builder()
                    .resultText(text + " [SUFFIXED]")
                    .build());
        };

        mcpClient = createClient(List.of(), List.of(addPrefix, addSuffix));

        ToolExecutionResult result = mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"data\"}")
                .build());
        assertThat(result.isError()).isFalse();
        assertThat(result.resultText()).isEqualTo("[PREFIXED] data [SUFFIXED]");
    }

    @Test
    void guardrailReceivesMcpClientReference() {
        AtomicInteger inputChecked = new AtomicInteger(0);
        AtomicInteger outputChecked = new AtomicInteger(0);

        McpToolInputGuardrail inputGuardrail = request -> {
            assertThat(request.mcpClient()).isNotNull();
            inputChecked.incrementAndGet();
            return McpToolInputGuardrailResult.success();
        };

        McpToolOutputGuardrail outputGuardrail = request -> {
            assertThat(request.mcpClient()).isNotNull();
            outputChecked.incrementAndGet();
            return McpToolOutputGuardrailResult.success(request.toolExecutionResult());
        };

        mcpClient = createClient(List.of(inputGuardrail), List.of(outputGuardrail));

        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"test\"}")
                .build());
        assertThat(inputChecked.get()).isEqualTo(1);
        assertThat(outputChecked.get()).isEqualTo(1);
    }
}
