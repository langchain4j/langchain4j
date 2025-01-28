package dev.langchain4j.mcp.client.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class McpToolsTestBase {

    static McpClient mcpClient;

    private static final Logger log = LoggerFactory.getLogger(McpToolsTestBase.class);

    @Test
    public void verifyToolSpecifications() {
        ToolProviderResult toolProviderResult = obtainTools();

        Map<ToolSpecification, ToolExecutor> tools = toolProviderResult.tools();
        assertThat(tools.size()).isEqualTo(5);

        ToolSpecification echoString = findToolSpecificationByName(toolProviderResult, "echoString");
        assertThat(echoString.description()).isEqualTo("Echoes a string");
        JsonStringSchema echoStringParam =
                (JsonStringSchema) echoString.parameters().properties().get("input");
        assertThat(echoStringParam.description()).isEqualTo("The string to be echoed");

        ToolSpecification echoInteger = findToolSpecificationByName(toolProviderResult, "echoInteger");
        assertThat(echoInteger.description()).isEqualTo("Echoes an integer");
        JsonIntegerSchema echoIntegerParam =
                (JsonIntegerSchema) echoInteger.parameters().properties().get("input");
        assertThat(echoIntegerParam.description()).isEqualTo("The integer to be echoed");

        ToolSpecification echoBoolean = findToolSpecificationByName(toolProviderResult, "echoBoolean");
        assertThat(echoBoolean.description()).isEqualTo("Echoes a boolean");
        JsonBooleanSchema echoBooleanParam =
                (JsonBooleanSchema) echoBoolean.parameters().properties().get("input");
        assertThat(echoBooleanParam.description()).isEqualTo("The boolean to be echoed");

        ToolSpecification longOperation = findToolSpecificationByName(toolProviderResult, "longOperation");
        assertThat(longOperation.description()).isEqualTo("Takes 10 seconds to complete");
        assertThat(longOperation.parameters().properties()).isEmpty();

        ToolSpecification error = findToolSpecificationByName(toolProviderResult, "error");
        assertThat(error.description()).isEqualTo("Throws a business error");
        assertThat(error.parameters().properties()).isEmpty();
    }

    @Test
    public void executeTool() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = findToolExecutorByName(toolProviderResult, "echoString");
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
        ToolExecutor executor = findToolExecutorByName(toolProviderResult, "echoString");
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": 1}") // wrong argument type
                .build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);
        assertThat(toolExecutionResultString).isEqualTo("There was an error executing the tool");
    }

    @Test
    public void executeNonExistentTool() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = findToolExecutorByName(toolProviderResult, "echoString");
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("THIS-TOOL-DOES-NOT-EXIST")
                .arguments("{\"input\": 1}")
                .build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);
        assertThat(toolExecutionResultString).isEqualTo("There was an error executing the tool");
    }

    @Test
    public void executeToolThatThrowsBusinessError() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = findToolExecutorByName(toolProviderResult, "error");
        ToolExecutionRequest toolExecutionRequest =
                ToolExecutionRequest.builder().name("error").arguments("{}").build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);
        assertThat(toolExecutionResultString).isEqualTo("There was an error executing the tool");
    }

    @Test
    public void timeout() {
        ToolProviderResult toolProviderResult = obtainTools();
        ToolExecutor executor = findToolExecutorByName(toolProviderResult, "longOperation");
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("longOperation")
                .arguments("{}")
                .build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);
        assertThat(toolExecutionResultString).isEqualTo("There was a timeout executing the tool");
    }

    ToolProviderResult obtainTools() {
        ToolProvider toolProvider =
                McpToolProvider.builder().mcpClients(List.of(mcpClient)).build();
        return toolProvider.provideTools(null);
    }

    ToolSpecification findToolSpecificationByName(ToolProviderResult toolProviderResult, String name) {
        return toolProviderResult.tools().keySet().stream()
                .filter(toolSpecification -> toolSpecification.name().equals(name))
                .findFirst()
                .get();
    }

    ToolExecutor findToolExecutorByName(ToolProviderResult toolProviderResult, String name) {
        return toolProviderResult.tools().entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(name))
                .findFirst()
                .get()
                .getValue();
    }
}
