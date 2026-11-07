package dev.langchain4j.agentic.patterns.cascade;

import static dev.langchain4j.agentic.patterns.cascade.ConfidenceCascadePlanner.ANSWER_KEY;
import static dev.langchain4j.agentic.patterns.cascade.ConfidenceCascadePlanner.QUESTION_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.planner.Action;
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
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConfidenceCascadePlannerResumeTest {

    private static final String REQUEST = "How do I descale my coffee machine?";

    public static class FaqAgent {
        final AtomicInteger calls = new AtomicInteger();

        @Agent(description = "Answers frequently asked questions", outputKey = "faqAnswer")
        public String faq(@V("request") String request) {
            calls.incrementAndGet();
            return "faq answer";
        }
    }

    public static class ExpertAgent {
        final AtomicInteger calls = new AtomicInteger();

        @Agent(description = "Answers any question", outputKey = "expertAnswer")
        public String expert(@V("request") String request) {
            calls.incrementAndGet();
            return "expert answer";
        }
    }

    public interface ResumableCascade extends AgenticScopeAccess {

        @Agent
        String ask(@MemoryId String sessionId, @V("request") String request);
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

    private final FaqAgent faqAgent = new FaqAgent();
    private final ExpertAgent expertAgent = new ExpertAgent();

    // the agent waiting for a human is named after its method, askUser
    private final HumanInTheLoop operator = AgenticServices.humanInTheLoopBuilder()
            .description("A support operator")
            .outputKey("operatorAnswer")
            .responseProvider(scope -> new SuspendedResponse<>("support-ticket"))
            .build();

    @AfterEach
    void cleanup() {
        AgenticScopePersister.setStore(null);
    }

    private static void simulateCrash(Object agenticSystem) {
        ((AgenticScopeOwner) agenticSystem).registry().clearInMemory();
    }

    private static DecisionModelMock modelGrading(double probability) {
        return DecisionModelMock.thatAlwaysAnswers(Map.of(QUESTION_NAME, YesNoAnswer.from(probability)));
    }

    private static DecisionModelMock modelGradingInTurn(double... probabilities) {
        AtomicInteger grades = new AtomicInteger();
        return DecisionModelMock.thatAnswers(
                request -> Map.of(QUESTION_NAME, YesNoAnswer.from(probabilities[grades.getAndIncrement()])));
    }

    private static ResumableCascade cascade(DecisionModelMock model, Object... tiers) {
        return AgenticServices.plannerBuilder(ResumableCascade.class)
                .subAgents(tiers)
                .outputKey("answer")
                .planner(() -> new ConfidenceCascadePlanner(model, 0.5))
                .build();
    }

    private static void suspensionOf(Runnable invocation) {
        assertThatThrownBy(invocation::run).isInstanceOf(AgenticSystemSuspendedException.class);
    }

    @Test
    void shouldResumeAtTheTierThatWasWaitingWithoutPayingAgainForTheCheaperOnes() {
        AgenticScopePersister.setStore(new JsonInMemoryStore());
        DecisionModelMock model = modelGrading(0.1);
        ResumableCascade cascade = cascade(model, faqAgent, operator);

        suspensionOf(() -> cascade.ask("s1", REQUEST));
        simulateCrash(cascade);
        cascade.getAgenticScope("s1").completePendingResponse("operator answer");

        assertThat(cascade.ask("s1", REQUEST)).isEqualTo("operator answer");
        assertThat(faqAgent.calls).hasValue(1);
        // the operator is the last tier, so its answer is not graded
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void shouldGradeTheAnswerOfAHumanInTheMiddleOnResume() {
        AgenticScopePersister.setStore(new JsonInMemoryStore());
        DecisionModelMock model = modelGrading(0.1);
        ResumableCascade cascade = cascade(model, faqAgent, operator, expertAgent);

        suspensionOf(() -> cascade.ask("s2", REQUEST));
        simulateCrash(cascade);
        cascade.getAgenticScope("s2").completePendingResponse("operator answer");

        assertThat(cascade.ask("s2", REQUEST)).isEqualTo("expert answer");
        assertThat(faqAgent.calls).hasValue(1);
        assertThat(expertAgent.calls).hasValue(1);
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).input())
                .isEqualTo(FieldsInput.from(Map.of("request", REQUEST, ANSWER_KEY, "operator answer")));
    }

    @Test
    void shouldAcceptTheAnswerOfAHumanInTheMiddleOnResume() {
        AgenticScopePersister.setStore(new JsonInMemoryStore());
        DecisionModelMock model = modelGradingInTurn(0.1, 0.9);
        ResumableCascade cascade = cascade(model, faqAgent, operator, expertAgent);

        suspensionOf(() -> cascade.ask("s3", REQUEST));
        simulateCrash(cascade);
        cascade.getAgenticScope("s3").completePendingResponse("operator answer");

        assertThat(cascade.ask("s3", REQUEST)).isEqualTo("operator answer");
        assertThat(expertAgent.calls).hasValue(0);
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void shouldSuspendAgainWhenResumedBeforeTheHumanAnswered() {
        AgenticScopePersister.setStore(new JsonInMemoryStore());
        DecisionModelMock model = modelGrading(0.1);
        ResumableCascade cascade = cascade(model, faqAgent, operator);

        suspensionOf(() -> cascade.ask("s4", REQUEST));
        simulateCrash(cascade);
        suspensionOf(() -> cascade.ask("s4", REQUEST));

        assertThat(faqAgent.calls).hasValue(1);
        assertThat(model.requests()).hasSize(1);

        cascade.getAgenticScope("s4").completePendingResponse("operator answer");
        assertThat(cascade.ask("s4", REQUEST)).isEqualTo("operator answer");
    }

    // ── Execution state at the planner level ──

    private static AgentInvocation invocation(String agentName, Object output) {
        return new AgentInvocation(Object.class, agentName, agentName, Map.of(), output);
    }

    private static AgentInvocation skippedInvocation(String agentName) {
        return new AgentInvocation(Object.class, agentName, agentName, Map.of(), null, "activation condition is false");
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
    void shouldRestoreTheTierBeingTriedFromTheExecutionState() {
        DecisionModelMock model = modelGrading(0.1);
        AgentInstance cascade = (AgentInstance) cascade(model, faqAgent, expertAgent);
        DefaultAgenticScope scope = new AgenticScopeRegistry("cascade").create("s5");
        scope.writeState("request", REQUEST);
        InitPlanningContext initPlanningContext = new InitPlanningContext(scope, cascade, cascade.subagents());

        // the first tier answers and is rejected before the interruption
        ConfidenceCascadePlanner planner = new ConfidenceCascadePlanner(model, 0.5);
        planner.init(initPlanningContext);
        assertThat(calledAgents(planner.firstAction(new PlanningContext(scope, null))))
                .containsExactly("faq");
        assertThat(calledAgents(planner.nextAction(new PlanningContext(scope, invocation("faq", "faq answer")))))
                .containsExactly("expert");
        Map<String, Object> executionState = planner.executionState();
        assertThat(executionState)
                .containsEntry("confidence-cascade-tier", 1)
                .containsEntry("confidence-cascade-tier-name", "expert")
                .containsEntry("confidence-cascade-pending", false)
                .containsEntry("confidence-cascade-answered", 0);

        ConfidenceCascadePlanner resumed = new ConfidenceCascadePlanner(model, 0.5);
        resumed.init(initPlanningContext);
        resumed.restoreExecutionState(executionState);

        assertThat(calledAgents(resumed.firstAction(new PlanningContext(scope, null))))
                .containsExactly("expert");
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void shouldReturnTheLatestAnswerFromTheScopeWhenTheRemainingTiersAreSkippedAfterARestore() {
        DecisionModelMock model = modelGrading(0.1);
        AgentInstance cascade = (AgentInstance) cascade(model, faqAgent, expertAgent);
        DefaultAgenticScope scope = new AgenticScopeRegistry("cascade").create("s6");
        scope.writeState("request", REQUEST);
        scope.writeState("faqAnswer", "faq answer");

        ConfidenceCascadePlanner resumed = new ConfidenceCascadePlanner(model, 0.5);
        resumed.init(new InitPlanningContext(scope, cascade, cascade.subagents()));
        // the cursor comes back from JSON as a number of any kind
        resumed.restoreExecutionState(Map.of(
                "confidence-cascade-tier",
                1L,
                "confidence-cascade-pending",
                false,
                "confidence-cascade-answered",
                0));

        assertThat(calledAgents(resumed.firstAction(new PlanningContext(scope, null))))
                .containsExactly("expert");
        Action action = resumed.nextAction(new PlanningContext(scope, skippedInvocation("expert")));

        assertThat(action.isDone()).isTrue();
        assertThat(action.result()).isEqualTo("faq answer");
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void shouldFailToResumeWhenTheTierBeingTriedIsNoLongerAtTheSavedPosition() {
        DecisionModelMock model = modelGrading(0.1);
        AgentInstance cascade = (AgentInstance) cascade(model, faqAgent, expertAgent);
        DefaultAgenticScope scope = new AgenticScopeRegistry("cascade").create("s7");
        scope.writeState("request", REQUEST);

        ConfidenceCascadePlanner resumed = new ConfidenceCascadePlanner(model, 0.5);
        resumed.init(new InitPlanningContext(scope, cascade, cascade.subagents()));
        // the state was saved while trying a tier that is no longer the second one
        resumed.restoreExecutionState(Map.of(
                "confidence-cascade-tier", 1,
                "confidence-cascade-tier-name", "human",
                "confidence-cascade-pending", true));

        assertThatThrownBy(() -> resumed.firstAction(new PlanningContext(scope, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'human' in position 1");
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void shouldFailToResumeWhenTheTierBeingTriedNoLongerExists() {
        DecisionModelMock model = modelGrading(0.1);
        AgentInstance cascade = (AgentInstance) cascade(model, faqAgent, expertAgent);
        DefaultAgenticScope scope = new AgenticScopeRegistry("cascade").create("s8");
        scope.writeState("request", REQUEST);

        ConfidenceCascadePlanner resumed = new ConfidenceCascadePlanner(model, 0.5);
        resumed.init(new InitPlanningContext(scope, cascade, cascade.subagents()));
        // the state was saved while trying a third tier, removed since then
        resumed.restoreExecutionState(Map.of(
                "confidence-cascade-tier", 2,
                "confidence-cascade-tier-name", "human",
                "confidence-cascade-pending", true));

        assertThatThrownBy(() -> resumed.firstAction(new PlanningContext(scope, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("subagents of the cascade changed");
    }

    @Test
    void shouldHaveNoExecutionStateBeforeTheFirstTierRuns() {
        ConfidenceCascadePlanner planner =
                new ConfidenceCascadePlanner(DecisionModelMock.thatAlwaysThrowsExceptionWithMessage("unused"), 0.5);

        assertThat(planner.executionState()).isEmpty();
    }
}
