package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.AbstractAiServicesWithToolErrorHandlerTest;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

public abstract class McpToolsTestBase extends AbstractAiServicesWithToolErrorHandlerTest {

    static McpClient mcpClient;

    @Test
    public void verifyToolSpecifications() {
        ToolProviderResult toolProviderResult = obtainTools();

        Map<ToolSpecification, ToolExecutor> tools = toolProviderResult.tools();
        assertThat(tools).hasSize(9);

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
        assertThat(longOperation.description()).isEqualTo("Takes 10 seconds to complete");
        assertThat(longOperation.parameters().properties()).isEmpty();

        ToolSpecification error = toolProviderResult.toolSpecificationByName("error");
        assertThat(error.description()).isEqualTo("Throws a business error");
        assertThat(error.parameters().properties()).isEmpty();

        ToolSpecification errorResponse = toolProviderResult.toolSpecificationByName("errorResponse");
        assertThat(errorResponse.description()).isEqualTo("Returns a response as an error");
        assertThat(errorResponse.parameters().properties()).isEmpty();
    }

    @Test
    public void executeTool() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = toolProviderResult.toolExecutorByName("echoString");
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"abc\"}")
                .build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);
        assertThat(toolExecutionResultString).isEqualTo("abc");
    }

    @Test
    public void executeToolWithWrongArgumentType() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = toolProviderResult.toolExecutorByName("echoString");
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": 1}") // wrong argument type
                .build();
        assertThatThrownBy(() -> executor.execute(toolExecutionRequest, null))
                .isExactlyInstanceOf(ToolExecutionException.class) // TODO should be ToolArgumentsException
                .hasMessage("Internal error")
                .hasFieldOrPropertyWithValue("errorCode", -32603); // TODO should be -32602
    }

    @Test
    public void executeNonExistentTool() {
        // this should never happen when used through AI Service, as it will be handled by hallucinatedToolNameStrategy
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = toolProviderResult.toolExecutorByName("echoString");
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("THIS-TOOL-DOES-NOT-EXIST")
                .arguments("{\"input\": 1}")
                .build();
        assertThatThrownBy(() -> executor.execute(toolExecutionRequest, null))
                .isExactlyInstanceOf(ToolArgumentsException.class)
                .hasMessage("Invalid tool name: THIS-TOOL-DOES-NOT-EXIST")
                .hasFieldOrPropertyWithValue("errorCode", -32602);
    }

    @Test
    public void executeToolThatThrowsBusinessError() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = toolProviderResult.toolExecutorByName("error");
        ToolExecutionRequest toolExecutionRequest =
                ToolExecutionRequest.builder().name("error").arguments("{}").build();
        assertThatThrownBy(() -> executor.execute(toolExecutionRequest, null))
                .isExactlyInstanceOf(ToolExecutionException.class)
                .hasMessage("Internal error")
                .hasFieldOrPropertyWithValue("errorCode", -32603);
    }

    @Test
    public void executeToolThatReturnsError() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = toolProviderResult.toolExecutorByName("errorResponse");
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("errorResponse")
                .arguments("{}")
                .build();
        assertThatThrownBy(() -> executor.execute(toolExecutionRequest, null))
                .isExactlyInstanceOf(ToolExecutionException.class)
                .hasMessage("This is an actual error");
    }

    @Test
    public void timeout() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = toolProviderResult.toolExecutorByName("longOperation");
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("longOperation")
                .arguments("{}")
                .build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);
        assertThat(toolExecutionResultString).isEqualTo("There was a timeout executing the tool");
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
    protected void configureGetWeatherTool(AiServices<?> aiServiceBuilder) {
        aiServiceBuilder.toolProvider(createMcpToolProvider());
    }

    private static McpToolProvider createMcpToolProvider() {
        return McpToolProvider.builder()
                .mcpClients(mcpClient)
                .build();
    }
}
