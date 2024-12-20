package dev.langchain4j.mcp.client.transport.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIterable;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

public class McpHttpTransportTest {

    // FIXME: we have a full copy of the project's sources here because the
    // official node.js server-everything npm package does not include the
    // HTTP transport. Once we find a suitable and maintained MCP server
    // image (preferably docker image) that supports the HTTP transport, the
    // test could be rewritten to use that.
    static GenericContainer<?> container = new GenericContainer<>("docker.io/node:23")
            .withCopyFileToContainer(MountableFile.forClasspathResource("everything"), "/everything")
            .withLogConsumer(outputFrame -> System.out.println(outputFrame.getUtf8String()))
            .withWorkingDirectory("/everything")
            .withCommand("sh", "-c", "npm install && npm run build && node dist/sse.js")
            .withExposedPorts(3001)
            .waitingFor(Wait.forHttp("/").forStatusCode(404));

    static Integer port;

    static McpClient mcpClient;

    @BeforeAll
    public static void setup() {
        container.start();
        port = container.getMappedPort(3001);
        McpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl("http://localhost:" + port + "/sse")
                .logRequests(true)
                .logResponses(true)
                .build();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .build();
    }

    @AfterAll
    public static void cleanup() {
        container.stop();
    }

    @Test
    public void providingTools() throws Exception {
        // obtain a list of tools from the MCP server
        ToolProvider toolProvider =
                McpToolProvider.builder().mcpClients(List.of(mcpClient)).build();
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

        // verify the list of tools
        assertThat(toolProviderResult.tools().size()).isEqualTo(5);
        Set<String> toolNames = toolProviderResult.tools().keySet().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());
        assertThatIterable(toolNames)
                .containsExactlyInAnyOrder("add", "echo", "longRunningOperation", "sampleLLM", "getTinyImage");

        // verify the 'add' tool
        ToolSpecification addTool = findToolByName(toolProviderResult, "add");
        assertThat(addTool.description()).isEqualTo("Adds two numbers");
        JsonNumberSchema a =
                (JsonNumberSchema) addTool.parameters().properties().get("a");
        assertThat(a.description().equals("First number"));
        JsonNumberSchema b =
                (JsonNumberSchema) addTool.parameters().properties().get("b");
        assertThat(b.description().equals("Second number"));

        // verify the 'echo' tool
        ToolSpecification echoTool = findToolByName(toolProviderResult, "echo");
        assertThat(echoTool.description()).isEqualTo("Echoes back the input");
        JsonStringSchema input =
                (JsonStringSchema) echoTool.parameters().properties().get("message");
        assertThat(input.description().equals("Message to echo"));

        // verify the 'longRunningOperation' tool
        ToolSpecification longRunningOperationTool = findToolByName(toolProviderResult, "longRunningOperation");
        assertThat(longRunningOperationTool.description())
                .isEqualTo("Demonstrates a long running operation with progress updates");
        JsonNumberSchema duration = (JsonNumberSchema)
                longRunningOperationTool.parameters().properties().get("duration");
        assertThat(duration.description().equals("Duration of the operation in seconds"));
        JsonNumberSchema steps = (JsonNumberSchema)
                longRunningOperationTool.parameters().properties().get("steps");
        assertThat(steps.description().equals("Number of steps in the operation"));

        // verify the 'sampleLLM' tool
        ToolSpecification sampleLLMTool = findToolByName(toolProviderResult, "sampleLLM");
        assertThat(sampleLLMTool.description()).isEqualTo("Samples from an LLM using MCP's sampling feature");
        JsonStringSchema prompt =
                (JsonStringSchema) sampleLLMTool.parameters().properties().get("prompt");
        assertThat(prompt.description().equals("The prompt to send to the LLM"));
        JsonNumberSchema maxTokens =
                (JsonNumberSchema) sampleLLMTool.parameters().properties().get("maxTokens");
        assertThat(maxTokens.description().equals("Maximum number of tokens to generate"));

        // verify the 'getTinyImage' tool
        ToolSpecification getTinyImageTool = findToolByName(toolProviderResult, "getTinyImage");
        assertThat(getTinyImageTool.description()).isEqualTo("Returns the MCP_TINY_IMAGE");
        assertThat(getTinyImageTool.parameters().properties().isEmpty());
    }

    private ToolSpecification findToolByName(ToolProviderResult toolProviderResult, String name) {
        return toolProviderResult.tools().keySet().stream()
                .filter(toolSpecification -> toolSpecification.name().equals(name))
                .findFirst()
                .get();
    }

    @Test
    public void executingATool() {
        // obtain tools from the server
        ToolProvider toolProvider =
                McpToolProvider.builder().mcpClients(List.of(mcpClient)).build();
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

        // find the 'add' tool and execute it on the MCP server
        ToolExecutor executor = toolProviderResult.tools().entrySet().stream()
                .filter(entry -> entry.getKey().name().equals("add"))
                .findFirst()
                .get()
                .getValue();
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("add")
                .arguments("{\"a\": 5, \"b\": 12}")
                .build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);

        // validate the tool execution result
        assertThat(toolExecutionResultString).isEqualTo("The sum of 5 and 12 is 17.");
    }

    @Test
    public void timeout() {
        // obtain tools from the server
        ToolProvider toolProvider =
                McpToolProvider.builder().mcpClients(List.of(mcpClient)).build();
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

        // find the 'longRunningOperation' tool and execute it on the MCP server
        ToolExecutor executor = toolProviderResult.tools().entrySet().stream()
                .filter(entry -> entry.getKey().name().equals("longRunningOperation"))
                .findFirst()
                .get()
                .getValue();
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("longRunningOperation")
                .arguments("{\"duration\": 5, \"steps\": 1}")
                .build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);

        // validate the tool execution result
        assertThat(toolExecutionResultString).isEqualTo("There was a timeout executing the tool");
    }
}
