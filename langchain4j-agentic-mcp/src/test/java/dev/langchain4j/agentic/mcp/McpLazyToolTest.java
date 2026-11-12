package dev.langchain4j.agentic.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentUnavailableException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.McpClientAgent;
import dev.langchain4j.agentic.declarative.McpClientSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupplierParameterResolver;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The MCP client of an MCP client agent is created and its tool resolved lazily, on the first invocation, so that
 * building an agentic system never depends on the availability of the MCP server (issue #6957). The name of the
 * agent is always known locally, the description and the argument details come from the tool when first needed, a
 * failed resolution is retried, and concurrent first invocations resolve the tool once. The resolution runs on a
 * thread of the LangChain4j executor, within the resolution timeout of the agent when one is set, and the
 * invocations waiting on it all fail when it fails (issue #6958).
 */
@Timeout(60)
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
     * can be told to be down, or slow, or to make the creation of a client or a listing wait on a gate.
     */
    static class McpServer implements Supplier<McpClient> {

        final List<ToolSpecification> tools;
        final String reply;
        final AtomicInteger clients = new AtomicInteger();
        final AtomicInteger listings = new AtomicInteger();
        final CountDownLatch interrupted = new CountDownLatch(1);
        volatile boolean down;
        volatile boolean ignoringInterrupt;
        volatile long delayMillis;
        volatile CountDownLatch creationGate;
        volatile CountDownLatch listingGate;
        volatile Thread supplyingThread;

        McpServer(String reply, ToolSpecification... tools) {
            this.reply = reply;
            this.tools = List.of(tools);
        }

        @Override
        public McpClient get() {
            clients.incrementAndGet();
            supplyingThread = Thread.currentThread();
            awaitGate(creationGate);
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

        private void awaitGate(CountDownLatch gate) {
            while (gate != null) {
                try {
                    gate.await();
                    return;
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    if (!ignoringInterrupt) {
                        // like DefaultMcpClient, which reports the interruption of a request with a RuntimeException
                        throw new IllegalStateException(e);
                    }
                }
            }
        }

        private List<ToolSpecification> listTools() throws InterruptedException {
            listings.incrementAndGet();
            awaitGate(listingGate);
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

    public interface StoryAgentWithWrongClientType {

        @McpClientAgent(toolName = "writer", outputKey = "story")
        String generateStory(@V("topic") String topic);

        @McpClientSupplier
        static String mcpClient() {
            return "not a client";
        }
    }

    public interface StoryAgentWithTypedClient {

        @McpClientAgent(toolName = "writer", outputKey = "story")
        String generateStory(@V("topic") String topic);

        @McpClientSupplier
        static McpClient mcpClient() {
            return server.get();
        }
    }

    public interface ImpatientStoryAgent {

        @McpClientAgent(toolName = "writer", outputKey = "story", resolutionTimeoutSeconds = 1)
        String generateStory(@V("topic") String topic);

        @McpClientSupplier
        static Object mcpClient() {
            return server.get();
        }
    }

    public interface ImpatientPipeline {

        @SequenceAgent(
                outputKey = "story",
                subAgents = {Analyzer.class, ImpatientStoryAgent.class})
        String process(@V("request") String request);
    }

    public interface StoryAgentWithoutTimeout {

        @McpClientAgent(toolName = "writer", outputKey = "story", resolutionTimeoutSeconds = 0)
        String generateStory(@V("topic") String topic);

        @McpClientSupplier
        static Object mcpClient() {
            return server.get();
        }
    }

    public interface StoryAgentWithNegativeTimeout {

        @McpClientAgent(toolName = "writer", outputKey = "story", resolutionTimeoutSeconds = -2)
        String generateStory(@V("topic") String topic);

        @McpClientSupplier
        static Object mcpClient() {
            return server.get();
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

        // an McpToolResolutionException is an AgentUnavailableException, the type common to every integration
        assertThatThrownBy(() -> writer.write("dragons"))
                .isInstanceOf(McpToolResolutionException.class)
                .isInstanceOf(AgentUnavailableException.class)
                .hasMessageContaining("'write'")
                .hasMessageContaining("'writer'")
                .hasCauseInstanceOf(IllegalStateException.class);

        server.down = false;
        assertThat(writer.write("dragons")).isEqualTo("a story about dragons");
        assertThat(writer.write("dragons")).isEqualTo("a story about dragons");
        assertThat(server.clients).hasValue(2);
        assertThat(server.listings).hasValue(1);
    }

    /** Starts the given number of concurrent invocations and returns once they all wait on the same resolution. */
    private static List<Thread> startWaitingInvocations(int count, Writer writer, List<Throwable> failures) {
        List<Thread> callers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Thread caller = new Thread(() -> failures.add(catchThrowable(() -> writer.write("dragons"))));
            caller.start();
            callers.add(caller);
        }
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> callers.stream().allMatch(caller -> caller.getState() == Thread.State.WAITING));
        return callers;
    }

    private static void join(List<Thread> callers) throws InterruptedException {
        for (Thread caller : callers) {
            caller.join(10_000);
        }
    }

    @Test
    void a_client_supplier_that_never_returns_fails_after_the_resolution_timeout() throws Exception {
        server.creationGate = new CountDownLatch(1);
        Writer writer = McpAgent.builder(server, Writer.class)
                .toolName("writer")
                .resolutionTimeout(Duration.ofMillis(200))
                .outputKey("story")
                .build();

        assertThatThrownBy(() -> writer.write("dragons"))
                .isInstanceOf(McpToolResolutionException.class)
                .isInstanceOf(AgentUnavailableException.class)
                .hasMessageEndingWith("'writer' of the MCP client agent 'write' within 200 ms")
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(server.interrupted.await(10, TimeUnit.SECONDS)).isTrue();

        // once the interrupted supplier has ended, the next invocation resolves the tool again
        server.creationGate = null;
        await().atMost(10, TimeUnit.SECONDS)
                .ignoreExceptionsInstanceOf(McpToolResolutionException.class)
                .untilAsserted(() -> assertThat(writer.write("dragons")).isEqualTo("a story about dragons"));
        assertThat(server.clients).hasValue(2);
    }

    @Test
    void an_invocation_arriving_while_a_resolution_that_timed_out_is_still_running_fails_at_once_saying_so()
            throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        server.creationGate = gate;
        server.ignoringInterrupt = true;
        Writer writer = McpAgent.builder(server, Writer.class)
                .toolName("writer")
                .resolutionTimeout(Duration.ofMillis(200))
                .outputKey("story")
                .build();

        assertThatThrownBy(() -> writer.write("dragons"))
                .isInstanceOf(McpToolResolutionException.class)
                .hasMessageEndingWith("'writer' of the MCP client agent 'write' within 200 ms");
        assertThat(server.interrupted.await(10, TimeUnit.SECONDS)).isTrue();

        // the tool is not resolved a second time while the first resolution is running, and the failure does not
        // read as if the server had been given the timeout again
        assertThatThrownBy(() -> writer.write("dragons"))
                .isInstanceOf(McpToolResolutionException.class)
                .hasMessageEndingWith("'writer' of the MCP client agent 'write': the previous resolution is still"
                        + " running after its timeout of 200 ms")
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(server.clients).hasValue(1);

        // the tool that the first resolution finds in the end is kept
        gate.countDown();
        await().atMost(10, TimeUnit.SECONDS)
                .ignoreExceptionsInstanceOf(McpToolResolutionException.class)
                .untilAsserted(() -> assertThat(writer.write("dragons")).isEqualTo("a story about dragons"));
        assertThat(server.clients).hasValue(1);
        assertThat(server.listings).hasValue(1);
    }

    @Test
    void a_listing_of_the_tools_that_never_returns_fails_after_the_resolution_timeout_and_keeps_the_client()
            throws Exception {
        server.listingGate = new CountDownLatch(1);
        Writer writer = McpAgent.builder(server, Writer.class)
                .toolName("writer")
                .resolutionTimeout(Duration.ofMillis(200))
                .outputKey("story")
                .build();

        assertThatThrownBy(() -> writer.write("dragons"))
                .isInstanceOf(McpToolResolutionException.class)
                .hasMessageEndingWith("within 200 ms")
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(server.interrupted.await(10, TimeUnit.SECONDS)).isTrue();

        // the client created before the listing timed out is used again
        server.listingGate = null;
        await().atMost(10, TimeUnit.SECONDS)
                .ignoreExceptionsInstanceOf(McpToolResolutionException.class)
                .untilAsserted(() -> assertThat(writer.write("dragons")).isEqualTo("a story about dragons"));
        assertThat(server.clients).hasValue(1);
        assertThat(server.listings).hasValue(2);
    }

    @Test
    void the_resolution_timeout_stops_a_real_client_detecting_the_protocol_of_a_silent_server() {
        McpTransport silentTransport = mock(McpTransport.class);
        when(silentTransport.sendRequest(any(McpCallContext.class))).thenAnswer(invocation -> new CompletableFuture<>());
        Writer writer = McpAgent.builder(
                        () -> new DefaultMcpClient.Builder()
                                .transport(silentTransport)
                                .initializationTimeout(Duration.ofMinutes(2))
                                .build(),
                        Writer.class)
                .toolName("writer")
                .resolutionTimeout(Duration.ofMillis(200))
                .outputKey("story")
                .build();

        assertThatThrownBy(() -> writer.write("dragons"))
                .isInstanceOf(McpToolResolutionException.class)
                .hasCauseInstanceOf(TimeoutException.class);

        verify(silentTransport, timeout(10_000)).stop();
        verify(silentTransport, never()).sendInitializeRequest(any());
    }

    @Test
    void concurrent_invocations_share_the_failure_of_a_single_resolution() throws Exception {
        server.down = true;
        server.creationGate = new CountDownLatch(1);
        Writer writer = McpAgent.builder(server, Writer.class)
                .toolName("writer")
                .outputKey("story")
                .build();

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<Thread> callers = startWaitingInvocations(8, writer, failures);
        server.creationGate.countDown();
        join(callers);

        assertThat(failures).hasSize(8).doesNotHaveDuplicates().allSatisfy(failure -> assertThat(failure)
                .isInstanceOf(McpToolResolutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class));
        assertThat(failures.stream().map(Throwable::getCause).distinct()).hasSize(1);
        assertThat(server.clients).hasValue(1);

        // the failure is not cached: the next invocation resolves the tool again
        server.down = false;
        assertThat(writer.write("dragons")).isEqualTo("a story about dragons");
        assertThat(server.clients).hasValue(2);
    }

    @Test
    void concurrent_invocations_share_a_configuration_error() throws Exception {
        server.listingGate = new CountDownLatch(1);
        Writer writer = McpAgent.builder(server, Writer.class)
                .toolName("poet")
                .outputKey("story")
                .build();

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<Thread> callers = startWaitingInvocations(8, writer, failures);
        server.listingGate.countDown();
        join(callers);

        assertThat(failures).hasSize(8).allSatisfy(failure -> assertThat(failure)
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("'poet' not found"));
        assertThat(failures.stream().distinct()).hasSize(1);
        assertThat(server.listings).hasValue(1);
    }

    @Test
    void the_client_supplier_runs_on_the_invoking_thread_unless_a_resolution_timeout_is_set() {
        Writer writer = McpAgent.builder(server, Writer.class)
                .toolName("writer")
                .outputKey("story")
                .build();

        assertThat(writer.write("dragons")).isEqualTo("a story about dragons");

        assertThat(server.supplyingThread).isSameAs(Thread.currentThread());

        // with a timeout the resolution must be abandoned at its deadline, so it runs on a thread of the executor
        Writer limitedWriter = McpAgent.builder(server, Writer.class)
                .toolName("writer")
                .resolutionTimeout(Duration.ofSeconds(10))
                .outputKey("story")
                .build();

        assertThat(limitedWriter.write("dragons")).isEqualTo("a story about dragons");

        assertThat(server.supplyingThread).isNotNull().isNotSameAs(Thread.currentThread());
    }

    @Test
    void the_resolution_timeout_of_the_annotation_is_applied() {
        server.creationGate = new CountDownLatch(1);
        ImpatientPipeline pipeline = AgenticServices.createAgenticSystem(ImpatientPipeline.class, (ChatModel) null);

        Throwable failure = catchThrowable(() -> pipeline.process("dragons"));

        assertThat(causeOfType(failure, McpToolResolutionException.class))
                .hasMessageContaining("'generateStory'")
                .hasMessageEndingWith("within 1 s")
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void the_resolution_timeout_cannot_be_negative() {
        var builder = McpAgent.builder(server, Writer.class);

        assertThatThrownBy(() -> builder.resolutionTimeout(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
        assertThat(builder.resolutionTimeout(null)).isSameAs(builder);
        assertThat(builder.resolutionTimeout(Duration.ZERO)).isSameAs(builder);
        assertThatThrownBy(() ->
                        AgenticServices.createAgenticSystem(StoryAgentWithNegativeTimeout.class, (ChatModel) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void a_zero_resolution_timeout_removes_the_limit() {
        server.delayMillis = 300;

        // no deadline at all, and not one that has already passed, on the builder and on the annotation
        Writer writer = McpAgent.builder(server, Writer.class)
                .toolName("writer")
                .resolutionTimeout(Duration.ZERO)
                .outputKey("story")
                .build();
        assertThat(writer.write("dragons")).isEqualTo("a story about dragons");

        StoryAgentWithoutTimeout declared =
                AgenticServices.createAgenticSystem(StoryAgentWithoutTimeout.class, (ChatModel) null);
        assertThat(declared.generateStory("dragons")).isEqualTo("a story about dragons");
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
    void at_most_one_mcp_agent_of_a_system_can_be_named_with_the_placeholder() {
        // two agents with neither a name nor a tool name could not be told apart, in the same workflow...
        Throwable siblings = catchThrowable(() -> AgenticServices.sequenceBuilder()
                .subAgents(namelessWriter(), namelessWriter())
                .build());
        assertThat(siblings)
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("more than one MCP client agent named with the placeholder 'mcp-agent'")
                .hasMessageContaining("McpClientBuilder.name(...) or toolName(...)");

        // ...or in different branches of the same system, where only their ids would differ
        Throwable nested = catchThrowable(() -> AgenticServices.sequenceBuilder()
                .subAgents(
                        AgenticServices.parallelBuilder().subAgents(namelessWriter()).build(),
                        namelessWriter())
                .build());
        assertThat(nested).isInstanceOf(AgenticSystemConfigurationException.class);

        // one agent named with the placeholder next to named ones is fine, and the check never contacts the server
        UntypedAgent pipeline = AgenticServices.sequenceBuilder()
                .subAgents(
                        namelessWriter(),
                        McpAgent.builder(server).name("rewriter").inputKeys("story").outputKey("story").build())
                .outputKey("story")
                .build();
        assertThat(((AgentInstance) pipeline).subagents())
                .extracting(AgentInstance::name)
                .containsExactly("mcp-agent", "rewriter");
        assertThat(server.clients).hasValue(0);
    }

    private static UntypedAgent namelessWriter() {
        return McpAgent.builder(server).inputKeys("topic").outputKey("story").build();
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

        // an untyped agent without input keys could not report any argument until the tool is resolved, so it is
        // rejected when built, since some planners read the arguments at invocation without resolving the tool
        assertThatThrownBy(() -> McpAgent.builder(server).toolName("writer").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("untyped MCP client agent")
                .hasMessageContaining("inputKeys");

        // a typed agent reports the arguments of its method, with the schema descriptions once resolved
        Writer writer = McpAgent.builder(server, Writer.class).toolName("writer").build();
        McpClientInstance writerInstance = (McpClientInstance) writer;
        assertThat(writerInstance.arguments()).extracting(AgentArgument::name).containsExactly("topic");
        assertThat(writerInstance.arguments().get(0).type()).isEqualTo(String.class);
        assertThat(writerInstance.arguments().get(0).description()).isNullOrEmpty();
        assertThat(server.clients).hasValue(1);
        writerInstance.toolSpecification();
        assertThat(writerInstance.arguments().get(0).description()).isEqualTo("The topic");
        assertThat(server.clients).hasValue(2);
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
        assertThat(hasCauseOfType(failure, McpToolResolutionException.class)).isFalse();
    }

    @Test
    void a_supplier_returning_something_else_than_a_client_is_a_configuration_error() {
        // the supplier is declared to return Object, so the instance is only checked when the client is created
        StoryAgentWithWrongClient storyAgent =
                AgenticServices.createAgenticSystem(StoryAgentWithWrongClient.class, (ChatModel) null);

        Throwable failure = catchThrowable(() -> storyAgent.generateStory("dragons"));

        assertThat(causeOfType(failure, AgenticSystemConfigurationException.class))
                .hasMessageContaining(StoryAgentWithWrongClient.class.getName())
                .hasMessageContaining("java.lang.String")
                .hasMessageContaining("McpClient");
        assertThat(hasCauseOfType(failure, McpToolResolutionException.class)).isFalse();
    }

    @Test
    void a_supplier_declared_with_a_type_that_cannot_be_a_client_fails_when_the_agent_is_built() {
        Throwable failure = catchThrowable(
                () -> AgenticServices.createAgenticSystem(StoryAgentWithWrongClientType.class, (ChatModel) null));

        assertThat(failure)
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining(StoryAgentWithWrongClientType.class.getName())
                .hasMessageContaining("java.lang.String")
                .hasMessageContaining("McpClient");

        // a supplier declared to return an McpClient passes the check at build and still runs at the first invocation
        StoryAgentWithTypedClient storyAgent =
                AgenticServices.createAgenticSystem(StoryAgentWithTypedClient.class, (ChatModel) null);
        assertThat(server.clients).hasValue(0);
        assertThat(storyAgent.generateStory("dragons")).isEqualTo("a story about dragons");
        assertThat(server.clients).hasValue(1);
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

    @Test
    void a_supervisor_does_not_hide_a_configuration_error_of_a_subagent_behind_a_warning() {
        // the tool name has a typo: a configuration error, not an unavailable server, found when the supervisor
        // reads the description of the agent
        Writer writer = McpAgent.builder(server, Writer.class)
                .toolName("writr")
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

        Throwable failure = catchThrowable(() -> supervisor.invoke("write a story"));
        assertThat(causeOfType(failure, AgenticSystemConfigurationException.class))
                .hasMessageContaining("'writr' not found")
                .hasMessageContaining("writer");
        assertThat(hasCauseOfType(failure, AgentUnavailableException.class)).isFalse();
        assertThat(plannerModel.requests()).isEmpty();
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

    @Test
    void an_error_handler_can_skip_an_untyped_agent_whose_mcp_server_is_down() {
        server.down = true;
        List<AgentInvocationError> reportedErrors = new CopyOnWriteArrayList<>();
        UntypedAgent storyAgent = McpAgent.builder(server)
                .toolName("writer")
                .inputKeys("topic")
                .outputKey("story")
                .listener(new AgentListener() {
                    @Override
                    public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
                        reportedErrors.add(agentInvocationError);
                    }
                })
                .build();
        AgenticScopeFunction<String> analyzer = AgenticServices.nonAiAgentBuilder(
                        scope -> "topic of " + scope.readState("request", ""))
                .name("analyzer")
                .description("Analyzes the request")
                .outputKey("topic")
                .build();
        List<String> failedAgents = new CopyOnWriteArrayList<>();
        UntypedAgent pipeline = AgenticServices.sequenceBuilder()
                .subAgents(analyzer, storyAgent)
                .outputKey("story")
                .errorHandler(errorContext -> {
                    failedAgents.add(errorContext.agentName());
                    return hasCauseOfType(errorContext.exception(), McpToolResolutionException.class)
                            ? ErrorRecoveryResult.result("no story available, the MCP server is down")
                            : ErrorRecoveryResult.throwException();
                })
                .build();

        assertThat(pipeline.invoke(Map.of("request", "dragons")))
                .isEqualTo("no story available, the MCP server is down");
        assertThat(failedAgents).containsExactly("writer");
        assertThat(reportedErrors).hasSize(1);
        assertThat(hasCauseOfType(reportedErrors.get(0).error(), McpToolResolutionException.class))
                .isTrue();
    }

    @Test
    void a_failed_listing_of_the_tools_of_a_given_client_is_retried_with_the_same_client() {
        McpClient client = mock(McpClient.class);
        when(client.listTools())
                .thenThrow(new IllegalStateException("connection lost"))
                .thenReturn(List.of(WRITER_TOOL));
        when(client.executeTool(any()))
                .thenReturn(ToolExecutionResult.builder()
                        .resultText("a story about dragons")
                        .isError(false)
                        .build());
        Writer writer = McpAgent.builder(client, Writer.class)
                .toolName("writer")
                .outputKey("story")
                .build();

        assertThatThrownBy(() -> writer.write("dragons"))
                .isInstanceOf(McpToolResolutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(writer.write("dragons")).isEqualTo("a story about dragons");
        verify(client, times(2)).listTools();
    }

    @Test
    void a_missing_tool_name_with_several_tools_on_the_server_fails_at_the_first_invocation() {
        McpServer serverWithTwoTools =
                new McpServer("a story about dragons", WRITER_TOOL, tool("poet", "Writes a poem", "topic"));
        Writer writer = McpAgent.builder(serverWithTwoTools, Writer.class)
                .outputKey("story")
                .build();
        assertThat(serverWithTwoTools.clients).hasValue(0);

        assertThatThrownBy(() -> ((McpClientInstance) writer).toolSpecification())
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("Tool name is required")
                .hasMessageContaining("writer")
                .hasMessageContaining("poet");
        assertThatThrownBy(() -> writer.write("dragons")).isInstanceOf(AgenticSystemConfigurationException.class);
    }

    @Test
    void a_supplier_returning_null_is_a_configuration_error() {
        Writer writer = McpAgent.builder(() -> null, Writer.class)
                .toolName("writer")
                .outputKey("story")
                .build();

        assertThatThrownBy(() -> writer.write("dragons"))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("returned null");
    }

    @Test
    void a_null_client_or_supplier_fails_when_the_builder_is_created() {
        assertThatThrownBy(() -> McpAgent.builder((McpClient) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mcpClient");
        assertThatThrownBy(() -> McpAgent.builder((Supplier<McpClient>) null, Writer.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mcpClientSupplier");
    }
}
