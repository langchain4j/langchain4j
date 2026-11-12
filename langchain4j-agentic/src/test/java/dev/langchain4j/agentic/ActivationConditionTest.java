package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.MapperAgentInvoker;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentSkipped;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.workflow.ConditionalAgentInstance;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.agentic.workflow.LoopAgentInstance;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ActivationConditionTest {

    static final ChatModel ECHO_MODEL = ChatModelMock.thatResponds(chatRequest -> {
        var userMessage = (dev.langchain4j.data.message.UserMessage)
                chatRequest.messages().get(chatRequest.messages().size() - 1);
        return AiMessage.from("echo " + userMessage.singleText());
    });

    public interface EchoAgent {

        @UserMessage("{{text}}")
        @Agent(description = "Echoes a text", outputKey = "echo")
        String echo(@V("text") String text);
    }

    public interface ListMapper {

        @Agent(outputKey = "echoes")
        List<String> mapAll(@V("texts") List<String> texts);
    }

    private static AgenticScopeFunction<String> writer(String outputKey, String value, AtomicInteger invocations) {
        return AgenticServices.nonAiAgentBuilder(scope -> {
                    invocations.incrementAndGet();
                    return value;
                })
                .name(outputKey + "Writer")
                .outputKey(outputKey)
                .build();
    }

    private static AgenticScopeFunction<String> conditionalWriter(
            String outputKey, String value, AtomicInteger invocations, Predicate<AgenticScope> condition) {
        return AgenticServices.nonAiAgentBuilder(scope -> {
                    invocations.incrementAndGet();
                    return value;
                })
                .name(outputKey + "Writer")
                .outputKey(outputKey)
                .activationCondition(outputKey + " is needed", condition)
                .build();
    }

    // ---------------------------------------------------------------
    // Optional skip
    // ---------------------------------------------------------------

    @Test
    void optional_agent_without_output_key_is_skipped_when_an_argument_is_missing() {
        EchoAgent optionalEcho = AgenticServices.agentBuilder(EchoAgent.class)
                .chatModel(ECHO_MODEL)
                .outputKey((String) null)
                .optional(true)
                .build();
        AtomicInteger after = new AtomicInteger();

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(optionalEcho, writer("result", "done", after))
                .outputKey("result")
                .build();

        assertThat(sequence.invoke(Map.of())).isEqualTo("done");
        assertThat(after).hasValue(1);
    }

    // ---------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------

    @Test
    void null_condition_is_rejected_when_configured() {
        assertThatThrownBy(() -> AgenticServices.agentBuilder(EchoAgent.class).activationCondition(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("activationCondition");
        assertThatThrownBy(() -> AgenticServices.sequenceBuilder().activationCondition("description", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("activationCondition");
        assertThatThrownBy(() -> AgenticServices.supervisorBuilder().activationCondition(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("activationCondition");
        assertThatThrownBy(() -> AgenticServices.nonAiAgentBuilder(scope -> "x").activationCondition(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("activationCondition");
        assertThatThrownBy(() -> AgenticServices.humanInTheLoopBuilder().activationCondition(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("activationCondition");
    }

    // ---------------------------------------------------------------
    // Evaluation
    // ---------------------------------------------------------------

    @Test
    void inactive_agent_of_a_sequence_is_skipped_without_writing_its_output() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger last = new AtomicInteger();

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(
                        writer("first", "A", first),
                        conditionalWriter("second", "B", skipped, scope -> false),
                        writer("third", "C", last))
                .outputKey("third")
                .build();

        ResultWithAgenticScope<String> result = sequence.invokeWithAgenticScope(Map.of());

        assertThat(result.result()).isEqualTo("C");
        assertThat(first).hasValue(1);
        assertThat(skipped).hasValue(0);
        assertThat(last).hasValue(1);
        assertThat(result.agenticScope().hasState("second")).isFalse();
        assertThat(result.agenticScope().agentInvocations("secondWriter")).isEmpty();
    }

    @Test
    void active_agent_of_a_sequence_is_invoked() {
        AtomicInteger invocations = new AtomicInteger();

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(conditionalWriter("result", "B", invocations, scope -> scope.hasState("input")))
                .outputKey("result")
                .build();

        assertThat(sequence.invoke(Map.of("input", "x"))).isEqualTo("B");
        assertThat(invocations).hasValue(1);
    }

    @Test
    void condition_is_evaluated_at_every_iteration_of_a_loop() {
        List<Integer> activeIterations = Collections.synchronizedList(new ArrayList<>());
        AgenticScopeFunction<Integer> counter = AgenticServices.nonAiAgentBuilder(
                        scope -> scope.readState("count", 0) + 1)
                .name("counter")
                .outputKey("count")
                .build();
        AgenticScopeFunction<Object> oddOnly = AgenticServices.nonAiAgentBuilder(scope -> {
                    activeIterations.add(scope.readState("count", 0));
                    return null;
                })
                .name("oddOnly")
                .activationCondition("count is odd", scope -> scope.readState("count", 0) % 2 == 1)
                .build();

        UntypedAgent loop = AgenticServices.loopBuilder()
                .subAgents(counter, oddOnly)
                .maxIterations(3)
                .outputKey("count")
                .build();

        assertThat(loop.invoke(Map.of())).isEqualTo(3);
        assertThat(activeIterations).containsExactly(1, 3);
    }

    @Test
    void inactive_agent_of_a_parallel_workflow_is_skipped() {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger inactive = new AtomicInteger();

        UntypedAgent parallel = AgenticServices.parallelBuilder()
                .subAgents(
                        conditionalWriter("left", "L", active, scope -> true),
                        conditionalWriter("right", "R", inactive, scope -> false))
                .build();

        ResultWithAgenticScope<String> result = parallel.invokeWithAgenticScope(Map.of());

        assertThat(active).hasValue(1);
        assertThat(inactive).hasValue(0);
        assertThat(result.agenticScope().readState("left")).isEqualTo("L");
        assertThat(result.agenticScope().hasState("right")).isFalse();
    }

    @Test
    void condition_of_a_mapped_agent_is_evaluated_per_item_and_a_skipped_item_maps_to_null() {
        AtomicInteger evaluations = new AtomicInteger();
        EchoAgent echo = AgenticServices.agentBuilder(EchoAgent.class)
                .chatModel(ECHO_MODEL)
                .activationCondition("not the second item", scope -> evaluations.getAndIncrement() != 1)
                .build();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ListMapper mapper = AgenticServices.parallelMapperBuilder(ListMapper.class)
                    .subAgents(echo)
                    .executor(executor)
                    .build();

            assertThat(mapper.mapAll(List.of("a", "b", "c"))).containsExactly("echo a", null, "echo c");
            assertThat(evaluations).hasValue(3);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void agent_of_a_conditional_workflow_runs_only_if_both_conditions_hold() {
        AtomicInteger bothTrue = new AtomicInteger();
        AtomicInteger ownFalse = new AtomicInteger();
        AtomicInteger branchFalse = new AtomicInteger();

        UntypedAgent router = AgenticServices.conditionalBuilder()
                .subAgents(scope -> true, conditionalWriter("a", "A", bothTrue, scope -> true))
                .subAgents(scope -> true, conditionalWriter("b", "B", ownFalse, scope -> false))
                .subAgents(scope -> false, conditionalWriter("c", "C", branchFalse, scope -> true))
                .build();

        router.invoke(Map.of());

        assertThat(bothTrue).hasValue(1);
        assertThat(ownFalse).hasValue(0);
        assertThat(branchFalse).hasValue(0);
    }

    @Test
    void inactive_nested_workflow_is_skipped_as_a_whole() {
        AtomicInteger nested = new AtomicInteger();
        AtomicInteger after = new AtomicInteger();

        UntypedAgent inner = AgenticServices.sequenceBuilder()
                .subAgents(writer("innerFirst", "1", nested), writer("innerSecond", "2", nested))
                .outputKey("innerSecond")
                .activationCondition("inner is needed", scope -> scope.readState("needInner", false))
                .build();

        UntypedAgent outer = AgenticServices.sequenceBuilder()
                .subAgents(inner, writer("result", "done", after))
                .outputKey("result")
                .build();

        assertThat(outer.invoke(Map.of("needInner", false))).isEqualTo("done");
        assertThat(nested).hasValue(0);
        assertThat(after).hasValue(1);

        assertThat(outer.invoke(Map.of("needInner", true))).isEqualTo("done");
        assertThat(nested).hasValue(2);
    }

    @Test
    void inactive_async_agent_is_skipped_synchronously() {
        AtomicInteger asyncInvocations = new AtomicInteger();
        AgenticScopeFunction<String> asyncAgent = AgenticServices.nonAiAgentBuilder(scope -> {
                    asyncInvocations.incrementAndGet();
                    return "async";
                })
                .name("asyncAgent")
                .outputKey("asyncResult")
                .async(true)
                .activationCondition("never", scope -> false)
                .build();
        AgenticScopeFunction<String> reader = AgenticServices.nonAiAgentBuilder(
                        scope -> String.valueOf(scope.readState("asyncResult")))
                .name("reader")
                .outputKey("result")
                .build();

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(asyncAgent, reader)
                .outputKey("result")
                .build();

        assertThat(sequence.invoke(Map.of())).isEqualTo("null");
        assertThat(asyncInvocations).hasValue(0);
    }

    @Test
    void condition_is_evaluated_before_resolving_the_arguments() {
        AtomicInteger invocations = new AtomicInteger();
        AgenticScopeFunction<String> needsInput = AgenticServices.nonAiAgentBuilder(scope -> {
                    invocations.incrementAndGet();
                    return "invoked";
                })
                .name("needsInput")
                .inputKey(String.class, "missing")
                .outputKey("needsInput")
                .activationCondition("never", scope -> false)
                .build();

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(needsInput, writer("result", "done", new AtomicInteger()))
                .outputKey("result")
                .build();

        assertThat(sequence.invoke(Map.of())).isEqualTo("done");
        assertThat(invocations).hasValue(0);
    }

    @Test
    void failing_condition_is_retried_through_the_error_handler() {
        AtomicInteger evaluations = new AtomicInteger();
        AtomicInteger invocations = new AtomicInteger();
        List<String> failedAgents = new ArrayList<>();

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(conditionalWriter("result", "done", invocations, scope -> {
                    if (evaluations.incrementAndGet() == 1) {
                        throw new IllegalStateException("not yet");
                    }
                    return true;
                }))
                .outputKey("result")
                .errorHandler(errorContext -> {
                    failedAgents.add(errorContext.agentName());
                    return ErrorRecoveryResult.retry();
                })
                .build();

        assertThat(sequence.invoke(Map.of())).isEqualTo("done");
        assertThat(failedAgents).containsExactly("resultWriter");
        assertThat(evaluations).hasValue(2);
        assertThat(invocations).hasValue(1);
    }

    @Test
    void failing_condition_completes_with_the_result_of_the_error_handler() {
        AtomicInteger invocations = new AtomicInteger();

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(conditionalWriter("result", "done", invocations, scope -> {
                    throw new IllegalStateException("broken condition");
                }))
                .outputKey("result")
                .errorHandler(errorContext -> ErrorRecoveryResult.result("fallback"))
                .build();

        assertThat(sequence.invoke(Map.of())).isEqualTo("fallback");
        assertThat(invocations).hasValue(0);
    }

    @Test
    void failing_condition_is_rethrown_by_default() {
        AtomicInteger invocations = new AtomicInteger();

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(conditionalWriter("result", "done", invocations, scope -> {
                    throw new IllegalStateException("broken condition");
                }))
                .outputKey("result")
                .build();

        assertThatThrownBy(() -> sequence.invoke(Map.of()))
                .isInstanceOf(AgentInvocationException.class)
                .hasMessageContaining("Activation condition of agent 'resultWriter' failed")
                .hasRootCauseMessage("broken condition");
        assertThat(invocations).hasValue(0);
    }

    @Test
    void condition_of_the_root_agent_is_ignored() {
        AtomicInteger invocations = new AtomicInteger();

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(writer("result", "done", invocations))
                .outputKey("result")
                .activationCondition("never", scope -> false)
                .build();

        assertThat(sequence.invoke(Map.of())).isEqualTo("done");
        assertThat(invocations).hasValue(1);
    }

    @Test
    void skip_is_notified_to_listeners_and_to_the_planner() {
        List<AgentSkipped> skips = Collections.synchronizedList(new ArrayList<>());
        RecordingSequencePlanner planner = new RecordingSequencePlanner();
        EchoAgent optionalEcho = AgenticServices.agentBuilder(EchoAgent.class)
                .chatModel(ECHO_MODEL)
                .optional(true)
                .build();

        UntypedAgent recordingSequence = AgenticServices.plannerBuilder()
                .subAgents(
                        writer("echo", "previous", new AtomicInteger()),
                        conditionalWriter("result", "R", new AtomicInteger(), scope -> false),
                        optionalEcho)
                .planner(() -> planner)
                .build();
        // the listener is inherited by the subagents of the nested planner
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(recordingSequence)
                .listener(new AgentListener() {
                    @Override
                    public void onAgentSkipped(AgentSkipped agentSkipped) {
                        skips.add(agentSkipped);
                    }

                    @Override
                    public boolean inheritedBySubagents() {
                        return true;
                    }
                })
                .build();

        sequence.invoke(Map.of());

        assertThat(skips)
                .extracting(AgentSkipped::agentName, AgentSkipped::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "resultWriter", "activation condition is false: result is needed"),
                        org.assertj.core.groups.Tuple.tuple("echo", "missing argument 'text'"));
        assertThat(planner.invocations)
                .extracting(AgentInvocation::agentName, AgentInvocation::skipped, AgentInvocation::output)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("echoWriter", false, "previous"),
                        org.assertj.core.groups.Tuple.tuple("resultWriter", true, null),
                        // a skipped agent returns the current value of its output key
                        org.assertj.core.groups.Tuple.tuple("echo", true, "previous"));
    }

    public static class RecordingSequencePlanner implements Planner {

        final List<AgentInvocation> invocations = Collections.synchronizedList(new ArrayList<>());
        private List<AgentInstance> subagents;
        private int cursor;

        @Override
        public void init(InitPlanningContext initPlanningContext) {
            this.subagents = initPlanningContext.subagents();
        }

        @Override
        public Action firstAction(PlanningContext planningContext) {
            return call(subagents.get(cursor++));
        }

        @Override
        public Action nextAction(PlanningContext planningContext) {
            invocations.add(planningContext.previousAgentInvocation());
            return cursor < subagents.size() ? call(subagents.get(cursor++)) : done();
        }
    }

    // ---------------------------------------------------------------
    // Builders and accessor forwarding
    // ---------------------------------------------------------------

    private static final Predicate<AgenticScope> NEVER = scope -> false;

    private static AgentCondition conditionOfSubagent(Object agent) {
        UntypedAgent parent = AgenticServices.sequenceBuilder().subAgents(agent).build();
        return ((AgentInstance) parent).subagents().get(0).activationCondition();
    }

    private static void assertCondition(AgentCondition condition, String description) {
        assertThat(condition).isNotNull();
        assertThat(condition.description()).isEqualTo(description);
        assertThat(condition.predicate()).isSameAs(NEVER);
    }

    @Test
    void ai_agent_builder_sets_the_condition() {
        EchoAgent echo = AgenticServices.agentBuilder(EchoAgent.class)
                .chatModel(ECHO_MODEL)
                .activationCondition("ai", NEVER)
                .build();

        assertCondition(((AgentInstance) echo).activationCondition(), "ai");
        assertCondition(conditionOfSubagent(echo), "ai");
    }

    @Test
    void condition_without_description_has_an_unknown_description() {
        EchoAgent echo = AgenticServices.agentBuilder(EchoAgent.class)
                .chatModel(ECHO_MODEL)
                .activationCondition(NEVER)
                .build();

        assertCondition(conditionOfSubagent(echo), "<unknown>");
    }

    @Test
    void workflow_builders_set_the_condition() {
        Object leaf = writer("x", "x", new AtomicInteger());

        assertCondition(
                conditionOfSubagent(AgenticServices.sequenceBuilder()
                        .subAgents(leaf)
                        .activationCondition("sequence", NEVER)
                        .build()),
                "sequence");
        assertCondition(
                conditionOfSubagent(AgenticServices.loopBuilder()
                        .subAgents(leaf)
                        .activationCondition("loop", NEVER)
                        .build()),
                "loop");
        assertCondition(
                conditionOfSubagent(AgenticServices.parallelBuilder()
                        .subAgents(leaf)
                        .activationCondition("parallel", NEVER)
                        .build()),
                "parallel");
        assertCondition(
                conditionOfSubagent(AgenticServices.conditionalBuilder()
                        .subAgents(leaf)
                        .activationCondition("conditional", NEVER)
                        .build()),
                "conditional");
        assertCondition(
                conditionOfSubagent(AgenticServices.parallelMapperBuilder()
                        .subAgents(AgenticServices.agentBuilder(EchoAgent.class)
                                .chatModel(ECHO_MODEL)
                                .build())
                        .itemsProvider("texts")
                        .activationCondition("mapper", NEVER)
                        .build()),
                "mapper");
        assertCondition(
                conditionOfSubagent(AgenticServices.plannerBuilder()
                        .subAgents(leaf)
                        .planner(RecordingSequencePlanner::new)
                        .activationCondition("planner", NEVER)
                        .build()),
                "planner");
        assertCondition(
                conditionOfSubagent(AgenticServices.supervisorBuilder()
                        .chatModel(ECHO_MODEL)
                        .subAgents(leaf)
                        .activationCondition("supervisor", NEVER)
                        .build()),
                "supervisor");
    }

    @Test
    void workflow_instance_views_forward_the_condition_and_optional_flag() {
        UntypedAgent loop = AgenticServices.loopBuilder()
                .subAgents(writer("x", "x", new AtomicInteger()))
                .activationCondition("loop", NEVER)
                .build();
        UntypedAgent router = AgenticServices.conditionalBuilder()
                .subAgents(writer("x", "x", new AtomicInteger()))
                .activationCondition("router", NEVER)
                .build();

        LoopAgentInstance loopInstance = ((AgentInstance) loop).as(LoopAgentInstance.class);
        ConditionalAgentInstance routerInstance = ((AgentInstance) router).as(ConditionalAgentInstance.class);

        assertCondition(loopInstance.activationCondition(), "loop");
        assertThat(loopInstance.optional()).isFalse();
        assertCondition(routerInstance.activationCondition(), "router");
    }

    @Test
    void non_ai_agent_builder_sets_the_condition() {
        AgenticScopeFunction<String> agent = AgenticServices.nonAiAgentBuilder(scope -> "x")
                .activationCondition("non ai", NEVER)
                .build();

        assertCondition(agent.activationCondition(), "non ai");
        assertCondition(conditionOfSubagent(agent), "non ai");
    }

    @Test
    void human_in_the_loop_builder_sets_the_condition() {
        HumanInTheLoop humanInTheLoop = AgenticServices.humanInTheLoopBuilder()
                .responseProvider(() -> "ok")
                .activationCondition("human", NEVER)
                .build();

        assertCondition(humanInTheLoop.activationCondition(), "human");
        assertCondition(conditionOfSubagent(humanInTheLoop), "human");
    }

    @Test
    void mapper_agent_invoker_forwards_the_condition_of_the_mapped_agent() {
        EchoAgent echo = AgenticServices.agentBuilder(EchoAgent.class)
                .chatModel(ECHO_MODEL)
                .activationCondition("mapped", NEVER)
                .build();
        UntypedAgent parent = AgenticServices.sequenceBuilder().subAgents(echo).build();
        AgentExecutor executor =
                (AgentExecutor) ((AgentInstance) parent).subagents().get(0);

        assertCondition(
                new MapperAgentInvoker(executor.agentInvoker(), "item", 0, "mapper").activationCondition(), "mapped");
    }
}
