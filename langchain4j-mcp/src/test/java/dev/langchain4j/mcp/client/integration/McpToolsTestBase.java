package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

import java.time.Duration;
import java.util.Map;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.McpToolExecutor;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AbstractAiServicesWithToolErrorHandlerTest;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

public abstract class McpToolsTestBase extends AbstractAiServicesWithToolErrorHandlerTest {

    static McpClient mcpClient;

    @Test
    public void verifyToolSpecifications() {
        ToolProviderResult toolProviderResult = obtainTools();

        Map<ToolSpecification, ToolExecutor> tools = toolProviderResult.tools();
        assertThat(tools).hasSize(12);

        ToolSpecification echoString = toolProviderResult.toolSpecificationByName("echoString");
        assertThat(echoString.description()).isEqualTo("Echoes a string");
        JsonStringSchema echoStringParam =
                (JsonStringSchema) echoString.parameters().properties().get("input");
        assertThat(echoStringParam.description()).isEqualTo("The string to be echoed");

        ToolSpecification echoInteger = toolProviderResult.toolSpecificationByName("echoInteger");
        assertThat(echoInteger.description()).isEqualTo("Echoes an integer");
        JsonIntegerSchema echoIntegerParam =
                (JsonIntegerSchema) echoInteger.parameters().properties().get("input");
        assertThat(echoIntegerParam.description()).isEqualTo("The integer to be echoed");

        ToolSpecification echoBoolean = toolProviderResult.toolSpecificationByName("echoBoolean");
        assertThat(echoBoolean.description()).isEqualTo("Echoes a boolean");
        JsonBooleanSchema echoBooleanParam =
                (JsonBooleanSchema) echoBoolean.parameters().properties().get("input");
        assertThat(echoBooleanParam.description()).isEqualTo("The boolean to be echoed");

        ToolSpecification longOperation = toolProviderResult.toolSpecificationByName("longOperation");
        assertThat(longOperation.description())
                .isEqualTo("Takes 10 seconds to complete. "
                        + "If the execution is cancelled, the wasCancellationReceived tool will start returning true");
        assertThat(longOperation.parameters().properties()).isEmpty();

        ToolSpecification error = toolProviderResult.toolSpecificationByName("error");
        assertThat(error.description()).isEqualTo("Throws a business error");
        assertThat(error.parameters().properties()).isEmpty();

        ToolSpecification errorResponse = toolProviderResult.toolSpecificationByName("errorResponse");
        assertThat(errorResponse.description()).isEqualTo("Returns a response as an error");
        assertThat(errorResponse.parameters().properties()).isEmpty();

        ToolSpecification structuredContent = toolProviderResult.toolSpecificationByName("structuredContent");
        assertThat(structuredContent.description()).isEqualTo("Returns structured content");
        assertThat(structuredContent.parameters().properties()).isEmpty();
    }

    @Test
    public void executeTool() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = toolProviderResult.toolExecutorByName("echoString");
        ToolExecutionRequest toolExecutionRequest =
                ToolExecutionRequest.builder().arguments("{\"input\": \"abc\"}").build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);
        assertThat(toolExecutionResultString).isEqualTo("abc");
    }

    @Test
    public void executeToolThatReturnsStructuredContent() {
        ToolProviderResult toolProviderResult = obtainTools();
        McpToolExecutor executor = (McpToolExecutor) toolProviderResult.toolExecutorByName("structuredContent");
        ToolExecutionRequest toolExecutionRequest =
                ToolExecutionRequest.builder().arguments("").build();
        ToolExecutionResult toolExecutionResult = executor.executeWithContext(
                toolExecutionRequest, InvocationContext.builder().build());
        assertThat(toolExecutionResult.resultText()).isEqualTo("{\"bar\":1,\"baz\":\"hello\"}");
        assertThat(toolExecutionResult.result()).isInstanceOf(Map.class);
        Map<String, Object> resultMap = (Map<String, Object>) toolExecutionResult.result();
        assertThat(resultMap).hasSize(2);
        assertThat(resultMap.get("bar")).isEqualTo(1);
        assertThat(resultMap.get("baz")).isEqualTo("hello");
    }

    @Test
    public void executeToolWithWrongArgumentType() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = toolProviderResult.toolExecutorByName("echoString");
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .arguments("{\"input\": 1}") // wrong argument type
                .build();
        assertThatThrownBy(() -> executor.execute(toolExecutionRequest, null))
                .isExactlyInstanceOf(ToolArgumentsException.class)
                .hasMessageMatching(".+")
                .hasFieldOrPropertyWithValue("errorCode", -32602);
    }

    @Test
    public void executeToolThatThrowsBusinessError() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = toolProviderResult.toolExecutorByName("error");
        ToolExecutionRequest toolExecutionRequest =
                ToolExecutionRequest.builder().arguments("{}").build();
        assertThatThrownBy(() -> executor.execute(toolExecutionRequest, null))
                .isExactlyInstanceOf(ToolExecutionException.class)
                .hasMessage("Internal error")
                .hasFieldOrPropertyWithValue("errorCode", -32603);
    }

    @Test
    public void executeToolThatReturnsError() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = toolProviderResult.toolExecutorByName("errorResponse");
        ToolExecutionRequest toolExecutionRequest =
                ToolExecutionRequest.builder().arguments("{}").build();
        assertThatThrownBy(() -> executor.execute(toolExecutionRequest, null))
                .isExactlyInstanceOf(ToolExecutionException.class)
                .hasMessage("This is an actual error");
    }

    @Test
    public void timeout() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = toolProviderResult.toolExecutorByName("longOperation");
        ToolExecutionRequest toolExecutionRequest =
                ToolExecutionRequest.builder().arguments("{}").build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);
        assertThat(toolExecutionResultString).isEqualTo("There was a timeout executing the tool");
        ToolExecutionRequest checkCancellationRequest =
                ToolExecutionRequest.builder().arguments("{}").build();
        // wait until the server can confirm that the cancellation notification was received
        await().timeout(Duration.ofSeconds(30))
                .until(
                        () -> toolProviderResult
                                .toolExecutorByName("wasCancellationReceived")
                                .execute(checkCancellationRequest, null),
                        is("true"));
    }

    // this is specifically for 'executeToolWithUntypedArrayParameter'
    OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    public void executeToolWithUntypedArrayParameter() {
        ChatService service = AiServices.builder(ChatService.class)
                .toolProvider(createMcpToolProvider())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .chatModel(chatModel)
                .build();
        String response = service.chat(
                "Call the tool named 'untypedArray' with this array as the 'arr' parameter: [0, \"abs\", null], and pass me the result.");
        assertThat(response).contains("6789");
    }

    interface ChatService {
        String chat(String prompt);
    }

    ToolProviderResult obtainTools() {
        return createMcpToolProvider().provideTools(null);
    }

    @Override
    protected void configureGetWeatherThrowingExceptionTool(RuntimeException ignored, AiServices<?> aiServiceBuilder) {
        aiServiceBuilder.toolProvider(createMcpToolProvider());
    }

    @Override
    protected void configureGetWeatherThrowingExceptionWithoutMessageTool(RuntimeException ignored, AiServices<?> aiServiceBuilder) {
        configureGetWeatherThrowingExceptionTool(ignored, aiServiceBuilder);
    }

    @Override
    protected void configureGetWeatherTool(AiServices<?> aiServiceBuilder) {
        aiServiceBuilder.toolProvider(createMcpToolProvider());
    }

    private static McpToolProvider createMcpToolProvider() {
        return McpToolProvider.builder().mcpClients(mcpClient).build();
    }
}
