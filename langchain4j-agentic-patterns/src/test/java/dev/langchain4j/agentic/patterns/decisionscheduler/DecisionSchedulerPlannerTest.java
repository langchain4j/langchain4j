package dev.langchain4j.agentic.patterns.decisionscheduler;

import static dev.langchain4j.agentic.patterns.decisionscheduler.DecisionSchedulerPlanner.GOAL_QUESTION_NAME;
import static dev.langchain4j.agentic.patterns.decisionscheduler.DecisionSchedulerPlanner.DEFAULT_USEFULNESS_LEVELS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.AgenticServices.NonAiAgentBuilder;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentUnavailableException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockDecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DecisionSchedulerPlannerTest {

    private static final String TICKET = "Where is my order #4711?";
    private static final String GOAL = "The reply answers the customer's ticket";

    // ── Agentic systems ──

    public interface SupportDesk {

        @Agent
        String handle(@V("ticket") String ticket);
    }

    public interface SupportDeskWithScope {

        @Agent
        ResultWithAgenticScope<String> handle(@V("ticket") String ticket);
    }

    public interface StatusDesk {

        @Agent
        String handle(@V("ticket") String ticket, @V("orderStatus") String orderStatus);
    }

    public interface ObjectDesk {

        @Agent
        Object handle(@V("ticket") String ticket);
    }

    public record Ticket(String subject, int priority) {}

    public interface TicketDesk {

        @Agent
        String handle(@V("ticket") Ticket ticket);
    }

    public interface TicketMapDesk {

        @Agent
        String handle(@V("ticket") Map<String, Object> ticket);
    }

    public interface SupportPipeline {

        @Agent
        String process(@V("ticket") String ticket);
    }

    public static class Summarizer {

        @Agent(outputKey = "summary")
        public String summarize(@V("reply") String reply) {
            return "summary of " + reply;
        }
    }

    public record OrderStatus(String carrier, int daysLate) {}

    public interface AiWriter {

        @UserMessage("Write a reply to the ticket {{ticket}} using the order status {{orderStatus}}")
        @Agent(name = "writer", description = "The writer agent", outputKey = "reply")
        String write(@V("ticket") String ticket, @V("orderStatus") String orderStatus);
    }

    // ── Subagents recording their invocations ──

    private final List<String> invoked = new CopyOnWriteArrayList<>();

    private static AgentArgument input(String name) {
        return new AgentArgument(String.class, name);
    }

    private static AgentArgument input(String name, String defaultValue) {
        return new AgentArgument(String.class, name, defaultValue);
    }

    /** A subagent named after the given name, described as "The name agent" and answering "name output". */
    private NonAiAgentBuilder<Object> agent(String name, String outputKey, AgentArgument... inputs) {
        return agent(name, outputKey, scope -> name + " output", inputs);
    }

    private NonAiAgentBuilder<Object> agent(
            String name, String outputKey, Function<AgenticScope, Object> output, AgentArgument... inputs) {
        return AgenticServices.<Object>nonAiAgentBuilder(scope -> {
                    invoked.add(name);
                    return output.apply(scope);
                })
                .name(name)
                .description("The " + name + " agent")
                .inputs(inputs)
                .outputKey(outputKey);
    }

    private AgenticScopeFunction<Object> tracker() {
        return agent("tracker", "orderStatus", input("ticket")).build();
    }

    private AgenticScopeFunction<Object> advisor() {
        return agent("advisor", "refundPolicy", input("ticket")).build();
    }

    /** Writes the reply from the ticket and from the findings, which are not mandatory. */
    private AgenticScopeFunction<Object> writer() {
        return agent(
                        "writer",
                        "reply",
                        scope -> "reply using " + scope.readState("orderStatus", "nothing") + " and "
                                + scope.readState("refundPolicy", "nothing"),
                        input("ticket"),
                        input("orderStatus", "not available"),
                        input("refundPolicy", "not available"))
                .build();
    }

    // ── Decision models ──

    private static Map<String, Object> state(DecisionRequest request) {
        return ((FieldsInput) request.input()).fields();
    }

    private static ScaleAnswer score(double score) {
        return ScaleAnswer.builder().mean(score * (DEFAULT_USEFULNESS_LEVELS.size() - 1)).build();
    }

    /**
     * A model answering each request with the goal probability and the scores computed from its state. The score of
     * a subagent is found by the name of its question, and a subagent without a score is useless.
     */
    private static MockDecisionModel model(
            Function<Map<String, Object>, Double> goalProbability,
            Function<Map<String, Object>, Map<String, Double>> scores) {
        return MockDecisionModel.thatAnswers(request -> {
            Map<String, Object> state = state(request);
            Map<String, Double> stateScores = scores.apply(state);
            Map<String, DecisionAnswer> answers = new LinkedHashMap<>();
            request.questions().forEach((name, question) -> answers.put(
                    name,
                    GOAL_QUESTION_NAME.equals(name)
                            ? YesNoAnswer.from(goalProbability.apply(state))
                            : score(stateScores.getOrDefault(name, 0.0))));
            return answers;
        }).thenRepeats();
    }

    private static MockDecisionModel model(double goalProbability, Map<String, Double> scores) {
        return model(state -> goalProbability, state -> scores);
    }

    /** A model considering the goal reached as soon as the reply is part of the state. */
    private static MockDecisionModel modelReachingTheGoalWithAReply(Map<String, Double> scores) {
        return model(state -> state.containsKey("reply") ? 0.9 : 0.1, state -> scores);
    }

    private static DecisionSchedulerPlanner.Builder planner(MockDecisionModel model) {
        return DecisionSchedulerPlanner.builder()
                .decisionModel(model)
                .goal(GOAL)
                .goalThreshold(0.5)
                .activationThreshold(0.5);
    }

    private static <T> T scheduler(Class<T> type, Supplier<DecisionSchedulerPlanner> planner, Object... subagents) {
        return AgenticServices.plannerBuilder(type)
                .subAgents(subagents)
                .outputKey("reply")
                .planner(planner::get)
                .build();
    }

    private static SupportDesk scheduler(MockDecisionModel model, Object... subagents) {
        return scheduler(SupportDesk.class, () -> planner(model).build(), subagents);
    }

    // ── Selection ──

    @Test
    void shouldInvokeTheMostUsefulSubagentAtEachStepUntilTheGoalIsReached() {
        MockDecisionModel model =
                modelReachingTheGoalWithAReply(Map.of("tracker", 0.9, "advisor", 0.2, "writer", 0.6));

        String reply = scheduler(model, tracker(), advisor(), writer()).handle(TICKET);

        assertThat(invoked).containsExactly("tracker", "writer");
        assertThat(reply).isEqualTo("reply using tracker output and nothing");
        assertThat(model.requests()).hasSize(3);
    }

    @Test
    void shouldPreferTheFirstDeclaredSubagentOnATie() {
        MockDecisionModel model = modelReachingTheGoalWithAReply(Map.of("tracker", 0.7, "advisor", 0.7, "writer", 0.6));

        scheduler(model, tracker(), advisor(), writer()).handle(TICKET);

        assertThat(invoked).containsExactly("tracker", "advisor", "writer");
    }

    @Test
    void shouldAskTheModelEvenWhenASingleSubagentCanRun() {
        MockDecisionModel model = model(0.1, Map.of("writer", 0.2));

        // the only candidate is not invoked blindly: the model finds it not useful enough
        assertThatThrownBy(() -> scheduler(model, writer()).handle(TICKET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no subagent is useful enough");
        assertThat(invoked).isEmpty();
        assertThat(model.request().questions()).containsOnlyKeys("writer");
    }

    @Test
    void shouldInvokeASubagentWhoseScoreEqualsTheActivationThreshold() {
        MockDecisionModel model = modelReachingTheGoalWithAReply(Map.of("writer", 0.5));

        assertThat(scheduler(model, writer()).handle(TICKET)).isEqualTo("reply using nothing and nothing");
        assertThat(invoked).containsExactly("writer");
    }

    // ── Stops ──

    @Test
    void shouldStopWhenTheGoalProbabilityEqualsTheThreshold() {
        MockDecisionModel model = model(0.5, Map.of("tracker", 0.9, "writer", 0.9));

        // the goal is asked once the reply exists, and stops the scheduler although the tracker is still useful
        scheduler(model, writer(), tracker()).handle(TICKET);

        assertThat(invoked).containsExactly("writer");
    }

    @Test
    void shouldStopWithTheResultWhenNoSubagentReachesTheActivationThreshold() {
        MockDecisionModel model = model(0.1, Map.of("writer", 0.9, "tracker", 0.49));

        // the goal is not reached, but the reply exists and is what the caller gets
        assertThat(scheduler(model, writer(), tracker()).handle(TICKET)).isEqualTo("reply using nothing and nothing");
        assertThat(invoked).containsExactly("writer");
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void shouldFailWhenNoSubagentReachesTheActivationThresholdBeforeTheResultExists() {
        MockDecisionModel model = model(0.1, Map.of("tracker", 0.9, "advisor", 0.49, "writer", 0.49));

        assertThatThrownBy(() -> scheduler(model, tracker(), advisor(), writer()).handle(TICKET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stopped after 1 invocation(s)")
                .hasMessageContaining("output key 'reply'")
                .hasMessageContaining("no subagent is useful enough")
                .hasMessageContaining("{advisor=0.49, writer=0.49}")
                .hasMessageContaining("activation threshold 0.5");
        assertThat(invoked).containsExactly("tracker");
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void shouldStopWithTheResultWithoutAskingTheModelWhenNoSubagentCanRun() {
        MockDecisionModel model = model(0.1, Map.of("writer", 0.9));

        // once the writer ran none of its arguments changes, so nothing can run and the model is not asked again
        assertThat(scheduler(model, writer()).handle(TICKET)).isEqualTo("reply using nothing and nothing");
        assertThat(invoked).containsExactly("writer");
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void shouldFailWithoutAskingTheModelWhenNoSubagentCanRunBeforeTheResultExists() {
        MockDecisionModel model = model(0.1, Map.of("summarizer", 0.9));

        assertThatThrownBy(() -> scheduler(model, agent("summarizer", "reply", input("orderStatus")).build())
                        .handle(TICKET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stopped after 0 invocation(s)")
                .hasMessageContaining("output key 'reply'")
                .hasMessageContaining("no subagent can run");
        assertThat(invoked).isEmpty();
        assertThat(model.requests()).isEmpty();
    }

    /** Rewrites the reply, which is also one of its arguments, so it can always run again. */
    private AgenticScopeFunction<Object> rewriter() {
        return agent("rewriter", "reply", input("ticket"), input("reply", "no reply yet"))
                .build();
    }

    @Test
    void shouldStopWithTheResultAtTheMaximumNumberOfInvocationsWithoutAskingTheModelAgain() {
        MockDecisionModel model = model(0.1, Map.of("rewriter", 0.9));

        String reply = scheduler(
                        SupportDesk.class, () -> planner(model).maxInvocations(3).build(), rewriter())
                .handle(TICKET);

        // one decision for each invocation, and none after the last one
        assertThat(reply).isEqualTo("rewriter output");
        assertThat(invoked).containsExactly("rewriter", "rewriter", "rewriter");
        assertThat(model.requests()).hasSize(3);
    }

    @Test
    void shouldFailAtTheMaximumNumberOfInvocationsBeforeTheResultExists() {
        MockDecisionModel model = model(0.1, Map.of("tracker", 0.9, "writer", 0.8));

        assertThatThrownBy(() -> scheduler(
                                SupportDesk.class,
                                () -> planner(model).maxInvocations(1).build(),
                                tracker(),
                                writer())
                        .handle(TICKET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stopped after 1 invocation(s)")
                .hasMessageContaining("output key 'reply'")
                .hasMessageContaining("the maximum number of invocations was reached");
        assertThat(invoked).containsExactly("tracker");
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void shouldWriteTheResultUnderTheOutputKey() {
        MockDecisionModel model = modelReachingTheGoalWithAReply(Map.of("writer", 0.9));

        ResultWithAgenticScope<String> result = scheduler(
                        SupportDeskWithScope.class, () -> planner(model).build(), writer())
                .handle(TICKET);

        assertThat(result.result()).isEqualTo("reply using nothing and nothing");
        assertThat(result.agenticScope().readState("reply", "")).isEqualTo("reply using nothing and nothing");
    }

    // ── Candidates ──

    private static List<String> questionNames(DecisionRequest request) {
        return List.copyOf(request.questions().keySet());
    }

    @Test
    void shouldOfferOnlyTheSubagentsWhoseMandatoryArgumentsAreInTheScope() {
        MockDecisionModel model = modelReachingTheGoalWithAReply(Map.of("tracker", 0.9, "summarizer", 0.9));

        scheduler(model, agent("summarizer", "reply", input("orderStatus")).build(), tracker())
                .handle(TICKET);

        assertThat(invoked).containsExactly("tracker", "summarizer");
        assertThat(questionNames(model.requests().get(0))).containsExactly("tracker");
        assertThat(questionNames(model.requests().get(1))).containsExactly("summarizer");
    }

    @Test
    void shouldOfferASubagentWhoseMissingArgumentsHaveADefaultValue() {
        MockDecisionModel model = modelReachingTheGoalWithAReply(Map.of("writer", 0.9));

        scheduler(model, tracker(), writer()).handle(TICKET);

        assertThat(questionNames(model.requests().get(0))).containsExactly("tracker", "writer");
        assertThat(invoked).containsExactly("writer");
    }

    @Test
    void shouldOfferAnAiAgentWhoseMissingArgumentHasADefaultValueSetOnItsBuilder() {
        MockDecisionModel model = modelReachingTheGoalWithAReply(Map.of("writer", 0.9));
        AiWriter aiWriter = AgenticServices.agentBuilder(AiWriter.class)
                .chatModel(MockChatModel.thatResponds("ai reply"))
                .defaultKeyValue("orderStatus", "not available")
                .build();

        assertThat(scheduler(model, tracker(), aiWriter).handle(TICKET)).isEqualTo("ai reply");
        assertThat(questionNames(model.requests().get(0))).containsExactly("tracker", "writer");
        assertThat(invoked).isEmpty();
    }

    @Test
    void shouldNotOfferAgainASubagentWhoseArgumentsDidNotChange() {
        MockDecisionModel model = model(0.1, Map.of("tracker", 0.9, "advisor", 0.8));

        scheduler(model, tracker(), advisor()).handle(TICKET);

        assertThat(invoked).containsExactly("tracker", "advisor");
        assertThat(questionNames(model.requests().get(0)))
                .containsExactly(GOAL_QUESTION_NAME, "tracker", "advisor");
        assertThat(questionNames(model.requests().get(1))).containsExactly(GOAL_QUESTION_NAME, "advisor");
        // once both ran no subagent can run again, so the model isn't asked a third time
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void shouldOfferAgainASubagentWhenOneOfItsArgumentsChanges() {
        // the writer is the most useful until it wrote a reply, then the advisor, whose output is an argument of the
        // writer, and the goal is reached when the reply uses that output
        MockDecisionModel model = model(
                state -> String.valueOf(state.get("reply")).contains("advisor output") ? 0.9 : 0.1,
                state -> state.containsKey("reply") && !state.containsKey("refundPolicy")
                        ? Map.of("advisor", 0.9)
                        : Map.of("writer", 0.9, "advisor", 0.6));

        String reply = scheduler(model, advisor(), writer()).handle(TICKET);

        assertThat(invoked).containsExactly("writer", "advisor", "writer");
        assertThat(reply).isEqualTo("reply using nothing and advisor output");
        assertThat(questionNames(model.requests().get(1))).containsExactly(GOAL_QUESTION_NAME, "advisor");
        assertThat(questionNames(model.requests().get(2))).containsExactly(GOAL_QUESTION_NAME, "writer");
    }

    @Test
    void shouldNotOfferAnInactiveSubagent() {
        MockDecisionModel model = modelReachingTheGoalWithAReply(Map.of("tracker", 0.9, "writer", 0.6));
        AgenticScopeFunction<Object> inactiveTracker = agent("tracker", "orderStatus", input("ticket"))
                .activationCondition(scope -> false)
                .build();

        scheduler(model, inactiveTracker, writer()).handle(TICKET);

        assertThat(invoked).containsExactly("writer");
        assertThat(questionNames(model.requests().get(0))).containsExactly("writer");
    }

    @Test
    void shouldNotOfferAgainASkippedSubagentNorTheSubagentsReadingItsOutput() {
        MockDecisionModel model = model(0.1, Map.of("writer", 0.9, "tracker", 0.8));
        // its condition holds when the candidates of the first two steps are selected, but no more when it is invoked
        AtomicInteger evaluations = new AtomicInteger();
        AgenticScopeFunction<Object> skippedTracker = agent("tracker", "orderStatus", input("ticket"))
                .activationCondition(scope -> evaluations.incrementAndGet() <= 2)
                .build();

        scheduler(model, writer(), skippedTracker).handle(TICKET);

        // the writer reads the output of the tracker, but a skipped tracker didn't change it
        assertThat(invoked).containsExactly("writer");
        assertThat(questionNames(model.requests().get(0))).containsExactly("writer", "tracker");
        assertThat(questionNames(model.requests().get(1))).containsExactly(GOAL_QUESTION_NAME, "tracker");
        assertThat(model.requests()).hasSize(2);
    }

    // ── Unavailable subagents ──

    private static AgentInstance remoteAgent(String name) {
        AgentInstance remote = mock(AgentInstance.class);
        when(remote.name()).thenReturn(name);
        when(remote.agentId()).thenReturn(name + "$0");
        when(remote.outputKey()).thenReturn(name + "Output");
        when(remote.arguments()).thenReturn(List.of(input("ticket")));
        return remote;
    }

    /** The first action of a scheduler with the given remote subagent declared before a tracker and a writer. */
    private Action firstActionWith(MockDecisionModel model, AgentInstance remote, String... stateKeys) {
        AgentInstance scheduler = (AgentInstance) scheduler(model, tracker(), writer());
        DefaultAgenticScope scope = new AgenticScopeRegistry("scheduler").create("unavailable");
        for (String key : stateKeys) {
            scope.writeState(key, TICKET);
        }
        List<AgentInstance> subagents = List.of(
                remote, scheduler.subagents().get(0), scheduler.subagents().get(1));

        DecisionSchedulerPlanner planner = planner(model).build();
        planner.init(new InitPlanningContext(scope, scheduler, subagents));
        return planner.firstAction(new PlanningContext(scope, null));
    }

    private static List<String> calledAgents(Action action) {
        assertThat(action.isDone()).isFalse();
        return ((Action.AgentCallAction) action)
                .agentsToCall().stream()
                        .map(AgentInstance.class::cast)
                        .map(AgentInstance::name)
                        .toList();
    }

    @Test
    void shouldLeaveOutASubagentThatIsUnavailable() {
        MockDecisionModel model = model(0.1, Map.of("remote", 0.9, "tracker", 0.8));
        AgentInstance remote = remoteAgent("remote");
        when(remote.description())
                .thenThrow(new AgentUnavailableException(
                        "remote", "Failed to fetch the agent card", new IllegalStateException("connection refused")));

        assertThat(calledAgents(firstActionWith(model, remote, "ticket"))).containsExactly("tracker");
        assertThat(questionNames(model.request())).containsExactly("tracker", "writer");
    }

    @Test
    void shouldPropagateAnyOtherFailureOfTheDescriptionOfASubagent() {
        MockDecisionModel model = model(0.1, Map.of("tracker", 0.8));
        AgentInstance remote = remoteAgent("remote");
        when(remote.description()).thenThrow(new IllegalStateException("Tool 'writr' not found"));

        assertThatThrownBy(() -> firstActionWith(model, remote, "ticket"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'writr' not found");
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void shouldNotReadTheDescriptionOfASubagentThatCannotRun() {
        MockDecisionModel model = model(0.1, Map.of());
        AgentInstance remote = remoteAgent("remote");

        // without the ticket in the scope no subagent can run
        assertThatThrownBy(() -> firstActionWith(model, remote))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no subagent can run");
        verify(remote, never()).description();
        assertThat(model.requests()).isEmpty();
    }

    // ── Request ──

    @Test
    void shouldSendTheArgumentsAndTheOutputsInTheScopeAsTheStateOfEachStep() {
        MockDecisionModel model =
                modelReachingTheGoalWithAReply(Map.of("tracker", 0.9, "advisor", 0.2, "writer", 0.6));

        scheduler(model, tracker(), advisor(), writer()).handle(TICKET);

        assertThat(model.requests().get(0).input()).isEqualTo(FieldsInput.from(Map.of("ticket", TICKET)));
        assertThat(model.requests().get(1).input())
                .isEqualTo(FieldsInput.from(Map.of("ticket", TICKET, "orderStatus", "tracker output")));
        assertThat(model.requests().get(2).input())
                .isEqualTo(FieldsInput.from(Map.of(
                        "ticket",
                        TICKET,
                        "orderStatus",
                        "tracker output",
                        "reply",
                        "reply using tracker output and nothing")));
    }

    @Test
    void shouldLeaveTheExcludedOutputKeysOutOfTheState() {
        MockDecisionModel model =
                modelReachingTheGoalWithAReply(Map.of("tracker", 0.9, "advisor", 0.2, "writer", 0.6));

        String reply = scheduler(
                        SupportDesk.class,
                        () -> planner(model).excludedOutputKeys("orderStatus").build(),
                        tracker(),
                        advisor(),
                        writer())
                .handle(TICKET);

        // the writer still reads the output of the tracker, which the decision model never receives
        assertThat(reply).isEqualTo("reply using tracker output and nothing");
        assertThat(model.requests().get(1).input()).isEqualTo(FieldsInput.from(Map.of("ticket", TICKET)));
        assertThat(model.requests().get(2).input())
                .isEqualTo(FieldsInput.from(
                        Map.of("ticket", TICKET, "reply", "reply using tracker output and nothing")));
    }

    @Test
    void shouldLeaveOutAnExcludedOutputKeyThatIsAlsoAnArgumentOfTheScheduler() {
        MockDecisionModel model = modelReachingTheGoalWithAReply(Map.of("writer", 0.9));

        String reply = scheduler(
                        StatusDesk.class,
                        () -> planner(model).excludedOutputKeys("orderStatus").build(),
                        tracker(),
                        writer())
                .handle(TICKET, "known status");

        // the order status is both an argument of the scheduler and the output of the tracker
        assertThat(reply).isEqualTo("reply using known status and nothing");
        assertThat(model.requests().get(0).input()).isEqualTo(FieldsInput.from(Map.of("ticket", TICKET)));
    }

    @Test
    void shouldAskOneScaleQuestionPerCandidateAndTheGoalOnceTheResultExists() {
        MockDecisionModel model = modelReachingTheGoalWithAReply(Map.of("writer", 0.9));
        AgenticScopeFunction<String> undescribedTracker = AgenticServices.nonAiAgentBuilder(scope -> "status")
                .name("tracker")
                .inputs(input("ticket"))
                .outputKey("orderStatus")
                .build();

        scheduler(model, writer(), undescribedTracker).handle(TICKET);

        Map<String, ?> firstQuestions = model.requests().get(0).questions();
        // each question is named after the id of its agent, and asks about the agent by its name
        assertThat(firstQuestions).containsOnlyKeys("writer", "tracker");
        assertThat(firstQuestions.get("writer"))
                .isEqualTo(ScaleQuestion.from(
                        "How useful would it be to run the agent 'writer' (The writer agent) now, to reach this "
                                + "goal: " + GOAL + "?",
                        List.of("USELESS", "MARGINAL", "USEFUL", "ESSENTIAL")));
        assertThat(firstQuestions.get("tracker"))
                .isEqualTo(ScaleQuestion.from(
                        // an agent without a description is described by its name
                        "How useful would it be to run the agent 'tracker' (tracker) now, to reach this goal: " + GOAL
                                + "?",
                        DEFAULT_USEFULNESS_LEVELS));

        Map<String, ?> secondQuestions = model.requests().get(1).questions();
        assertThat(secondQuestions).containsOnlyKeys(GOAL_QUESTION_NAME, "tracker");
        assertThat(secondQuestions.get(GOAL_QUESTION_NAME))
                .isEqualTo(YesNoQuestion.from("Has this goal been reached: " + GOAL + "?"));
    }

    @Test
    void shouldAskForTheGoalAtEveryStepWhenNoSubagentWritesTheOutputKey() {
        MockDecisionModel model = model(0.1, Map.of("tracker", 0.9));

        // the output key of the scheduler is "reply", which the tracker doesn't write: no subagent is expected to
        // produce the result, so stopping without it is not a failure
        assertThat(scheduler(model, tracker()).handle(TICKET)).isNull();

        assertThat(questionNames(model.request())).containsExactly(GOAL_QUESTION_NAME, "tracker");
        assertThat(invoked).containsExactly("tracker");
    }

    @Test
    void shouldStopBeforeInvokingAnySubagentWhenTheGoalIsAlreadyReached() {
        MockDecisionModel model = model(0.9, Map.of("tracker", 0.9));

        scheduler(model, tracker()).handle(TICKET);

        assertThat(invoked).isEmpty();
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void shouldSendTheArgumentsAsTheyAreAndConvertATypedOutputToJsonValues() {
        MockDecisionModel model = model(0.1, Map.of("tracker", 0.9));
        Map<String, Object> ticket = Map.of("subject", "Late parcel", "priority", 2);
        AgenticScopeFunction<Object> typedTracker = agent(
                        "tracker", "orderStatus", scope -> new OrderStatus("ACME", 3), new AgentArgument(Map.class, "ticket"))
                .build();

        AgenticScopeFunction<Object> mapAdvisor = agent(
                        "advisor", "refundPolicy", new AgentArgument(Map.class, "ticket"))
                .build();

        scheduler(TicketMapDesk.class, () -> planner(model).build(), typedTracker, mapAdvisor)
                .handle(ticket);

        assertThat(invoked).containsExactly("tracker");
        assertThat(model.requests().get(1).input())
                .isEqualTo(FieldsInput.from(Map.of(
                        "ticket", ticket, "orderStatus", Map.of("carrier", "ACME", "daysLate", 3))));
    }

    @Test
    void shouldSendAContentOutputAsItIs() {
        MockDecisionModel model = model(0.1, Map.of("tracker", 0.9));
        ImageContent image = ImageContent.from("https://example.com/parcel.png");
        AgenticScopeFunction<Object> photographer =
                agent("tracker", "orderStatus", scope -> image, input("ticket")).build();

        scheduler(ObjectDesk.class, () -> planner(model).build(), photographer, advisor())
                .handle(TICKET);

        assertThat(model.requests().get(1).input())
                .isEqualTo(FieldsInput.from(Map.of("ticket", TICKET, "orderStatus", image)));
    }

    @Test
    void shouldRejectAnArgumentThatTheDecisionModelDoesNotAccept() {
        MockDecisionModel model = model(0.1, Map.of("tracker", 0.9));
        AgenticScopeFunction<Object> ticketTracker = agent(
                        "tracker", "orderStatus", new AgentArgument(Ticket.class, "ticket"))
                .build();

        // the record is sent as it is, like the arguments of a decision router, and the decision model rejects it
        assertThatThrownBy(() -> scheduler(TicketDesk.class, () -> planner(model).build(), ticketTracker)
                        .handle(new Ticket("Late parcel", 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strings, numbers, booleans")
                .hasMessageContaining(Ticket.class.getName());
        assertThat(model.requests()).isEmpty();
        assertThat(invoked).isEmpty();
    }

    // ── Composition ──

    @Test
    void shouldBeAStepOfASequence() {
        MockDecisionModel model = modelReachingTheGoalWithAReply(Map.of("tracker", 0.9, "writer", 0.6));

        SupportPipeline pipeline = AgenticServices.sequenceBuilder(SupportPipeline.class)
                .subAgents(scheduler(model, tracker(), writer()), new Summarizer())
                .outputKey("summary")
                .build();

        assertThat(pipeline.process(TICKET)).isEqualTo("summary of reply using tracker output and nothing");
    }

    @Test
    void shouldBeReportedAsAStar() {
        MockDecisionModel model = model(0.9, Map.of());

        assertThat(planner(model).build().topology()).isEqualTo(AgenticSystemTopology.STAR);
        assertThat(HtmlReportGenerator.generateTopology(scheduler(model, tracker(), writer())))
                .contains("<span class=\"topology-badge star\">Star</span>")
                .contains("<span class=\"agent-name\">tracker</span>")
                .contains("<span class=\"agent-name\">writer</span>");
    }

    // ── Delayed answers ──

    private AgenticScopeFunction<Object> asyncTracker(String outputKey) {
        return agent(
                        "tracker",
                        outputKey,
                        scope -> {
                            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
                            return "tracker output";
                        },
                        input("ticket"))
                .async(true)
                .build();
    }

    @Test
    void shouldWaitForTheOutputOfAnAsyncSubagentBeforeDeciding() {
        MockDecisionModel model = modelReachingTheGoalWithAReply(Map.of("tracker", 0.9, "writer", 0.6));

        String reply = scheduler(model, asyncTracker("orderStatus"), writer()).handle(TICKET);

        assertThat(reply).isEqualTo("reply using tracker output and nothing");
        assertThat(model.requests().get(1).input())
                .isEqualTo(FieldsInput.from(Map.of("ticket", TICKET, "orderStatus", "tracker output")));
    }

    @Test
    void shouldRetryAFailedAsyncSubagentWithoutDeadlocking() {
        MockDecisionModel model = modelReachingTheGoalWithAReply(Map.of("tracker", 0.9, "writer", 0.6));
        AtomicInteger attempts = new AtomicInteger();
        AgenticScopeFunction<Object> flakyAsyncTracker = agent(
                        "tracker",
                        "orderStatus",
                        scope -> {
                            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
                            if (attempts.incrementAndGet() == 1) {
                                throw new IllegalStateException("tracker temporarily down");
                            }
                            return "tracker output";
                        },
                        input("ticket"))
                .async(true)
                .build();
        SupportDesk desk = AgenticServices.plannerBuilder(SupportDesk.class)
                .subAgents(flakyAsyncTracker, writer())
                .outputKey("reply")
                .errorHandler(errorContext -> ErrorRecoveryResult.retry())
                .planner(() -> planner(model).build())
                .build();

        String reply = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> desk.handle(TICKET));

        assertThat(reply).isEqualTo("reply using tracker output and nothing");
        assertThat(attempts).hasValue(2);
        assertThat(invoked).containsExactly("tracker", "tracker", "writer");
    }

    @Test
    void shouldFailWhenTheDelayedOutputOfASubagentHasNoOutputKey() {
        MockDecisionModel model = model(0.1, Map.of("tracker", 0.9));

        assertThatThrownBy(() -> scheduler(model, asyncTracker(null), writer()).handle(TICKET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'tracker'")
                .hasMessageContaining("output key");
        assertThat(model.requests()).hasSize(1);
    }

    // ── Validation and failures ──

    @Test
    void shouldRejectANullDecisionModel() {
        assertThatThrownBy(() -> planner(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decisionModel");
    }

    @Test
    void shouldRejectAMissingOrBlankGoal() {
        MockDecisionModel model = model(0.9, Map.of());

        for (String goal : new String[] {null, "", "  "}) {
            assertThatThrownBy(() -> planner(model).goal(goal).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("goal");
        }
    }

    @Test
    void shouldRejectAMissingThreshold() {
        MockDecisionModel model = model(0.9, Map.of());

        assertThatThrownBy(() -> DecisionSchedulerPlanner.builder()
                        .decisionModel(model)
                        .goal(GOAL)
                        .activationThreshold(0.5)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Goal threshold must be set");
        assertThatThrownBy(() -> DecisionSchedulerPlanner.builder()
                        .decisionModel(model)
                        .goal(GOAL)
                        .goalThreshold(0.5)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Activation threshold must be set");
    }

    @Test
    void shouldRejectAThresholdOutsideZeroToOne() {
        MockDecisionModel model = model(0.9, Map.of());

        for (double threshold : new double[] {0.0, 1.0, -0.1, 1.5, Double.NaN}) {
            assertThatThrownBy(() -> planner(model).goalThreshold(threshold).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Goal threshold must be greater than 0.0 and less than 1.0");
            assertThatThrownBy(() -> planner(model).activationThreshold(threshold).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Activation threshold must be greater than 0.0 and less than 1.0");
        }
    }

    @Test
    void shouldRejectAMaximumNumberOfInvocationsLessThanOne() {
        MockDecisionModel model = model(0.9, Map.of());

        assertThatThrownBy(() -> planner(model).maxInvocations(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Max invocations must be at least 1");
    }

    @Test
    void shouldAllowTenInvocationsByDefault() {
        MockDecisionModel model = model(0.1, Map.of("rewriter", 0.9));

        scheduler(model, rewriter()).handle(TICKET);

        assertThat(invoked).hasSize(10);
    }

    @Test
    void shouldAskTheReplacedQuestionsWithTheReplacedLevels() {
        List<String> levels = List.of("NO", "MAYBE", "YES");
        // the middle level of three is scored 0.5, which reaches the activation threshold
        MockDecisionModel model = MockDecisionModel.thatAnswers(request -> Map.of(
                GOAL_QUESTION_NAME, YesNoAnswer.from(0.1),
                "tracker", ScaleAnswer.builder().mean(1.0).build()));
        SupportDesk scheduler = scheduler(
                SupportDesk.class,
                () -> planner(model)
                        .goalQuestionTemplate(PromptTemplate.from("Is it done? {{goal}}"))
                        .usefulnessQuestionTemplate(
                                PromptTemplate.from("Should {{name}} run now? {{description}}. Goal: {{goal}}"))
                        .usefulnessLevels(levels)
                        .build(),
                tracker());

        scheduler.handle(TICKET);

        assertThat(invoked).containsExactly("tracker");
        Map<String, ?> questions = model.request().questions();
        assertThat(questions.get(GOAL_QUESTION_NAME)).isEqualTo(YesNoQuestion.from("Is it done? " + GOAL));
        assertThat(questions.get("tracker"))
                .isEqualTo(ScaleQuestion.from(
                        "Should tracker run now? The tracker agent. Goal: " + GOAL, levels));
    }

    @Test
    void shouldRejectInvalidQuestionTemplatesAndLevels() {
        MockDecisionModel model = model(0.9, Map.of());

        assertThatThrownBy(() -> planner(model)
                        .goalQuestionTemplate(PromptTemplate.from("Is {{goal}} reached for {{customer}}?"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{{goal}}");
        assertThatThrownBy(() -> planner(model)
                        .usefulnessQuestionTemplate(PromptTemplate.from("Is it useful to reach {{goal}}?"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{{name}} or {{description}}");
        assertThatThrownBy(() -> planner(model)
                        .usefulnessQuestionTemplate(PromptTemplate.from("Is {{name}} useful for {{customer}}?"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{{name}}, {{description}} and {{goal}}");
        assertThatThrownBy(() -> planner(model).usefulnessLevels(List.of("USEFUL")).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 levels");
    }

    @Test
    void shouldRejectAPlannerInstanceInitializedTwice() {
        MockDecisionModel model = modelReachingTheGoalWithAReply(Map.of("tracker", 0.9, "writer", 0.6));
        DecisionSchedulerPlanner planner = planner(model).build();
        SupportDesk scheduler = scheduler(SupportDesk.class, () -> planner, tracker(), writer());

        assertThat(scheduler.handle(TICKET)).isEqualTo("reply using tracker output and nothing");

        assertThatThrownBy(() -> scheduler.handle(TICKET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be reused");
    }

    @Test
    void shouldRejectAnUntypedScheduler() {
        MockDecisionModel model = model(0.1, Map.of("tracker", 0.9));

        UntypedAgent scheduler = AgenticServices.plannerBuilder()
                .subAgents(tracker(), writer())
                .planner(() -> planner(model).build())
                .build();

        assertThatThrownBy(() -> scheduler.invoke(Map.of("ticket", TICKET)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("typed agent interface");
        assertThat(invoked).isEmpty();
    }

    @Test
    void shouldRejectAnExcludedOutputKeyThatNoSubagentWrites() {
        MockDecisionModel model = model(0.1, Map.of("tracker", 0.9));

        assertThatThrownBy(() -> scheduler(
                                SupportDesk.class,
                                () -> planner(model).excludedOutputKeys("orderStats").build(),
                                tracker(),
                                writer())
                        .handle(TICKET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'orderStats'")
                .hasMessageContaining("[orderStatus, reply]");
        assertThat(invoked).isEmpty();
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void shouldRejectTheExclusionOfTheOutputKeyOfTheScheduler() {
        MockDecisionModel model = model(0.1, Map.of("tracker", 0.9));

        assertThatThrownBy(() -> scheduler(
                                SupportDesk.class,
                                () -> planner(model).excludedOutputKeys("reply").build(),
                                tracker(),
                                writer())
                        .handle(TICKET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'reply'")
                .hasMessageContaining("output key of the planner agent");
        assertThat(invoked).isEmpty();
        assertThat(model.requests()).isEmpty();
    }

    /** A subagent named "tracker" whatever its label, under which its invocations are recorded. */
    private AgenticScopeFunction<Object> trackerLabelled(String label) {
        return AgenticServices.<Object>nonAiAgentBuilder(scope -> {
                    invoked.add(label);
                    return label + " output";
                })
                .name("tracker")
                .description("The " + label + " tracker")
                .inputs(input("ticket"))
                .outputKey(label + "Status")
                .build();
    }

    @Test
    void shouldTellApartByTheirIdsTheSubagentsWithTheSameName() {
        MockDecisionModel model = model(0.1, Map.of("tracker$0", 0.6, "tracker$1", 0.9));

        scheduler(model, trackerLabelled("first"), trackerLabelled("second")).handle(TICKET);

        // the second one is the most useful, and once it ran only the first one can still run
        assertThat(invoked).containsExactly("second", "first");
        Map<String, ?> firstQuestions = model.requests().get(0).questions();
        assertThat(firstQuestions).containsOnlyKeys(GOAL_QUESTION_NAME, "tracker$0", "tracker$1");
        assertThat(firstQuestions.get("tracker$0"))
                .isEqualTo(ScaleQuestion.from(
                        "How useful would it be to run the agent 'tracker' (The first tracker) now, to reach this "
                                + "goal: " + GOAL + "?",
                        DEFAULT_USEFULNESS_LEVELS));
        assertThat(firstQuestions.get("tracker$1"))
                .isEqualTo(ScaleQuestion.from(
                        "How useful would it be to run the agent 'tracker' (The second tracker) now, to reach this "
                                + "goal: " + GOAL + "?",
                        DEFAULT_USEFULNESS_LEVELS));
        assertThat(questionNames(model.requests().get(1))).containsExactly(GOAL_QUESTION_NAME, "tracker$0");
    }

    @Test
    void shouldAcceptASubagentNamedLikeTheGoalQuestion() {
        MockDecisionModel model = model(0.1, Map.of(GOAL_QUESTION_NAME + "$0", 0.9));

        scheduler(model, agent(GOAL_QUESTION_NAME, "orderStatus", input("ticket")).build())
                .handle(TICKET);

        assertThat(invoked).containsExactly(GOAL_QUESTION_NAME);
        assertThat(questionNames(model.request())).containsExactly(GOAL_QUESTION_NAME, GOAL_QUESTION_NAME + "$0");
    }

    @Test
    void shouldPropagateADecisionModelFailure() {
        MockDecisionModel model = MockDecisionModel.thatFails(new RuntimeException("decision failed"));

        assertThatThrownBy(() -> scheduler(model, tracker(), writer()).handle(TICKET))
                .hasMessageContaining("decision failed");
        assertThat(invoked).isEmpty();
    }

    @Test
    void shouldPropagateARefusalOfTheDecisionModel() {
        MockDecisionModel model = MockDecisionModel.thatAnswersQuestions(question -> RefusalAnswer.of());

        assertThatThrownBy(() -> scheduler(model, tracker(), writer()).handle(TICKET))
                .isInstanceOf(ContentFilteredException.class);
        assertThat(invoked).isEmpty();
    }

    // ── Token usage ──

    @Test
    void shouldCountEveryDecisionCallOnTheScheduler() {
        DecisionModelMock model = modelReachingTheGoalWithAReply(Map.of("tracker", 0.9, "advisor", 0.2, "writer", 0.6))
                .withModelName("decision-model")
                .withTokenUsage(new TokenUsage(40, 2));
        AgentMonitor monitor = new AgentMonitor();
        SupportDesk desk = AgenticServices.plannerBuilder(SupportDesk.class)
                .subAgents(tracker(), advisor(), writer())
                .outputKey("reply")
                .planner(() -> planner(model).build())
                .listener(monitor)
                .build();

        desk.handle(TICKET);

        // one decision before each of the two invocations, and the one finding the goal reached
        assertThat(model.requests()).hasSize(3);
        var invocation = monitor.successfulExecutions().get(0).topLevelInvocations();
        assertThat(invocation.tokenUsage()).contains(new TokenUsage(120, 6));
        assertThat(invocation.modelName()).contains("decision-model");
    }
}
