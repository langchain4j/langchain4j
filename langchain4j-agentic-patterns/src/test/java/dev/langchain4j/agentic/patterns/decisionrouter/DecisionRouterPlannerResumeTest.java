package dev.langchain4j.agentic.patterns.decisionrouter;

import static dev.langchain4j.agentic.patterns.decisionrouter.DecisionRouterPlanner.QUESTION_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

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
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DecisionRouterPlannerResumeTest {

    private static final String REQUEST = "I was injured in a car accident, what should I do?";

    public static class MedicalExpert {
        final AtomicInteger calls = new AtomicInteger();

        @Agent(description = "Answers medical questions", outputKey = "medicalResponse")
        public String medical(@V("request") String request) {
            calls.incrementAndGet();
            return "medical answer";
        }
    }

    public interface ResumableRouter extends AgenticScopeAccess {

        @Agent
        String ask(@MemoryId String sessionId, @V("request") String request);
    }

    public interface ResumableMultiRouter extends AgenticScopeAccess {

        @Agent
        Map<String, Object> ask(@MemoryId String sessionId, @V("request") String request);
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

    private final MedicalExpert medicalExpert = new MedicalExpert();

    // the agent waiting for a human is named after its method, askUser
    private final HumanInTheLoop legalReviewer = AgenticServices.humanInTheLoopBuilder()
            .description("A lawyer answering legal questions")
            .outputKey("legalResponse")
            .responseProvider(scope -> new SuspendedResponse<>("legal-review"))
            .build();

    @AfterEach
    void cleanup() {
        AgenticScopePersister.setStore(null);
    }

    private static void simulateCrash(Object agenticSystem) {
        ((AgenticScopeOwner) agenticSystem).registry().clearInMemory();
    }

    @Test
    void shouldResumeTheSameAgentWithoutAskingTheDecisionModelAgain() {
        AgenticScopePersister.setStore(new JsonInMemoryStore());
        AtomicInteger decisions = new AtomicInteger();
        // asking again would route to the medical expert
        DecisionModelMock model = DecisionModelMock.thatAnswers(request -> Map.of(
                QUESTION_NAME,
                ChoiceAnswer.builder()
                        .value(decisions.getAndIncrement() == 0 ? "askUser" : "medical")
                        .build()));
        ResumableRouter router = AgenticServices.plannerBuilder(ResumableRouter.class)
                .subAgents(medicalExpert, legalReviewer)
                .outputKey("response")
                .planner(() -> new DecisionRouterPlanner(model))
                .build();

        suspensionOf(() -> router.ask("s1", REQUEST));
        simulateCrash(router);
        router.getAgenticScope("s1").completePendingResponse("legal answer");

        assertThat(router.ask("s1", REQUEST)).isEqualTo("legal answer");
        assertThat(model.requests()).hasSize(1);
        assertThat(medicalExpert.calls).hasValue(0);
    }

    @Test
    void shouldKeepTheOutputsOfTheAgentsThatCompletedBeforeTheSuspension() {
        AgenticScopePersister.setStore(new JsonInMemoryStore());
        DecisionModelMock model = DecisionModelMock.thatAlwaysAnswers(
                Map.of("medical", YesNoAnswer.of(0.8), "askUser", YesNoAnswer.of(0.7)));
        ResumableMultiRouter router = AgenticServices.plannerBuilder(ResumableMultiRouter.class)
                .subAgents(medicalExpert, legalReviewer)
                .outputKey("responses")
                .planner(() -> new DecisionRouterPlanner(model, 0.5))
                .build();

        suspensionOf(() -> router.ask("s2", REQUEST));
        simulateCrash(router);
        router.getAgenticScope("s2").completePendingResponse("legal answer");

        assertThat(router.ask("s2", REQUEST))
                .containsOnly(entry("medical", "medical answer"), entry("askUser", "legal answer"));
        assertThat(model.requests()).hasSize(1);
        assertThat(medicalExpert.calls).hasValue(1);
    }

    @Test
    void shouldReturnTheRestoredOutputsWhenAllTheActivatedAgentsHadCompleted() {
        DecisionModelMock model = DecisionModelMock.thatAlwaysAnswers(
                Map.of("medical", YesNoAnswer.of(0.8), "askUser", YesNoAnswer.of(0.7)));
        ResumableMultiRouter router = AgenticServices.plannerBuilder(ResumableMultiRouter.class)
                .subAgents(medicalExpert, legalReviewer)
                .planner(() -> new DecisionRouterPlanner(model, 0.5))
                .build();
        DefaultAgenticScope scope = new AgenticScopeRegistry("router").create("s3");
        scope.writeState("request", REQUEST);
        InitPlanningContext initPlanningContext =
                new InitPlanningContext(scope, (AgentInstance) router, ((AgentInstance) router).subagents());

        // the first run activates both agents, and both complete before the interruption
        DecisionRouterPlanner planner = new DecisionRouterPlanner(model, 0.5);
        planner.init(initPlanningContext);
        planner.firstAction(new PlanningContext(scope, null));
        planner.nextAction(new PlanningContext(scope, invocation("medical", "medical answer")));
        planner.nextAction(new PlanningContext(scope, invocation("askUser", "legal answer")));
        Map<String, Object> executionState = planner.executionState();

        DecisionRouterPlanner resumed = new DecisionRouterPlanner(model, 0.5);
        resumed.init(initPlanningContext);
        resumed.restoreExecutionState(executionState);
        Action action = resumed.firstAction(new PlanningContext(scope, null));

        assertThat(action.isDone()).isTrue();
        assertThat((Map<String, Object>) action.result())
                .containsOnly(entry("medical", "medical answer"), entry("askUser", "legal answer"));
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void shouldHaveNoExecutionStateBeforeDeciding() {
        DecisionRouterPlanner planner =
                new DecisionRouterPlanner(DecisionModelMock.thatAlwaysThrowsExceptionWithMessage("unused"));

        assertThat(planner.executionState()).isEmpty();
    }

    private static AgentInvocation invocation(String agentName, Object output) {
        return new AgentInvocation(Object.class, agentName, agentName, Map.of(), output);
    }

    private static void suspensionOf(Runnable invocation) {
        assertThatThrownBy(invocation::run).isInstanceOf(AgenticSystemSuspendedException.class);
    }
}
