package dev.langchain4j.agentic.micrometer;

import static dev.langchain4j.test.mock.MockChatModel.toolCall;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockDecisionModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MicrometerMetricsAgentListenerTest {

    public interface Writer {

        @UserMessage("Write about {{topic}}")
        @Agent(outputKey = "story")
        String write(@V("topic") String topic);
    }

    public interface StreamingStory {

        @Agent
        TokenStream write(@V("topic") String topic);
    }

    public interface Echo {

        @UserMessage("{{text}}")
        @Agent
        String echo(@V("text") String text);
    }

    public interface StreamingWriter {

        @UserMessage("Write about {{topic}}")
        @Agent(outputKey = "story")
        TokenStream write(@V("topic") String topic);
    }

    public interface ReaderWriter {

        @UserMessage("Write about {{topic}} for {{reader}}")
        @Agent(outputKey = "story")
        String write(@V("topic") String topic);
    }

    public interface UrgencyCheck {

        @Decide("Is this request urgent?")
        @Agent(outputKey = "urgent")
        boolean urgent(@V("request") String request);
    }

    public static class Facts {

        @Tool
        public String lookup() {
            return "facts";
        }
    }

    public static class FailingTools {

        @Tool
        public String fetch() {
            throw new IllegalStateException("fetch failed");
        }

        @Tool
        public String parse() {
            throw new IllegalArgumentException("parse failed");
        }
    }

    public static class BrokenFacts {

        @Tool
        public String lookup() {
            throw new IllegalStateException("no facts");
        }
    }

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerMetricsAgentListener listener = new MicrometerMetricsAgentListener(registry);

    @Test
    void records_the_workflow_the_agent_its_model_and_tool_calls_and_the_tool() {
        UntypedAgent novel = sequence("novel", writerCallingTool(new Facts()));

        assertThat(novel.invoke(Map.of("topic", "dragons"))).isEqualTo("a story");

        Timer workflowDuration = workflow("novel");
        assertThat(workflowDuration.count()).isEqualTo(1);
        assertThat(workflowDuration.getId().getTag("outcome")).isEqualTo("SUCCESS");
        Timer agentDuration = agent("writer");
        assertThat(agentDuration.count()).isEqualTo(1);
        assertThat(agentDuration.getId().getTag("outcome")).isEqualTo("SUCCESS");
        assertThat(agentDuration.getId().getTag("error.type")).isEqualTo("none");
        assertThat(summary("gen_ai.invoke_agent.inference_calls", "writer").totalAmount())
                .isEqualTo(2);
        assertThat(summary("gen_ai.invoke_agent.tool_calls", "writer").totalAmount())
                .isEqualTo(1);
        Timer toolDuration = tool("lookup");
        assertThat(toolDuration.count()).isEqualTo(1);
        assertThat(toolDuration.getId().getTag("gen_ai.agent.name")).isEqualTo("writer");
        assertThat(toolDuration.getId().getTag("gen_ai.tool.type")).isEqualTo("function");
        assertThat(toolDuration.getId().getTag("outcome")).isEqualTo("SUCCESS");
        assertThat(invocations("writer", "novel").count()).isEqualTo(1);
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void every_meter_carries_its_full_set_of_tags() {
        sequence("novel", writerCallingTool(new Facts())).invoke(Map.of("topic", "dragons"));

        assertThat(tagKeys("gen_ai.invoke_agent.duration"))
                .containsExactlyInAnyOrder("gen_ai.operation.name", "gen_ai.agent.name", "outcome", "error.type");
        assertThat(tagKeys("gen_ai.invoke_agent.inference_calls"))
                .containsExactlyInAnyOrder("gen_ai.operation.name", "gen_ai.agent.name");
        assertThat(tagKeys("gen_ai.invoke_agent.tool_calls"))
                .containsExactlyInAnyOrder("gen_ai.operation.name", "gen_ai.agent.name");
        assertThat(tagKeys("gen_ai.execute_tool.duration"))
                .containsExactlyInAnyOrder(
                        "gen_ai.tool.name", "gen_ai.tool.type", "gen_ai.agent.name", "outcome", "error.type");
        assertThat(tagKeys("langchain4j.agent.invocations"))
                .containsExactlyInAnyOrder("gen_ai.agent.name", "langchain4j.agent.parent.name", "outcome");
        assertThat(tagKeys("langchain4j.agent.invocations.active"))
                .containsExactlyInAnyOrder("gen_ai.operation.name", "gen_ai.agent.name");
    }

    @Test
    void meters_of_the_same_name_carry_the_same_tags_whatever_the_outcome() {
        invokeWithEveryOutcome();

        Map<String, Set<Set<String>>> tagKeysByName = registry.getMeters().stream()
                .collect(Collectors.groupingBy(
                        meter -> meter.getId().getName(),
                        Collectors.mapping(MicrometerMetricsAgentListenerTest::keys, Collectors.toSet())));
        assertThat(tagKeysByName)
                .isNotEmpty()
                .allSatisfy((name, tagKeys) -> assertThat(tagKeys).as(name).hasSize(1));
        assertThat(registry.find("gen_ai.invoke_agent.duration").timers())
                .extracting(timer -> timer.getId().getTag("outcome"))
                .contains("SUCCESS", "ERROR", "CANCELLED", "SUSPENDED");
    }

    @Test
    void no_invocation_stays_active_whatever_its_outcome() {
        invokeWithEveryOutcome();

        assertThat(registry.find("langchain4j.agent.invocations.active").longTaskTimers())
                .isNotEmpty()
                .allSatisfy(active -> assertThat(active.activeTasks())
                        .as(active.getId().toString())
                        .isZero());
    }

    public static class SlowStep {

        static final CountDownLatch STARTED = new CountDownLatch(1);
        static final CountDownLatch RELEASED = new CountDownLatch(1);

        @Agent(outputKey = "step")
        public String step() throws InterruptedException {
            STARTED.countDown();
            RELEASED.await(10, SECONDS);
            return "done";
        }
    }

    @Test
    void counts_the_invocations_in_progress_until_their_durations_are_recorded() throws Exception {
        CompletableFuture<Object> watch = CompletableFuture.supplyAsync(
                () -> sequence("watch", new SlowStep()).invoke(Map.of()));
        assertThat(SlowStep.STARTED.await(10, SECONDS)).isTrue();

        assertThat(active("invoke_workflow", "watch").activeTasks()).isEqualTo(1);
        assertThat(active("invoke_agent", "step").activeTasks()).isEqualTo(1);
        assertThat(registry.find("gen_ai.invoke_agent.duration").timers()).isEmpty();
        SlowStep.RELEASED.countDown();
        watch.get(10, SECONDS);

        assertThat(active("invoke_workflow", "watch").activeTasks()).isZero();
        assertThat(active("invoke_agent", "step").activeTasks()).isZero();
        assertThat(workflow("watch").count()).isEqualTo(1);
    }

    @Test
    void adds_the_provided_tags_to_every_meter() {
        MicrometerMetricsAgentListener tenantListener = MicrometerMetricsAgentListener.builder()
                .meterRegistry(registry)
                .tags(tenantTags())
                .build();

        sequence(tenantListener, "novel", writerCallingTool(new Facts()), summarizer())
                .invoke(Map.of("topic", "dragons", "tenant", "acme"));
        assertThatThrownBy(() -> sequence(tenantListener, "novel", writerFailingOnTool())
                .invoke(Map.of("topic", "dragons", "tenant", "acme")));

        assertThat(registry.getMeters())
                .extracting(meter -> meter.getId().getName())
                .contains(
                        "gen_ai.invoke_agent.duration",
                        "gen_ai.invoke_agent.inference_calls",
                        "gen_ai.invoke_agent.tool_calls",
                        "gen_ai.execute_tool.duration",
                        "langchain4j.agent.token.usage",
                        "langchain4j.agent.invocations",
                        "langchain4j.agent.invocations.active");
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTag("tenant"))
                        .as(meter.getId().toString())
                        .isEqualTo("acme"));
        assertThat(registry.get("gen_ai.execute_tool.duration")
                        .tag("error.type", "none")
                        .timer()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("gen_ai.execute_tool.duration")
                        .tag("error.type", "java.lang.IllegalStateException")
                        .timer()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void records_the_invocations_of_each_provided_tag_value_apart() {
        MicrometerMetricsAgentListener tenantListener = MicrometerMetricsAgentListener.builder()
                .meterRegistry(registry)
                .tags(tenantTags())
                .build();
        UntypedAgent novel = sequence(tenantListener, "novel", writerCallingTool(new Facts()));

        novel.invoke(Map.of("topic", "dragons", "tenant", "acme"));
        novel.invoke(Map.of("topic", "dragons", "tenant", "globex"));
        novel.invoke(Map.of("topic", "dragons", "tenant", "globex"));

        assertThat(workflow("novel", "acme").count()).isEqualTo(1);
        assertThat(workflow("novel", "globex").count()).isEqualTo(2);
    }

    @Test
    void the_tags_of_the_listener_take_precedence_over_provided_tags() {
        MicrometerMetricsAgentListener clashingListener = MicrometerMetricsAgentListener.builder()
                .meterRegistry(registry)
                .tags(agenticScope -> Tags.of(
                        "outcome", "provided",
                        "gen_ai.agent.name", "provided",
                        "gen_ai.operation.name", "provided",
                        "gen_ai.tool.type", "provided"))
                .build();

        sequence(clashingListener, "novel", writerCallingTool(new Facts())).invoke(Map.of("topic", "dragons"));

        assertThat(agent("writer").getId().getTag("outcome")).isEqualTo("SUCCESS");
        assertThat(workflow("novel").getId().getTag("outcome")).isEqualTo("SUCCESS");
        assertThat(tool("lookup").getId().getTag("gen_ai.agent.name")).isEqualTo("writer");
        assertThat(tool("lookup").getId().getTag("gen_ai.tool.type")).isEqualTo("function");
        assertThat(registry.get("gen_ai.invoke_agent.inference_calls")
                        .summary()
                        .getId()
                        .getTag("gen_ai.operation.name"))
                .isEqualTo("invoke_agent");
        assertThat(registry.get("gen_ai.invoke_agent.tool_calls")
                        .summary()
                        .getId()
                        .getTag("gen_ai.operation.name"))
                .isEqualTo("invoke_agent");
    }

    @Test
    void a_failing_tags_provider_does_not_fail_the_agents() {
        MicrometerMetricsAgentListener failingListener = MicrometerMetricsAgentListener.builder()
                .meterRegistry(registry)
                .tags(agenticScope -> {
                    throw new IllegalStateException("no tenant");
                })
                .build();

        assertThat(sequence(failingListener, "novel", writerCallingTool(new Facts()))
                        .invoke(Map.of("topic", "dragons")))
                .isEqualTo("a story");
        assertThat(registry.getMeters()).isEmpty();
        assertThat(failingListener.pendingInvocations()).isZero();
    }

    @Test
    void requires_a_meter_registry() {
        assertThatThrownBy(() -> new MicrometerMetricsAgentListener(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("meterRegistry cannot be null");
        assertThatThrownBy(() -> MicrometerMetricsAgentListener.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("meterRegistry cannot be null");
    }

    @Test
    void records_a_failed_agent_and_its_workflow_with_the_type_of_the_exception_that_caused_the_failure() {
        assertThatThrownBy(() -> sequence("novel", failingWriter()).invoke(Map.of("topic", "dragons")));

        Timer agentDuration = agent("writer");
        assertThat(agentDuration.count()).isEqualTo(1);
        assertThat(agentDuration.getId().getTag("outcome")).isEqualTo("ERROR");
        assertThat(agentDuration.getId().getTag("error.type")).isEqualTo("java.lang.IllegalStateException");
        Timer workflowDuration = workflow("novel");
        assertThat(workflowDuration.count()).isEqualTo(1);
        assertThat(workflowDuration.getId().getTag("outcome")).isEqualTo("ERROR");
        assertThat(workflowDuration.getId().getTag("error.type")).isEqualTo("java.lang.IllegalStateException");
        assertThat(summary("gen_ai.invoke_agent.inference_calls", "writer").totalAmount())
                .isEqualTo(1);
        assertThat(failedInvocations("writer", "novel").count()).isEqualTo(1);
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void records_an_llm_based_agent_failing_before_its_ai_service_starts() {
        ReaderWriter writer = AgenticServices.agentBuilder(ReaderWriter.class)
                .name("writer")
                .chatModel(MockChatModel.thatResponds("a story"))
                .build();

        assertThatThrownBy(() -> sequence("novel", writer).invoke(Map.of("topic", "dragons")));

        Timer agentDuration = agent("writer");
        assertThat(agentDuration.count()).isEqualTo(1);
        assertThat(agentDuration.getId().getTag("outcome")).isEqualTo("ERROR");
        assertThat(agentDuration.getId().getTag("error.type")).isEqualTo("java.lang.IllegalArgumentException");
        assertThat(registry.find("gen_ai.invoke_agent.inference_calls").summaries())
                .isEmpty();
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void records_a_tool_execution_whose_failure_is_sent_to_the_model() {
        sequence("novel", writerCallingTool(new BrokenFacts())).invoke(Map.of("topic", "dragons"));

        Timer toolDuration = tool("lookup");
        assertThat(toolDuration.count()).isEqualTo(1);
        assertThat(toolDuration.getId().getTag("outcome")).isEqualTo("ERROR");
        assertThat(toolDuration.getId().getTag("error.type")).isEqualTo("_OTHER");
        assertThat(agent("writer").getId().getTag("outcome")).isEqualTo("SUCCESS");
    }

    @Test
    void records_a_tool_execution_whose_failure_fails_the_agent() {
        assertThatThrownBy(() -> sequence("novel", writerFailingOnTool()).invoke(Map.of("topic", "dragons")));

        Timer toolDuration = tool("lookup");
        assertThat(toolDuration.count()).isEqualTo(1);
        assertThat(toolDuration.getId().getTag("outcome")).isEqualTo("ERROR");
        assertThat(toolDuration.getId().getTag("error.type")).isEqualTo("java.lang.IllegalStateException");
        assertThat(agent("writer").getId().getTag("outcome")).isEqualTo("ERROR");
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void reports_the_tools_a_model_invents_under_one_name() {
        for (int i = 0; i < 10; i++) {
            Writer writer = AgenticServices.agentBuilder(Writer.class)
                    .name("writer")
                    .chatModel(MockChatModel.thatResponds(
                            AiMessage.from(toolCall("invented" + i, Map.of())), AiMessage.from("a story")))
                    .tools(new Facts())
                    .build();
            assertThatThrownBy(() -> sequence("novel", writer).invoke(Map.of("topic", "dragons")));
        }
        sequence("novel", writerCallingTool(new Facts())).invoke(Map.of("topic", "dragons"));

        assertThat(registry.find("gen_ai.execute_tool.duration").timers())
                .extracting(timer -> timer.getId().getTag("gen_ai.tool.name"), Timer::count)
                .containsExactlyInAnyOrder(tuple("_OTHER", 10L), tuple("lookup", 1L));
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void records_concurrent_tool_executions_failing_the_agent_without_the_exception_of_another() {
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .name("writer")
                .chatModel(MockChatModel.thatResponds(
                        AiMessage.from(toolCall("fetch", Map.of()), toolCall("parse", Map.of())),
                        AiMessage.from("a story")))
                .tools(new FailingTools())
                .executeToolsConcurrently()
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                .build();

        assertThatThrownBy(() -> sequence("novel", writer).invoke(Map.of("topic", "dragons")));

        assertThat(registry.find("gen_ai.execute_tool.duration").timers())
                .extracting(
                        timer -> timer.getId().getTag("gen_ai.tool.name"),
                        timer -> timer.getId().getTag("outcome"),
                        timer -> timer.getId().getTag("error.type"))
                .containsExactlyInAnyOrder(tuple("fetch", "ERROR", "_OTHER"), tuple("parse", "ERROR", "_OTHER"));
        assertThat(agent("writer").getId().getTag("error.type"))
                .isIn("java.lang.IllegalStateException", "java.lang.IllegalArgumentException");
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void reports_the_items_of_a_parallel_mapper_under_the_name_of_its_subagent() {
        Echo echo = AgenticServices.agentBuilder(Echo.class)
                .name("echo")
                .chatModel(MockChatModel.thatResponds("echoed").thenRepeats())
                .build();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            AgenticServices.parallelMapperBuilder()
                    .name("mapper")
                    .subAgents(echo)
                    .itemsProvider("texts")
                    .executor(executor)
                    .outputKey("echoes")
                    .listener(listener)
                    .build()
                    .invoke(Map.of("texts", List.of("a", "b", "c", "d")));
        } finally {
            executor.shutdown();
        }

        assertThat(workflow("mapper").count()).isEqualTo(1);
        assertThat(agentTimers())
                .extracting(timer -> timer.getId().getTag("gen_ai.agent.name"))
                .containsExactly("echo");
        assertThat(agent("echo").count()).isEqualTo(4);
        assertThat(summary("gen_ai.invoke_agent.inference_calls", "echo").count())
                .isEqualTo(4);
        assertThat(invocations("echo", "mapper").count()).isEqualTo(4);
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void records_each_failed_item_of_a_parallel_mapper_once() {
        Echo echo = AgenticServices.agentBuilder(Echo.class)
                .name("echo")
                .chatModel(MockChatModel.thatFails(new IllegalStateException("model down"))
                        .thenRepeats())
                .build();
        UntypedAgent mapper = AgenticServices.parallelMapperBuilder()
                .name("mapper")
                .subAgents(echo)
                .itemsProvider("texts")
                .outputKey("echoes")
                .listener(listener)
                .build();

        assertThatThrownBy(() -> mapper.invoke(Map.of("texts", List.of("a", "b"))));

        Timer agentDuration = agent("echo");
        assertThat(agentDuration.getId().getTag("outcome")).isEqualTo("ERROR");
        assertThat(agentDuration.count()).isEqualTo(2);
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void reports_the_items_of_a_parallel_mapper_of_a_non_ai_agent_under_its_name() {
        AgenticServices.parallelMapperBuilder()
                .name("mapper")
                .subAgents(AgenticServices.agentAction(agenticScope -> {}))
                .itemsProvider("texts")
                .outputKey("echoes")
                .listener(listener)
                .build()
                .invoke(Map.of("texts", List.of("a", "b", "c")));

        assertThat(agentTimers()).extracting(Timer::count).containsExactly(3L);
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void records_each_iteration_of_a_loop() {
        AgenticServices.loopBuilder()
                .name("loop")
                .subAgents(writerCallingTool(new Facts()))
                .maxIterations(3)
                .outputKey("story")
                .listener(listener)
                .build()
                .invoke(Map.of("topic", "dragons"));

        assertThat(agent("writer").count()).isEqualTo(3);
        assertThat(workflow("loop").count()).isEqualTo(1);
        assertThat(invocations("writer", "loop").count()).isEqualTo(3);
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void records_nested_workflows_each_under_its_own_name() {
        UntypedAgent chapter = AgenticServices.sequenceBuilder()
                .name("chapter")
                .subAgents(writerCallingTool(new Facts()))
                .outputKey("story")
                .build();

        sequence("book", chapter).invoke(Map.of("topic", "dragons"));

        assertThat(workflow("book").count()).isEqualTo(1);
        assertThat(workflow("chapter").count()).isEqualTo(1);
        assertThat(agent("writer").count()).isEqualTo(1);
        assertThat(invocations("chapter", "book").count()).isEqualTo(1);
        assertThat(invocations("writer", "chapter").count()).isEqualTo(1);
        assertThat(registry.find("langchain4j.agent.invocations")
                        .tag("gen_ai.agent.name", "book")
                        .counters())
                .isEmpty();
    }

    @Test
    void records_an_asynchronous_agent() {
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .name("writer")
                .chatModel(MockChatModel.thatResponds("a story"))
                .async(true)
                .build();

        assertThat(sequence("novel", writer).invoke(Map.of("topic", "dragons"))).isEqualTo("a story");

        assertThat(agent("writer").count()).isEqualTo(1);
        assertThat(summary("gen_ai.invoke_agent.inference_calls", "writer").totalAmount())
                .isEqualTo(1);
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void records_nothing_for_a_skipped_agent() {
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .name("writer")
                .chatModel(MockChatModel.thatResponds("a story"))
                .activationCondition("never", agenticScope -> false)
                .build();

        sequence("novel", writer).invoke(Map.of("topic", "dragons"));

        assertThat(agentTimers()).isEmpty();
        assertThat(workflow("novel").count()).isEqualTo(1);
        assertThat(registry.find("langchain4j.agent.invocations").counters()).isEmpty();
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void records_a_workflow_suspended_by_a_human_in_the_loop_as_suspended() {
        UntypedAgent review = sequence("review", writerCallingTool(new Facts()), approval());

        assertThatThrownBy(() -> review.invoke(Map.of("topic", "dragons")))
                .isInstanceOf(AgenticSystemSuspendedException.class);

        Timer workflowDuration = workflow("review");
        assertThat(workflowDuration.count()).isEqualTo(1);
        assertThat(workflowDuration.getId().getTag("outcome")).isEqualTo("SUSPENDED");
        assertThat(workflowDuration.getId().getTag("error.type")).isEqualTo("none");
        assertThat(agent("writer").count()).isEqualTo(1);
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void times_a_streaming_agent_until_its_stream_completes() throws Exception {
        StreamingWriter writer = AgenticServices.agentBuilder(StreamingWriter.class)
                .name("writer")
                .streamingChatModel(MockStreamingChatModel.thatStreams("a", "story"))
                .listener(listener)
                .build();

        TokenStream stream = writer.write("dragons");

        assertThat(agentTimers()).isEmpty();
        assertThat(active("invoke_agent", "writer").activeTasks()).isEqualTo(1);
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        stream.onPartialResponse(partial -> {})
                .onCompleteResponse(response::complete)
                .onError(response::completeExceptionally)
                .start();
        response.get(10, SECONDS);
        await().atMost(10, SECONDS).until(() -> listener.pendingInvocations() == 0);
        assertThat(agent("writer").count()).isEqualTo(1);
        assertThat(active("invoke_agent", "writer").activeTasks()).isZero();
        assertThat(summary("gen_ai.invoke_agent.inference_calls", "writer").totalAmount())
                .isEqualTo(1);
    }

    @Test
    void records_a_cancelled_streaming_agent() {
        StreamingWriter writer = AgenticServices.agentBuilder(StreamingWriter.class)
                .name("writer")
                .streamingChatModel(MockStreamingChatModel.thatStreams("a", "b", "c"))
                .listener(listener)
                .build();

        writer.write("dragons")
                .onPartialResponseWithContext(
                        (partial, context) -> context.streamingHandle().cancel())
                .onError(error -> {})
                .start();

        await().atMost(10, SECONDS)
                .untilAsserted(() -> assertThat(registry.find("gen_ai.invoke_agent.duration")
                                .tag("gen_ai.agent.name", "writer")
                                .tag("outcome", "CANCELLED")
                                .timers())
                        .hasSize(1));
        assertThat(listener.pendingInvocations()).isZero();
    }

    public static class DetachedFix {

        static final CountDownLatch STARTED = new CountDownLatch(1);
        static final CountDownLatch RELEASED = new CountDownLatch(1);

        @Agent(async = true, outputKey = "fix")
        public String fix() throws InterruptedException {
            STARTED.countDown();
            RELEASED.await(10, SECONDS);
            return "fixed";
        }
    }

    public static class Analysis {

        @Agent(outputKey = "analysis")
        public String analyse() throws InterruptedException {
            DetachedFix.STARTED.await(10, SECONDS);
            return "analysis";
        }
    }

    @Test
    void records_a_detached_agent_that_completes_after_the_root_call_and_its_scope_ended() {
        UntypedAgent rollout = AgenticServices.sequenceBuilder()
                .name("rollout")
                .subAgents(new DetachedFix(), new Analysis())
                .outputKey("analysis")
                .listener(listener)
                .build();

        assertThat(rollout.invoke(Map.of())).isEqualTo("analysis");
        assertThat(registry.find("gen_ai.invoke_agent.duration")
                        .tag("gen_ai.agent.name", "fix")
                        .timer())
                .isNull();
        DetachedFix.RELEASED.countDown();

        await().atMost(10, SECONDS)
                .untilAsserted(() -> assertThat(agent("fix").count()).isEqualTo(1));
        assertThat(workflow("rollout").count()).isEqualTo(1);
        assertThat(listener.pendingInvocations()).isZero();
    }

    public static class SlowCheck {

        static final CountDownLatch STARTED = new CountDownLatch(3);
        static final CountDownLatch RELEASED = new CountDownLatch(1);

        @Agent(async = true, outputKey = "check")
        public String check() throws InterruptedException {
            STARTED.countDown();
            RELEASED.await(10, SECONDS);
            return "checked";
        }
    }

    @Test
    void records_each_of_overlapping_invocations_of_the_same_agent() throws InterruptedException {
        AgenticServices.loopBuilder()
                .name("checks")
                .subAgents(new SlowCheck())
                .maxIterations(3)
                .listener(listener)
                .build()
                .invoke(Map.of());

        assertThat(SlowCheck.STARTED.await(10, SECONDS)).isTrue();
        SlowCheck.RELEASED.countDown();

        await().atMost(10, SECONDS)
                .untilAsserted(() -> assertThat(agent("check").count()).isEqualTo(3));
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void records_a_supervisor_the_agents_it_invokes_and_the_tokens_of_its_planning() {
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .name("supervisor")
                .chatModel(MockChatModel.thatResponds(planning("""
                                {"agentName": "writer", "arguments": {"topic": "dragons"}}
                                """, new TokenUsage(100, 20)))
                        .thenResponds(planning("""
                                {"agentName": "done", "arguments": {"response": "a story"}}
                                """, new TokenUsage(30, 5))))
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .subAgents(AgenticServices.agentBuilder(Writer.class)
                        .name("writer")
                        .chatModel(MockChatModel.thatResponds("a story"))
                        .build())
                .listener(listener)
                .build();

        supervisor.invoke("Write a story about dragons");

        assertThat(workflow("supervisor").count()).isEqualTo(1);
        assertThat(agent("writer").count()).isEqualTo(1);
        assertThat(invocations("writer", "supervisor").count()).isEqualTo(1);
        assertThat(tokens("invoke_workflow", "supervisor", "input").totalAmount())
                .isEqualTo(130);
        assertThat(tokens("invoke_workflow", "supervisor", "output").totalAmount())
                .isEqualTo(25);
        assertThat(registry.find("gen_ai.invoke_agent.inference_calls")
                        .tag("gen_ai.agent.name", "supervisor")
                        .summaries())
                .isEmpty();
        assertThat(listener.pendingInvocations()).isZero();
    }

    public interface Editor {

        @UserMessage("Edit {{story}}")
        @Agent(outputKey = "story")
        String edit(@V("story") String story);
    }

    @Test
    void counts_the_invocations_of_each_agent_a_supervisor_routes_to() {
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .name("supervisor")
                .chatModel(MockChatModel.thatResponds("""
                                {"agentName": "writer", "arguments": {"topic": "dragons"}}
                                """)
                        .thenResponds("""
                                {"agentName": "editor", "arguments": {"story": "a story"}}
                                """)
                        .thenResponds("""
                                {"agentName": "editor", "arguments": {"story": "an edited story"}}
                                """)
                        .thenResponds("""
                                {"agentName": "done", "arguments": {"response": "an edited story"}}
                                """))
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .subAgents(
                        AgenticServices.agentBuilder(Writer.class)
                                .name("writer")
                                .chatModel(MockChatModel.thatResponds("a story"))
                                .build(),
                        AgenticServices.agentBuilder(Editor.class)
                                .name("editor")
                                .chatModel(MockChatModel.thatResponds("an edited story")
                                        .thenRepeats())
                                .build())
                .listener(listener)
                .build();

        supervisor.invoke("Write and edit a story about dragons");

        assertThat(invocations("writer", "supervisor").count()).isEqualTo(1);
        assertThat(invocations("editor", "supervisor").count()).isEqualTo(2);
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void records_a_decision_agent_without_inference_or_tool_calls() {
        UrgencyCheck check = AgenticServices.decisionAgentBuilder(UrgencyCheck.class)
                .name("urgency")
                .decisionModel(MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.9))
                .build();

        sequence("triage", check).invoke(Map.of("request", "The server is down"));

        assertThat(agent("urgency").count()).isEqualTo(1);
        assertThat(invocations("urgency", "triage").count()).isEqualTo(1);
        assertThat(registry.find("gen_ai.invoke_agent.inference_calls").summaries())
                .isEmpty();
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void a_failing_meter_registry_does_not_fail_the_agents() {
        SimpleMeterRegistry failingRegistry = new SimpleMeterRegistry();
        failingRegistry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                throw new IllegalStateException("registry failure");
            }
        });
        UntypedAgent novel = AgenticServices.sequenceBuilder()
                .name("novel")
                .subAgents(writerCallingTool(new Facts()))
                .outputKey("story")
                .listener(new MicrometerMetricsAgentListener(failingRegistry))
                .build();

        assertThat(novel.invoke(Map.of("topic", "dragons"))).isEqualTo("a story");
        assertThat(failingRegistry.getMeters()).isEmpty();
    }

    @Test
    void records_a_failed_attempt_of_an_agent_and_its_successful_retry() {
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .name("writer")
                .chatModel(MockChatModel.thatFails(new IllegalStateException("model down"))
                        .thenResponds("a story"))
                .build();
        UntypedAgent novel = AgenticServices.sequenceBuilder()
                .name("novel")
                .subAgents(writer)
                .outputKey("story")
                .errorHandler(errorContext -> ErrorRecoveryResult.retry())
                .listener(listener)
                .build();

        assertThat(novel.invoke(Map.of("topic", "dragons"))).isEqualTo("a story");

        assertThat(registry.find("gen_ai.invoke_agent.duration")
                        .tag("gen_ai.agent.name", "writer")
                        .timers())
                .extracting(timer -> timer.getId().getTag("outcome"), Timer::count)
                .containsExactlyInAnyOrder(tuple("ERROR", 1L), tuple("SUCCESS", 1L));
        assertThat(workflow("novel").getId().getTag("outcome")).isEqualTo("SUCCESS");
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void records_a_workflow_returning_a_stream_when_it_returns_it_and_its_agent_when_the_stream_completes()
            throws Exception {
        StreamingStory story = AgenticServices.sequenceBuilder(StreamingStory.class)
                .name("story")
                .subAgents(AgenticServices.agentBuilder(StreamingWriter.class)
                        .name("writer")
                        .streamingChatModel(MockStreamingChatModel.thatStreams("a", "story"))
                        .build())
                .outputKey("story")
                .listener(listener)
                .build();

        TokenStream stream = story.write("dragons");

        assertThat(workflow("story").count()).isEqualTo(1);
        assertThat(agentTimers()).isEmpty();
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        stream.onPartialResponse(partial -> {})
                .onCompleteResponse(response::complete)
                .onError(response::completeExceptionally)
                .start();
        response.get(10, SECONDS);
        await().atMost(10, SECONDS).until(() -> listener.pendingInvocations() == 0);
        assertThat(agent("writer").count()).isEqualTo(1);
    }

    @Test
    void keeps_a_token_stream_that_is_never_started_active() {
        StreamingWriter writer = AgenticServices.agentBuilder(StreamingWriter.class)
                .name("writer")
                .streamingChatModel(MockStreamingChatModel.thatStreams("a", "story"))
                .listener(listener)
                .build();

        writer.write("dragons");

        assertThat(registry.find("gen_ai.invoke_agent.duration").timers()).isEmpty();
        assertThat(registry.find("gen_ai.invoke_agent.inference_calls").summaries())
                .isEmpty();
        assertThat(active("invoke_agent", "writer").activeTasks()).isEqualTo(1);
        assertThat(listener.pendingInvocations()).isEqualTo(1);
    }

    @Test
    void records_an_agent_without_an_ai_service_from_its_invocation_callbacks() {
        AgenticScope agenticScope = mock(AgenticScope.class);
        when(agenticScope.memoryId()).thenReturn("memory");
        AgentInstance remote = mock(AgentInstance.class);
        when(remote.name()).thenReturn("remote");
        when(remote.agentId()).thenReturn("remote$0");
        when(remote.topology()).thenReturn(AgenticSystemTopology.AI_AGENT);
        when(remote.leaf()).thenReturn(true);

        listener.beforeAgentInvocation(new AgentRequest(agenticScope, remote, Map.of()));
        listener.afterAgentInvocation(new AgentResponse(agenticScope, remote, Map.of(), "done", null, null));

        assertThat(agent("remote").count()).isEqualTo(1);
        assertThat(agent("remote").getId().getTag("outcome")).isEqualTo("SUCCESS");
        assertThat(registry.find("gen_ai.invoke_agent.inference_calls").summaries())
                .isEmpty();
        assertThat(listener.pendingInvocations()).isZero();
    }

    private UntypedAgent sequence(String name, Object... subAgents) {
        return sequence(listener, name, subAgents);
    }

    private static UntypedAgent sequence(MicrometerMetricsAgentListener listener, String name, Object... subAgents) {
        return AgenticServices.sequenceBuilder()
                .name(name)
                .subAgents(subAgents)
                .outputKey("story")
                .listener(listener)
                .build();
    }

    private void invokeWithEveryOutcome() {
        sequence("novel", writerCallingTool(new Facts())).invoke(Map.of("topic", "dragons"));
        sequence("novel", writerCallingTool(new BrokenFacts())).invoke(Map.of("topic", "dragons"));
        assertThatThrownBy(() -> sequence("novel", failingWriter()).invoke(Map.of("topic", "dragons")));
        assertThatThrownBy(() -> sequence("review", writerCallingTool(new Facts()), approval())
                        .invoke(Map.of("topic", "dragons")))
                .isInstanceOf(AgenticSystemSuspendedException.class);
        AgenticServices.agentBuilder(StreamingWriter.class)
                .name("writer")
                .streamingChatModel(MockStreamingChatModel.thatStreams("a", "b", "c"))
                .listener(listener)
                .build()
                .write("dragons")
                .onPartialResponseWithContext(
                        (partial, context) -> context.streamingHandle().cancel())
                .onError(error -> {})
                .start();
        await().atMost(10, SECONDS).until(() -> listener.pendingInvocations() == 0);
    }

    private static Function<AgenticScope, Iterable<Tag>> tenantTags() {
        return agenticScope -> Tags.of("tenant", agenticScope.readState("tenant", "none"));
    }

    private static Writer summarizer() {
        return AgenticServices.agentBuilder(Writer.class)
                .name("summarizer")
                .chatModel(MockChatModel.thatResponds(planning("a summary", new TokenUsage(10, 2))))
                .build();
    }

    private static Writer writerFailingOnTool() {
        return AgenticServices.agentBuilder(Writer.class)
                .name("writer")
                .chatModel(modelCallingTool())
                .tools(new BrokenFacts())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                .build();
    }

    private static Writer writerCallingTool(Object tools) {
        return AgenticServices.agentBuilder(Writer.class)
                .name("writer")
                .chatModel(modelCallingTool())
                .tools(tools)
                .build();
    }

    private static Writer failingWriter() {
        return AgenticServices.agentBuilder(Writer.class)
                .name("writer")
                .chatModel(MockChatModel.thatFails(new IllegalStateException("model down")))
                .build();
    }

    private static HumanInTheLoop approval() {
        return AgenticServices.humanInTheLoopBuilder()
                .description("Human approval")
                .outputKey("approval")
                .responseProvider(agenticScope -> new SuspendedResponse<>("approval"))
                .build();
    }

    private static MockChatModel modelCallingTool() {
        return MockChatModel.thatResponds(request -> lastMessageIsToolResult(request)
                        ? AiMessage.from("a story")
                        : AiMessage.from(toolCall("lookup", Map.of())))
                .thenRepeats();
    }

    private static boolean lastMessageIsToolResult(ChatRequest request) {
        return request.messages().get(request.messages().size() - 1) instanceof ToolExecutionResultMessage;
    }

    private static ChatResponse planning(String reply, TokenUsage tokenUsage) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(reply))
                .tokenUsage(tokenUsage)
                .build();
    }

    private Timer agent(String name) {
        return duration("invoke_agent", name);
    }

    private Timer workflow(String name) {
        return duration("invoke_workflow", name);
    }

    private Timer duration(String operation, String name) {
        return registry.get("gen_ai.invoke_agent.duration")
                .tag("gen_ai.operation.name", operation)
                .tag("gen_ai.agent.name", name)
                .timer();
    }

    private Timer workflow(String name, String tenant) {
        return registry.get("gen_ai.invoke_agent.duration")
                .tag("gen_ai.operation.name", "invoke_workflow")
                .tag("gen_ai.agent.name", name)
                .tag("tenant", tenant)
                .timer();
    }

    private LongTaskTimer active(String operation, String name) {
        return registry.get("langchain4j.agent.invocations.active")
                .tag("gen_ai.operation.name", operation)
                .tag("gen_ai.agent.name", name)
                .longTaskTimer();
    }

    private Collection<Timer> agentTimers() {
        return registry.find("gen_ai.invoke_agent.duration")
                .tag("gen_ai.operation.name", "invoke_agent")
                .timers();
    }

    private Timer tool(String name) {
        return registry.get("gen_ai.execute_tool.duration")
                .tag("gen_ai.tool.name", name)
                .timer();
    }

    private DistributionSummary summary(String name, String agentName) {
        return registry.get(name).tag("gen_ai.agent.name", agentName).summary();
    }

    private DistributionSummary tokens(String operation, String agentName, String tokenType) {
        return registry.get("langchain4j.agent.token.usage")
                .tag("gen_ai.operation.name", operation)
                .tag("gen_ai.agent.name", agentName)
                .tag("gen_ai.token.type", tokenType)
                .summary();
    }

    private Counter invocations(String agentName, String parentName) {
        return invocations(agentName, parentName, "SUCCESS");
    }

    private Counter failedInvocations(String agentName, String parentName) {
        return invocations(agentName, parentName, "ERROR");
    }

    private Counter invocations(String agentName, String parentName, String outcome) {
        return registry.get("langchain4j.agent.invocations")
                .tag("gen_ai.agent.name", agentName)
                .tag("langchain4j.agent.parent.name", parentName)
                .tag("outcome", outcome)
                .counter();
    }

    private Set<String> tagKeys(String name) {
        return keys(registry.get(name).meter());
    }

    private static Set<String> keys(Meter meter) {
        return meter.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet());
    }
}
