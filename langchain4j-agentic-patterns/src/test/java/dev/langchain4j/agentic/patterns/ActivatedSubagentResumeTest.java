package dev.langchain4j.agentic.patterns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner;
import dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.AgenticScopeStore;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests that the planners selecting their subagents according to their activation conditions resume a composite
 * subagent that was activated before a suspension or a crash, even if its own subagents made its condition false.
 */
class ActivatedSubagentResumeTest {

    public interface InnerWork {

        @Agent
        String work(@V("start") String start);
    }

    public interface ResumableWork extends AgenticScopeAccess {

        @Agent
        String run(@MemoryId String sessionId, @V("start") String start);
    }

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

    private final AtomicInteger starts = new AtomicInteger();

    @AfterEach
    void cleanup() {
        AgenticScopePersister.setStore(null);
    }

    /** A workflow that, once started, makes its own condition false, and then waits for a human. */
    private InnerWork innerWork() {
        return AgenticServices.sequenceBuilder(InnerWork.class)
                .subAgents(
                        AgenticServices.agentAction(scope -> {
                            starts.incrementAndGet();
                            scope.writeState("started", true);
                        }),
                        AgenticServices.humanInTheLoopBuilder()
                                .outputKey("approval")
                                .responseProvider(scope -> new SuspendedResponse<>("review"))
                                .build(),
                        AgenticServices.agentAction(
                                scope -> scope.writeState("goal", "done:" + scope.readState("approval"))))
                .outputKey("goal")
                .activationCondition("the work is not started yet", scope -> !scope.hasState("started"))
                .build();
    }

    private void assertResumed(Supplier<Planner> planner, boolean crash) {
        AgenticScopePersister.setStore(new JsonInMemoryStore());
        ResumableWork system = AgenticServices.plannerBuilder(ResumableWork.class)
                .subAgents(innerWork())
                .outputKey("goal")
                .planner(planner)
                .build();

        assertThatThrownBy(() -> system.run("s1", "now")).isInstanceOf(AgenticSystemSuspendedException.class);
        if (crash) {
            ((AgenticScopeOwner) system).registry().clearInMemory();
        }
        system.getAgenticScope("s1").completePendingResponse("APPROVED");

        assertThat(system.run("s1", "now")).isEqualTo("done:APPROVED");
        assertThat(starts).hasValue(1);
        assertThat(system.getAgenticScope("s1").state().keySet()).noneMatch(key -> key.startsWith("__activated_"));
    }

    @ParameterizedTest(name = "after a crash: {0}")
    @ValueSource(booleans = {false, true})
    void goal_oriented_planner_resumes_an_activated_subagent(boolean crash) {
        assertResumed(GoalOrientedPlanner::new, crash);
    }

    @ParameterizedTest(name = "after a crash: {0}")
    @ValueSource(booleans = {false, true})
    void blackboard_planner_resumes_an_activated_subagent(boolean crash) {
        assertResumed(BlackboardPlanner::new, crash);
    }
}
