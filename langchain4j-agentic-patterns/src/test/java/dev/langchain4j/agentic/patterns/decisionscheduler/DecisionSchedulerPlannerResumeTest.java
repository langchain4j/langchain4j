package dev.langchain4j.agentic.patterns.decisionscheduler;

import static dev.langchain4j.agentic.patterns.decisionscheduler.DecisionSchedulerPlanner.DEFAULT_USEFULNESS_LEVELS;
import static dev.langchain4j.agentic.patterns.decisionscheduler.DecisionSchedulerPlanner.GOAL_QUESTION_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.AgenticScopeStore;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.test.mock.MockDecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DecisionSchedulerPlannerResumeTest {

    private static final String TICKET = "Where is my order #4711?";
    private static final String GOAL = "The reply answers the customer's ticket";

    public interface ResumableDesk extends AgenticScopeAccess {

        @Agent
        String handle(@MemoryId String sessionId, @V("ticket") String ticket);
    }

    /**
     * Keeps the agentic scopes as JSON, so that a resume after a crash restores the execution state of the planner
     * from its serialized form.
     */
    static class JsonInMemoryStore implements AgenticScopeStore {

        private final Map<AgenticScopeKey, String> scopes = new ConcurrentHashMap<>();

        @Override
        public boolean save(AgenticScopeKey key, DefaultAgenticScope agenticScope) {
            scopes.put(key, AgenticScopeSerializer.toJson(agenticScope));
            return true;
        }

        @Override
        public Optional<DefaultAgenticScope> load(AgenticScopeKey key) {
            return Optional.ofNullable(scopes.get(key)).map(AgenticScopeSerializer::fromJson);
        }

        @Override
        public boolean delete(AgenticScopeKey key) {
            return scopes.remove(key) != null;
        }

        @Override
        public Set<AgenticScopeKey> getAllKeys() {
            return scopes.keySet();
        }
    }

    // ── Subagents recording their invocations ──

    private final List<String> invoked = new CopyOnWriteArrayList<>();

    private static AgentArgument input(String name) {
        return new AgentArgument(String.class, name);
    }

    private static AgentArgument input(String name, String defaultValue) {
        return new AgentArgument(String.class, name, defaultValue);
    }

    private AgenticScopeFunction<Object> agent(String name, String outputKey, AgentArgument... inputs) {
        return AgenticServices.<Object>nonAiAgentBuilder(scope -> {
                    invoked.add(name);
                    return name + " output";
                })
                .name(name)
                .description("The " + name + " agent")
                .inputs(inputs)
                .outputKey(outputKey)
                .build();
    }

    private AgenticScopeFunction<Object> tracker() {
        return agent("tracker", "orderStatus", input("ticket"));
    }

    /** Writes the reply from the ticket and from the note of the operator, which is not mandatory. */
    private AgenticScopeFunction<Object> writer() {
        return agent("writer", "reply", input("ticket"), input("operatorNote", "not available"));
    }

    /** Rewrites the reply, which is also one of its arguments, so it can always run again. */
    private AgenticScopeFunction<Object> rewriter() {
        return agent("rewriter", "reply", input("ticket"), input("reply", "no reply yet"));
    }

    // the agent waiting for a human is named after its method, askUser
    private final HumanInTheLoop operator = AgenticServices.humanInTheLoopBuilder()
            .description("A support operator")
            .outputKey("operatorNote")
            .responseProvider(scope -> new SuspendedResponse<>("support-ticket"))
            .build();

    @AfterEach
    void cleanup() {
        AgenticScopePersister.setStore(null);
    }

    private static void simulateCrash(Object agenticSystem) {
        ((AgenticScopeOwner) agenticSystem).registry().clearInMemory();
    }

    private static void suspensionOf(Runnable invocation) {
        assertThatThrownBy(invocation::run).isInstanceOf(AgenticSystemSuspendedException.class);
    }

    // ── Decision models ──

    private static Map<String, Object> state(DecisionRequest request) {
        return ((FieldsInput) request.input()).fields();
    }

    private static List<String> questionNames(DecisionRequest request) {
        return List.copyOf(request.questions().keySet());
    }

    /**
     * A model answering each request with the goal probability and the scores computed from its state. The score of
     * a subagent is found by its name, and a subagent without a score is useless.
     */
    private static MockDecisionModel model(
            Function<Map<String, Object>, Double> goalProbability,
            Function<Map<String, Object>, Map<String, Double>> scores) {
        return MockDecisionModel.thatAnswers(request -> {
            Map<String, Object> state = state(request);
            Map<String, DecisionAnswer> answers = new LinkedHashMap<>();
            request.questions().forEach((name, question) -> answers.put(
                    name,
                    GOAL_QUESTION_NAME.equals(name)
                            ? YesNoAnswer.from(goalProbability.apply(state))
                            : ScaleAnswer.builder()
                                    .mean(scores.apply(state).getOrDefault(name, 0.0)
                                            * (DEFAULT_USEFULNESS_LEVELS.size() - 1))
                                    .build()));
            return answers;
        });
    }

    /**
     * A model asking for the tracker first, then for the operator, then for the writer, and considering the goal
     * reached once the reply is written.
     */
    private static MockDecisionModel modelAskingTheOperatorAfterTheTracker() {
        return model(state -> state.containsKey("reply") ? 0.9 : 0.1, state -> {
            if (!state.containsKey("orderStatus")) {
                return Map.of("tracker", 0.9);
            }
            return state.containsKey("operatorNote") ? Map.of("writer", 0.9) : Map.of("askUser", 0.9);
        });
    }

    private static DecisionSchedulerPlanner.Builder planner(MockDecisionModel model) {
        return DecisionSchedulerPlanner.builder()
                .decisionModel(model)
                .goal(GOAL)
                .goalThreshold(0.5)
                .activationThreshold(0.5);
    }

    private static ResumableDesk scheduler(MockDecisionModel model, Object... subagents) {
        return AgenticServices.plannerBuilder(ResumableDesk.class)
                .subAgents(subagents)
                .outputKey("reply")
                .planner(() -> planner(model).build())
                .build();
    }

    // ── Suspension and crash of the whole agentic system ──

    @Test
    void shouldSuspendWithoutAskingTheModelWhileAHumanAnswerIsPending() {
        AgenticScopePersister.setStore(new JsonInMemoryStore());
        MockDecisionModel model = modelAskingTheOperatorAfterTheTracker();
        ResumableDesk desk = scheduler(model, tracker(), operator, writer());

        suspensionOf(() -> desk.handle("s1", TICKET));

        assertThat(invoked).containsExactly("tracker");
        // one decision choosing the tracker, one choosing the operator, none on the pending answer
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void shouldDecideOnTheAnswerOfTheHumanWhenResumedAfterACrash() {
        AgenticScopePersister.setStore(new JsonInMemoryStore());
        MockDecisionModel model = modelAskingTheOperatorAfterTheTracker();
        ResumableDesk desk = scheduler(model, tracker(), operator, writer());

        suspensionOf(() -> desk.handle("s2", TICKET));
        simulateCrash(desk);
        desk.getAgenticScope("s2").completePendingResponse("operator answer");

        assertThat(desk.handle("s2", TICKET)).isEqualTo("writer output");
        // neither the tracker nor the operator ran again
        assertThat(invoked).containsExactly("tracker", "writer");
        // once the writer ran no subagent can run again, so the scheduler stops without a fourth decision
        assertThat(model.requests()).hasSize(3);
        DecisionRequest decisionOnTheAnswer = model.requests().get(2);
        assertThat(decisionOnTheAnswer.input())
                .isEqualTo(FieldsInput.from(Map.of(
                        "ticket", TICKET, "orderStatus", "tracker output", "operatorNote", "operator answer")));
        // the tracker and the operator already ran, and none of their arguments changed
        assertThat(questionNames(decisionOnTheAnswer)).containsExactly("writer");
    }

    @Test
    void shouldSuspendAgainWhenResumedBeforeTheHumanAnswered() {
        AgenticScopePersister.setStore(new JsonInMemoryStore());
        MockDecisionModel model = modelAskingTheOperatorAfterTheTracker();
        ResumableDesk desk = scheduler(model, tracker(), operator, writer());

        suspensionOf(() -> desk.handle("s3", TICKET));
        simulateCrash(desk);
        suspensionOf(() -> desk.handle("s3", TICKET));

        assertThat(invoked).containsExactly("tracker");
        assertThat(model.requests()).hasSize(2);

        desk.getAgenticScope("s3").completePendingResponse("operator answer");
        assertThat(desk.handle("s3", TICKET)).isEqualTo("writer output");
        assertThat(invoked).containsExactly("tracker", "writer");
    }

    // ── Execution state at the planner level ──

    private static AgentInvocation invocation(String agentName, Object output) {
        return new AgentInvocation(Object.class, agentName, agentName, Map.of(), output);
    }

    private static List<String> calledAgents(Action action) {
        assertThat(action.isDone()).isFalse();
        return ((Action.AgentCallAction) action)
                .agentsToCall().stream()
                        .map(AgentInstance.class::cast)
                        .map(AgentInstance::name)
                        .toList();
    }

    private static DefaultAgenticScope scopeWithTheTicket(String memoryId) {
        DefaultAgenticScope scope = new AgenticScopeRegistry("scheduler").create(memoryId);
        scope.writeState("ticket", TICKET);
        return scope;
    }

    @Test
    void shouldHaveNoExecutionStateBeforeASubagentIsSelected() {
        DecisionSchedulerPlanner planner = planner(MockDecisionModel.thatFails(new RuntimeException("unused")))
                .build();

        assertThat(planner.executionState()).isEmpty();
    }

    @Test
    void shouldRunAgainTheSubagentInFlightWithoutANewDecision() {
        MockDecisionModel model = model(state -> 0.1, state -> Map.of("tracker", 0.9, "writer", 0.6));
        AgentInstance desk = (AgentInstance) scheduler(model, tracker(), writer());
        DefaultAgenticScope scope = scopeWithTheTicket("s4");
        InitPlanningContext initPlanningContext = new InitPlanningContext(scope, desk, desk.subagents());

        // the tracker is selected, and the system stops while it runs
        DecisionSchedulerPlanner planner = planner(model).build();
        planner.init(initPlanningContext);
        assertThat(calledAgents(planner.firstAction(new PlanningContext(scope, null))))
                .containsExactly("tracker");
        Map<String, Object> executionState = planner.executionState();
        assertThat(executionState)
                .containsEntry("decision-scheduler-invocations", 1)
                .containsEntry("decision-scheduler-spent", List.of("tracker"))
                .containsEntry("decision-scheduler-current", "tracker")
                .containsEntry("decision-scheduler-pending", false);

        DecisionSchedulerPlanner resumed = planner(model).build();
        resumed.init(initPlanningContext);
        resumed.restoreExecutionState(executionState);

        assertThat(calledAgents(resumed.firstAction(new PlanningContext(scope, null))))
                .containsExactly("tracker");
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void shouldResumeAfterTheSubagentsAreDeclaredInAnotherOrder() {
        MockDecisionModel model = MockDecisionModel.thatFails(new RuntimeException("unused"));
        // the state was saved by a scheduler declaring the tracker before the writer
        AgentInstance desk = (AgentInstance) scheduler(model, writer(), tracker());
        DefaultAgenticScope scope = scopeWithTheTicket("s8");

        DecisionSchedulerPlanner resumed = planner(model).build();
        resumed.init(new InitPlanningContext(scope, desk, desk.subagents()));
        resumed.restoreExecutionState(Map.of(
                "decision-scheduler-invocations", 1,
                "decision-scheduler-spent", List.of("tracker"),
                "decision-scheduler-current", "tracker",
                "decision-scheduler-pending", false));

        // the state refers to the subagents by their names, so it does not depend on their positions
        assertThat(calledAgents(resumed.firstAction(new PlanningContext(scope, null))))
                .containsExactly("tracker");
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void shouldNotOfferAgainTheSpentSubagentsAfterARestore() {
        MockDecisionModel model = model(state -> 0.1, state -> Map.of("tracker", 0.9, "writer", 0.9));
        AgentInstance desk = (AgentInstance) scheduler(model, tracker(), writer());
        DefaultAgenticScope scope = scopeWithTheTicket("s5");

        DecisionSchedulerPlanner resumed = planner(model).build();
        resumed.init(new InitPlanningContext(scope, desk, desk.subagents()));
        // the state comes back from JSON, with a number of any kind
        resumed.restoreExecutionState(Map.of(
                "decision-scheduler-invocations", 2L,
                "decision-scheduler-spent", List.of("tracker", "writer"),
                "decision-scheduler-current", "writer",
                "decision-scheduler-pending", false));

        assertThat(calledAgents(resumed.firstAction(new PlanningContext(scope, null))))
                .containsExactly("writer");
        scope.writeState("reply", "writer output");
        Action action = resumed.nextAction(new PlanningContext(scope, invocation("writer", "writer output")));

        // both subagents ran and none of their arguments changed, so there is nothing to decide
        assertThat(action.isDone()).isTrue();
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void shouldKeepCountingTheInvocationsAfterARestore() {
        MockDecisionModel model = model(state -> 0.1, state -> Map.of("rewriter", 0.9));
        AgentInstance desk = (AgentInstance) scheduler(model, rewriter());
        DefaultAgenticScope scope = scopeWithTheTicket("s6");

        DecisionSchedulerPlanner resumed = planner(model).maxInvocations(2).build();
        resumed.init(new InitPlanningContext(scope, desk, desk.subagents()));
        resumed.restoreExecutionState(Map.of(
                "decision-scheduler-invocations", 2,
                "decision-scheduler-spent", List.of("rewriter"),
                "decision-scheduler-current", "rewriter",
                "decision-scheduler-pending", false));

        // running again the second invocation doesn't count as a third one
        assertThat(calledAgents(resumed.firstAction(new PlanningContext(scope, null))))
                .containsExactly("rewriter");
        scope.writeState("reply", "rewriter output");
        Action action = resumed.nextAction(new PlanningContext(scope, invocation("rewriter", "rewriter output")));

        // the limit of two invocations is reached, so the scheduler stops without asking the model
        assertThat(action.isDone()).isTrue();
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void shouldFailToResumeWhenTheSubagentInFlightNoLongerExists() {
        MockDecisionModel model = model(state -> 0.1, state -> Map.of("tracker", 0.9));
        AgentInstance desk = (AgentInstance) scheduler(model, tracker(), writer());
        DefaultAgenticScope scope = scopeWithTheTicket("s7");

        DecisionSchedulerPlanner resumed = planner(model).build();
        resumed.init(new InitPlanningContext(scope, desk, desk.subagents()));
        // the state was saved while waiting for a subagent removed since then
        resumed.restoreExecutionState(Map.of(
                "decision-scheduler-invocations", 2,
                "decision-scheduler-spent", List.of("tracker", "askUser"),
                "decision-scheduler-current", "askUser",
                "decision-scheduler-pending", true));

        assertThatThrownBy(() -> resumed.firstAction(new PlanningContext(scope, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'askUser'")
                .hasMessageContaining("subagents changed");
        assertThat(model.requests()).isEmpty();
    }
}
