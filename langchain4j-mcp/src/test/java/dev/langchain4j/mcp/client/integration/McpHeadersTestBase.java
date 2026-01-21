package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpHeadersSupplier;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.protocol.McpInitializationNotification;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public abstract class McpHeadersTestBase {

    static Process process;
    static McpClient mcpClient;
    static Map<String, String> headersMap = new HashMap<>();
    static volatile InvocationContext
            capturedInvocationContext; // will capture the invocation context from the tool call

    private static volatile boolean
            headerSupplierCalledForInitialize; // will be set to true when the supplier is called for the 'initialize'
    // method
    private static volatile boolean
            headerSupplierCalledForInitializedNotification; // will be set to true when the supplier is called for the
    // 'initialized' notification

    // This is the supplier of headers for the MCP client.
    // When the headersMap contains something, it will use that map as headers.
    // Otherwise, if the call is a tool call, it will return a header "X-Test-Header" with value "12345".
    static final McpHeadersSupplier customHeaders = new McpHeadersSupplier() {
        @Override
        public Map<String, String> apply(McpCallContext ctx) {
            if (ctx != null) {
                if (ctx.message() instanceof McpInitializeRequest) {
                    headerSupplierCalledForInitialize = true;
                }
                if (ctx.message() instanceof McpInitializationNotification) {
                    headerSupplierCalledForInitializedNotification = true;
                }
            }
            if (headersMap.isEmpty()) {
                if (ctx != null && ctx.message() instanceof McpCallToolRequest) {
                    if (ctx.invocationContext() != null) {
                        capturedInvocationContext = ctx.invocationContext();
                    }
                    return Map.of("X-Test-Header", "12345");
                } else {
                    return Map.of();
                }
            } else {
                return headersMap;
            }
        }
    };

    static Process startProcess() throws IOException, InterruptedException, TimeoutException {
        return startServerHttp("headers_mcp_server.java");
    }

    @AfterEach
    public void cleanup() {
        capturedInvocationContext = null;
    }

    @Test
    void initializationHeaders() {
        Assertions.assertThat(headerSupplierCalledForInitialize).isTrue();
        Assertions.assertThat(headerSupplierCalledForInitializedNotification).isTrue();
    }

    @Test
    void directToolCalls() {
        try {
            headersMap.put("X-Test-Header", "headerValue1");
            executeEchoHeadersToolAndAssertHeaderValue("headerValue1");

            headersMap.put("X-Test-Header", "headerValue2");
            executeEchoHeadersToolAndAssertHeaderValue("headerValue2");
        } finally {
            headersMap.clear();
        }
    }

    @Test
    void toolCallsViaAiService() {
        ChatModel mockChatModel = ChatModelMock.thatResponds((request) -> {
            if (request.messages().size() == 1) {
                // this is the initial chat request, respond with tool requesting response
                return AiMessage.from(ToolExecutionRequest.builder()
                        .name("echoHeader")
                        .arguments("{\"headerName\": \"X-Test-Header\"}")
                        .build());
            } else {
                // this is the follow-up request containing the result from the MCP tool execution, so just forward that
                // result as the final response
                ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) request.messages().stream()
                        .filter(m -> m instanceof ToolExecutionResultMessage)
                        .findFirst()
                        .get();
                return AiMessage.aiMessage(toolResult.text());
            }
        });

        DummyAiService service = AiServices.builder(DummyAiService.class)
                .chatModel(mockChatModel)
                .toolProvider(McpToolProvider.builder().mcpClients(mcpClient).build())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(5))
                .build();

        // call the AI service now - when it executes the tool using the MCP client, it should pass the proper
        // X-Test-Header
        Assertions.assertThat(service.chat()).isEqualTo("12345");
        // verify that the invocation context was properly passed to the header supplier
        Assertions.assertThat(capturedInvocationContext).isNotNull();
        Assertions.assertThat(capturedInvocationContext.methodName()).isEqualTo("chat");
        Assertions.assertThat(capturedInvocationContext.interfaceName())
                .isEqualTo("dev.langchain4j.mcp.client.integration.McpHeadersTestBase$DummyAiService");
    }

    interface DummyAiService {
        @UserMessage("Call the echoHeader tool with {headerName=X-Test-Header}")
        String chat();
    }

    private void executeEchoHeadersToolAndAssertHeaderValue(String expectedValue) {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("echoHeader")
                .arguments("{\"headerName\": \"X-Test-Header\"}")
                .build();
        String result = mcpClient.executeTool(toolExecutionRequest).resultText();
        assertThat(result).isEqualTo(expectedValue);
    }
}
