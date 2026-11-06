package dev.langchain4j.agentic.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.McpClientAgent;
import dev.langchain4j.agentic.declarative.McpClientSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupplierParameterResolver;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The MCP client of an MCP client agent is created and its tool resolved lazily, on the first invocation, so that
 * building an agentic system never depends on the availability of the MCP server (issue #6957). The name of the
 * agent is always known locally, the description and the argument details come from the tool when first needed, a
 * failed resolution is retried, and concurrent first invocations resolve the tool once.
 */
class McpLazyToolTest {

    private static final ToolSpecification WRITER_TOOL = tool("writer", "Writes a story about a topic", "topic");

    private static ToolSpecification tool(String name, String description, String... inputKeys) {
        JsonObjectSchema.Builder schema = JsonObjectSchema.builder();
        for (String inputKey : inputKeys) {
            schema.addProperty(
                    inputKey,
                    JsonStringSchema.builder().description("The " + inputKey).build());
        }
        return ToolSpecification.builder()
                .name(name)
                .description(description)
                .parameters(schema.build())
                .build();
    }

    /**
     * An MCP server, as seen through the clients it hands out: counts the clients created and the tool listings, and
     * can be told to be down, or slow.
     */
    static class McpServer implements Supplier<McpClient> {

        final List<ToolSpecification> tools;
        final String reply;
        final AtomicInteger clients = new AtomicInteger();
        final AtomicInteger listings = new AtomicInteger();
        volatile boolean down;
        volatile long delayMillis;

        McpServer(String reply, ToolSpecification... tools) {
            this.reply = reply;
            this.tools = List.of(tools);
        }

        @Override
        public McpClient get() {
            clients.incrementAndGet();
            if (down) {
                // like DefaultMcpClient, whose constructor fails when the server does not answer its initialization
                throw new IllegalStateException("connection refused");
            }
            McpClient client = mock(McpClient.class);
            when(client.listTools()).thenAnswer(invocation -> listTools());
            when(client.executeTool(any())).thenAnswer(invocation -> ToolExecutionResult.builder()
                    .resultText(reply)
                    .isError(false)
                    .build());
            return client;
        }

        private List<ToolSpecification> listTools() throws InterruptedException {
            listings.incrementAndGet();
            if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }
            if (down) {
                throw new IllegalStateException("connection lost");
            }
            return tools;
        }
    }

    /** The server of the declarative agents below, replaced before each test. */
    static McpServer server;

    @BeforeEach
    void startServer() {
        server = new McpServer("a story about dragons", WRITER_TOOL);
    }

    /** A planner model that immediately reports the task as done. */
    private static ChatModelMock donePlannerModel() {
        return ChatModelMock.thatAlwaysResponds("""
                {"agentName": "done", "arguments": {"response": "nothing to write"}}
                """);
    }

    private static String systemMessage(ChatModelMock plannerModel, int request) {
        return plannerModel.requests().get(request).messages().stream()
                .filter(SystemMessage.class::isInstance)
                .map(message -> ((SystemMessage) message).text())
                .findFirst()
                .orElseThrow();
    }

    private static <E extends Throwable> E causeOfType(Throwable throwable, Class<E> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
        }
        throw new AssertionError("No " + type.getSimpleName() + " in the cause chain of " + throwable);
    }

    private static boolean hasCauseOfType(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    public static class Analyzer {

        @Agent(description = "Analyzes the request", outputKey = "topic")
        public static String analyze(@V("request") String request) {
            return "topic of " + request;
        }
    }

    public interface StoryAgent {

        @McpClientAgent(toolName = "writer", outputKey = "story")
        String generateStory(@V("topic") String topic);

        @McpClientSupplier
        static Object mcpClient() {
            return server.get();
        }
    }

    public interface Pipeline {

        @SequenceAgent(
                outputKey = "story",
                subAgents = {Analyzer.class, StoryAgent.class})
        String process(@V("request") String request);
    }

    public interface NeverActivatedStoryAgent {

        @McpClientAgent(toolName = "writer", outputKey = "story")
        String generateStory(@V("topic") String topic);

        @McpClientSupplier
        static Object mcpClient() {
            return server.get();
        }

        @ActivationCondition
        static boolean never(AgenticScope scope) {
            return false;
        }
    }

    public interface PipelineWithOptionalStory {

        @SequenceAgent(
                outputKey = "topic",
                subAgents = {Analyzer.class, NeverActivatedStoryAgent.class})
        String process(@V("request") String request);
    }

    public interface NamedStoryAgent {

        @McpClientAgent(
                toolName = "writer",
                name = "storyteller",
                value = "Tells a story about the topic",
                outputKey = "story")
        String generateStory(@V("topic") String topic);

        @McpClientSupplier
        static Object mcpClient() {
            return server.get();
        }
    }

    public interface StoryAgentWithMissingTool {

        @McpClientAgent(toolName = "poet", outputKey = "story")
        String generateStory(@V("topic") String topic);

        @McpClientSupplier
        static Object mcpClient() {
            return server.get();
        }
    }

    public interface StoryAgentWithWrongClient {

        @McpClientAgent(toolName = "writer", outputKey = "story")
        String generateStory(@V("topic") String topic);

        @McpClientSupplier
        static Object mcpClient() {
            return "not a client";
        }
    }

    /** A typed agent for the programmatic builder. */
    public interface Writer {

        @Agent
        String write(@V("topic") String topic);
    }

    public interface DescribedWriter {

        @Agent(name = "storyteller", description = "Tells a story about the topic")
        String write(@V("topic") String topic);
    }

    @Test
    void building_a_workflow_does_not_require_the_mcp_server_to_be_up() {
        server.down = true;
        // the issue's reproducer: the client used to be created, and the tools listed, here
        Pipeline pipeline = AgenticServices.createAgenticSystem(Pipeline.class, (ChatModel) null);
        assertThat(server.clients).hasValue(0);

        Throwable failure = catchThrowable(() -> pipeline.process("dragons"));

        McpToolResolutionException toolException = causeOfType(failure, McpToolResolutionException.class);
        assertThat(toolException.agentName()).isEqualTo("generateStory");
        assertThat(toolException.toolName()).isEqualTo("writer");
        assertThat(toolException)
                .hasMessageContaining("'generateStory'")
                .hasMessageContaining("'writer'")
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(server.clients).hasValue(1);
    }

    @Test
    void an_error_handler_can_skip_an_agent_whose_mcp_server_is_down() {
        server.down = true;
        StoryAgent storyAgent = AgenticServices.createAgenticSystem(StoryAgent.class, (ChatModel) null);
        AgenticScopeFunction<String> analyzer = AgenticServices.nonAiAgentBuilder(
                        scope -> "topic of " + scope.readState("request", ""))
                .name("analyzer")
                .description("Analyzes the request")
                .outputKey("topic")
                .build();
        UntypedAgent pipeline = AgenticServices.sequenceBuilder()
                .subAgents(analyzer, storyAgent)
                .outputKey("story")
                .errorHandler(errorContext -> hasCauseOfType(errorContext.exception(), McpToolResolutionException.class)
                        ? ErrorRecoveryResult.result("no story available, the MCP server is down")
                        : ErrorRecoveryResult.throwException())
                .build();

        assertThat(pipeline.invoke(Map.of("request", "dragons")))
                .isEqualTo("no story available, the MCP server is down");
    }

    @Test
    void a_never_activated_mcp_subagent_never_creates_the_client() {
        PipelineWithOptionalStory pipeline =
                AgenticServices.createAgenticSystem(PipelineWithOptionalStory.class, (ChatModel) null);

        assertThat(pipeline.process("dragons")).isEqualTo("topic of dragons");
        assertThat(server.clients).hasValue(0);
    }

    @Test
    void the_client_is_created_and_the_tools_listed_once_across_concurrent_invocations() throws Exception {
        server.delayMillis = 200;
        Writer writer = McpAgent.builder(server, Writer.class).outputKey("story").build();

        int invocations = 8;
        CyclicBarrier start = new CyclicBarrier(invocations);
        ExecutorService executor = Executors.newFixedThreadPool(invocations);
        try {
            Callable<String> invocation = () -> {
                start.await(10, TimeUnit.SECONDS);
                return writer.write("dragons");
            };
            List<Future<String>> results = executor.invokeAll(Collections.nCopies(invocations, invocation));
            for (Future<String> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("a story about dragons");
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(server.clients).hasValue(1);
        assertThat(server.listings).hasValue(1);
    }

    @Test
    void a_failed_resolution_is_retried_on_the_next_invocation() {
        server.down = true;
        Writer writer = McpAgent.builder(server, Writer.class)
                .toolName("writer")
                .outputKey("story")
                .build();

        assertThatThrownBy(() -> writer.write("dragons"))
                .isInstanceOf(McpToolResolutionException.class)
                .hasMessageContaining("'write'")
                .hasMessageContaining("'writer'")
                .hasCauseInstanceOf(IllegalStateException.class);

        server.down = false;
        assertThat(writer.write("dragons")).isEqualTo("a story about dragons");
        assertThat(writer.write("dragons")).isEqualTo("a story about dragons");
        assertThat(server.clients).hasValue(2);
        assertThat(server.listings).hasValue(1);
    }

    @Test
    void name_and_id_are_known_without_the_client() {
        // a typed agent is named after its agent method
        Writer writer = McpAgent.builder(server, Writer.class)
                .toolName("writer")
                .outputKey("story")
                .build();
        AgentInstance writerInstance = (AgentInstance) writer;
        assertThat(writerInstance.name()).isEqualTo("write");
        assertThat(writerInstance.agentId()).isEqualTo("write");
        ((InternalAgent) writer).appendId("$1");
        assertThat(writerInstance.agentId()).isEqualTo("write$1");
        assertThat(writerInstance.name()).isEqualTo("write");

        // an explicit name and description win, from the builder or from the annotations
        Writer namedWriter = McpAgent.builder(server, Writer.class)
                .name("storyteller")
                .description("Tells a story")
                .outputKey("story")
                .build();
        assertThat(((AgentInstance) namedWriter).name()).isEqualTo("storyteller");
        assertThat(((AgentInstance) namedWriter).description()).isEqualTo("Tells a story");
        DescribedWriter describedWriter =
                McpAgent.builder(server, DescribedWriter.class).build();
        assertThat(((AgentInstance) describedWriter).name()).isEqualTo("storyteller");
        assertThat(((AgentInstance) describedWriter).description()).isEqualTo("Tells a story about the topic");

        // the same holds for the declarative API
        NamedStoryAgent namedDeclarative = AgenticServices.createAgenticSystem(NamedStoryAgent.class, (ChatModel) null);
        assertThat(((AgentInstance) namedDeclarative).name()).isEqualTo("storyteller");
        assertThat(((AgentInstance) namedDeclarative).description()).isEqualTo("Tells a story about the topic");
        StoryAgent declarative = AgenticServices.createAgenticSystem(StoryAgent.class, (ChatModel) null);
        assertThat(((AgentInstance) declarative).name()).isEqualTo("generateStory");

        // an untyped agent is named after its tool, when configured, else with a placeholder
        UntypedAgent untyped = McpAgent.builder(server).toolName("writer").inputKeys("topic").build();
        assertThat(((AgentInstance) untyped).name()).isEqualTo("writer");
        UntypedAgent nameless = McpAgent.builder(server).inputKeys("topic").build();
        assertThat(((AgentInstance) nameless).name()).isEqualTo("mcp-agent");

        assertThat(server.clients).hasValue(0);
    }

    @Test
    void the_description_comes_from_the_tool_and_the_name_does_not_change_after_the_resolution() {
        Writer writer = McpAgent.builder(server, Writer.class).outputKey("story").build();
        AgentInstance writerInstance = (AgentInstance) writer;
        assertThat(server.clients).hasValue(0);

        assertThat(writerInstance.description()).isEqualTo("Writes a story about a topic");
        assertThat(server.clients).hasValue(1);
        assertThat(server.listings).hasValue(1);

        assertThat(writer.write("dragons")).isEqualTo("a story about dragons");
        assertThat(writerInstance.name()).isEqualTo("write");
        assertThat(writerInstance.agentId()).isEqualTo("write");
        McpClientInstance mcpInstance = (McpClientInstance) writer;
        assertThat(mcpInstance.toolName()).isEqualTo("writer");
        assertThat(mcpInstance.toolSpecification().name()).isEqualTo("writer");
        assertThat(server.listings).hasValue(1);
    }

    @Test
    void the_arguments_are_local_until_the_tool_is_resolved() {
        // an untyped agent with explicit input keys reports them without types
        UntypedAgent untyped = McpAgent.builder(server).toolName("writer").inputKeys("topic").build();
        McpClientInstance untypedInstance = (McpClientInstance) untyped;
        assertThat(untypedInstance.arguments()).extracting(AgentArgument::name).containsExactly("topic");
        assertThat(untypedInstance.arguments().get(0).type()).isEqualTo(Object.class);
        assertThat(untypedInstance.arguments().get(0).description()).isNullOrEmpty();
        // and the types and descriptions of the input schema once the tool is resolved
        untypedInstance.toolSpecification();
        assertThat(untypedInstance.arguments().get(0).type()).isEqualTo(String.class);
        assertThat(untypedInstance.arguments().get(0).description()).isEqualTo("The topic");

        // an untyped agent without input keys only knows them from the schema
        UntypedAgent keyless = McpAgent.builder(server).toolName("writer").build();
        McpClientInstance keylessInstance = (McpClientInstance) keyless;
        assertThat(keylessInstance.arguments()).isEmpty();
        assertThat(keylessInstance.inputKeys()).containsExactly("topic");
        assertThat(keylessInstance.arguments()).extracting(AgentArgument::name).containsExactly("topic");

        // a typed agent reports the arguments of its method, with the schema descriptions once resolved
        Writer writer = McpAgent.builder(server, Writer.class).toolName("writer").build();
        McpClientInstance writerInstance = (McpClientInstance) writer;
        assertThat(writerInstance.arguments()).extracting(AgentArgument::name).containsExactly("topic");
        assertThat(writerInstance.arguments().get(0).type()).isEqualTo(String.class);
        assertThat(writerInstance.arguments().get(0).description()).isNullOrEmpty();
        assertThat(server.clients).hasValue(2);
        writerInstance.toolSpecification();
        assertThat(writerInstance.arguments().get(0).description()).isEqualTo("The topic");
        assertThat(server.clients).hasValue(3);
    }

    @Test
    void a_missing_input_of_a_typed_agent_fails_before_the_client_is_created() {
        Writer writer = McpAgent.builder(server, Writer.class)
                .toolName("writer")
                .outputKey("story")
                .build();
        UntypedAgent pipeline =
                AgenticServices.sequenceBuilder().subAgents(writer).outputKey("story").build();

        assertThatThrownBy(() -> pipeline.invoke(Map.of("request", "dragons")))
                .isInstanceOf(MissingArgumentException.class)
                .hasMessageContaining("topic");
        assertThat(server.clients).hasValue(0);
    }

    @Test
    void a_tool_that_is_not_found_fails_at_the_first_invocation() {
        StoryAgentWithMissingTool storyAgent =
                AgenticServices.createAgenticSystem(StoryAgentWithMissingTool.class, (ChatModel) null);
        assertThat(server.clients).hasValue(0);

        Throwable failure = catchThrowable(() -> storyAgent.generateStory("dragons"));

        assertThat(causeOfType(failure, AgenticSystemConfigurationException.class))
                .hasMessageContaining("'poet' not found")
                .hasMessageContaining("writer");
    }

    @Test
    void a_supplier_returning_something_else_than_a_client_is_a_configuration_error() {
        StoryAgentWithWrongClient storyAgent =
                AgenticServices.createAgenticSystem(StoryAgentWithWrongClient.class, (ChatModel) null);

        Throwable failure = catchThrowable(() -> storyAgent.generateStory("dragons"));

        assertThat(causeOfType(failure, AgenticSystemConfigurationException.class))
                .hasMessageContaining(StoryAgentWithWrongClient.class.getName())
                .hasMessageContaining("java.lang.String")
                .hasMessageContaining("McpClient");
    }

    @Test
    void a_supervisor_resolves_the_tool_of_a_subagent_without_description_when_first_invoked() {
        Writer writer = McpAgent.builder(server, Writer.class)
                .toolName("writer")
                .outputKey("story")
                .build();
        ChatModelMock plannerModel = donePlannerModel();
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(writer)
                .build();
        assertThat(server.clients).hasValue(0);

        assertThat(supervisor.invoke("write a story")).isEqualTo("nothing to write");
        assertThat(server.clients).hasValue(1);
        assertThat(systemMessage(plannerModel, 0)).contains("'write$0', 'Writes a story about a topic'");
    }

    @Test
    void a_supervisor_does_not_contact_the_server_for_a_subagent_with_an_explicit_description() {
        Writer writer = McpAgent.builder(server, Writer.class)
                .toolName("writer")
                .description("Tells a story about the topic")
                .outputKey("story")
                .build();
        ChatModelMock plannerModel = donePlannerModel();
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(writer)
                .build();

        assertThat(supervisor.invoke("write a story")).isEqualTo("nothing to write");
        assertThat(server.clients).hasValue(0);
        assertThat(systemMessage(plannerModel, 0)).contains("'write$0', 'Tells a story about the topic'");
    }

    @Test
    void a_supervisor_leaves_out_a_subagent_whose_server_is_down_and_offers_it_again_once_it_is_back() {
        server.down = true;
        Writer writer = McpAgent.builder(server, Writer.class)
                .toolName("writer")
                .outputKey("story")
                .build();
        AgenticScopeFunction<String> analyzer = AgenticServices.nonAiAgentBuilder(scope -> "analyzed")
                .name("analyzer")
                .description("Analyzes the request")
                .outputKey("topic")
                .build();
        ChatModelMock plannerModel = donePlannerModel();
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(analyzer, writer)
                .build();

        // the MCP server is down: the supervisor plans with its other subagents only, instead of failing
        assertThat(supervisor.invoke("write a story")).isEqualTo("nothing to write");
        assertThat(systemMessage(plannerModel, 0))
                .contains("'analyzer$0', 'Analyzes the request'")
                .doesNotContain("write$1");

        // the MCP server is back: the next invocation resolves the tool and offers the agent to the model
        server.down = false;
        assertThat(supervisor.invoke("write a story")).isEqualTo("nothing to write");
        assertThat(plannerModel.requests()).hasSize(2);
        assertThat(systemMessage(plannerModel, 1)).contains("'write$1', 'Writes a story about a topic'");
        assertThat(server.clients).hasValue(2);
    }

    /** An agent instance type the MCP client agent does not implement. */
    interface OtherAgentInstance extends AgentInstance {}

    @Test
    void the_mcp_agent_of_a_declarative_system_can_be_warmed_up_through_as() {
        Pipeline pipeline = AgenticServices.createAgenticSystem(Pipeline.class, (ChatModel) null);
        AgentInstance storyAgent = ((AgentInstance) pipeline)
                .subagents().stream()
                        .filter(subagent -> subagent.name().equals("generateStory"))
                        .findFirst()
                        .orElseThrow();
        assertThat(server.clients).hasValue(0);

        // the wrapper of the MCP agent gives access to the client agent, which resolves the tool at once
        assertThat(storyAgent.as(McpClientInstance.class).toolSpecification().name()).isEqualTo("writer");
        assertThat(server.clients).hasValue(1);
        assertThatThrownBy(() -> storyAgent.as(OtherAgentInstance.class)).isInstanceOf(ClassCastException.class);

        assertThat(pipeline.process("dragons")).isEqualTo("a story about dragons");
        assertThat(server.clients).hasValue(1);
        assertThat(server.listings).hasValue(1);
    }

    /** The tokens the supplier below was invoked with. */
    static final List<String> SUPPLIER_TOKENS = new CopyOnWriteArrayList<>();

    public interface StoryAgentWithResolvedSupplierParameter {

        @McpClientAgent(toolName = "writer", outputKey = "story")
        String generateStory(@V("topic") String topic);

        @McpClientSupplier
        static Object mcpClient(String token) {
            SUPPLIER_TOKENS.add(token);
            return server.get();
        }
    }

    @Test
    void the_resolved_parameters_of_the_client_supplier_are_resolved_when_the_agent_is_built() {
        // a resolver providing a per-request value, like a token, to the String parameter of the supplier
        AtomicInteger resolutions = new AtomicInteger();
        DeclarativeUtil.addSupplierParameterResolver(new SupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.supplierMethod().isAnnotationPresent(McpClientSupplier.class)
                        && context.parameter().getType() == String.class;
            }

            @Override
            public Object resolve(Context context) {
                return "token-" + resolutions.incrementAndGet();
            }
        });
        SUPPLIER_TOKENS.clear();
        try {
            StoryAgentWithResolvedSupplierParameter storyAgent = AgenticServices.createAgenticSystem(
                    StoryAgentWithResolvedSupplierParameter.class, (ChatModel) null);
            // the parameter is resolved on the thread building the agent, as for the other supplier methods
            assertThat(resolutions).hasValue(1);
            assertThat(SUPPLIER_TOKENS).isEmpty();
            assertThat(server.clients).hasValue(0);

            // the supplier runs with that value when the client is created on the first invocation
            assertThat(storyAgent.generateStory("dragons")).isEqualTo("a story about dragons");
            assertThat(SUPPLIER_TOKENS).containsExactly("token-1");
            assertThat(resolutions).hasValue(1);
        } finally {
            DeclarativeUtil.getSupplierParameterResolvers().clear();
        }
    }

    @Test
    void the_programmatic_builder_creates_the_client_lazily_when_given_a_supplier() {
        UntypedAgent writer = McpAgent.builder(server)
                .toolName("writer")
                .inputKeys("topic")
                .outputKey("story")
                .build();
        assertThat(server.clients).hasValue(0);

        assertThat(writer.invoke(Map.of("topic", "dragons"))).isEqualTo("a story about dragons");
        assertThat(server.clients).hasValue(1);
    }
}
