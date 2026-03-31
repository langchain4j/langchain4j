package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.internal.PendingResponse;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.impl.LoopPlanner;
import dev.langchain4j.agentic.workflow.impl.SequentialPlanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class RecoverabilityTest {

    @AfterEach
    void cleanup() {
        AgenticScopePersister.setStore(null);
    }

    // --- PendingResponse tests ---

    @Test
    void pendingResponse_initiallyNotDone() {
        PendingResponse<String> pending = new PendingResponse<>("test-id");

        assertThat(pending.isDone()).isFalse();
        assertThat(pending.responseId()).isEqualTo("test-id");
        assertThat(pending.toString()).isEqualTo("<pending:test-id>");
    }

    @Test
    void pendingResponse_completeUnblocks() throws Exception {
        PendingResponse<String> pending = new PendingResponse<>("test-id");
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread reader = new Thread(() -> {
            result.set(pending.blockingGet());
            latch.countDown();
        });
        reader.start();

        // Complete from another thread
        Thread.sleep(50);
        assertThat(pending.isDone()).isFalse();
        pending.complete("hello");

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(result.get()).isEqualTo("hello");
        assertThat(pending.isDone()).isTrue();
        assertThat(pending.toString()).isEqualTo("hello");
    }

    @Test
    void pendingResponse_completeReturnsFalseIfAlreadyDone() {
        PendingResponse<String> pending = new PendingResponse<>("test-id");

        assertThat(pending.complete("first")).isTrue();
        assertThat(pending.complete("second")).isFalse();
        assertThat(pending.blockingGet()).isEqualTo("first");
    }

    @Test
    void pendingResponse_blockingGetWithTimeout() throws Exception {
        PendingResponse<String> pending = new PendingResponse<>("test-id");
        pending.complete("value");

        String result = pending.blockingGet(1, TimeUnit.SECONDS);
        assertThat(result).isEqualTo("value");
    }

    @Test
    void pendingResponse_serializationRoundTrip() {
        JsonInMemoryAgenticScopeStore store = new JsonInMemoryAgenticScopeStore();
        AgenticScopePersister.setStore(store);
        AgenticScopeRegistry registry = new AgenticScopeRegistry("test-agent");
        DefaultAgenticScope scope = registry.create("test-memory");

        PendingResponse<String> pending = new PendingResponse<>("approval-id");
        scope.writeState("approval", pending);

        String json = AgenticScopeSerializer.toJson(scope);
        assertThat(json).contains("approval-id");

        DefaultAgenticScope restored = AgenticScopeSerializer.fromJson(json);
        Object restoredValue = restored.state().get("approval");
        assertThat(restoredValue).isInstanceOf(PendingResponse.class);

        PendingResponse<?> restoredPending = (PendingResponse<?>) restoredValue;
        assertThat(restoredPending.responseId()).isEqualTo("approval-id");
        assertThat(restoredPending.isDone()).isFalse();
    }

    // --- completePendingResponse tests ---

    @Test
    void completePendingResponse_completesMatchingResponse() {
        AgenticScopeRegistry registry = new AgenticScopeRegistry("test-agent");
        DefaultAgenticScope scope = registry.create("test");
        PendingResponse<String> pending = new PendingResponse<>("response-1");
        scope.writeState("humanInput", pending);

        assertThat(scope.pendingResponseIds()).containsExactly("response-1");

        boolean completed = scope.completePendingResponse("response-1", "user said yes");
        assertThat(completed).isTrue();
        assertThat(pending.isDone()).isTrue();
        assertThat(pending.blockingGet()).isEqualTo("user said yes");
        assertThat(scope.pendingResponseIds()).isEmpty();
    }

    @Test
    void completePendingResponse_returnsFalseForUnknownId() {
        AgenticScopeRegistry registry = new AgenticScopeRegistry("test-agent");
        DefaultAgenticScope scope = registry.create("test");
        PendingResponse<String> pending = new PendingResponse<>("response-1");
        scope.writeState("humanInput", pending);

        boolean completed = scope.completePendingResponse("unknown-id", "value");
        assertThat(completed).isFalse();
        assertThat(pending.isDone()).isFalse();
    }

    @Test
    void pendingResponseIds_returnsOnlyIncomplete() {
        AgenticScopeRegistry registry = new AgenticScopeRegistry("test-agent");
        DefaultAgenticScope scope = registry.create("test");

        PendingResponse<String> pending1 = new PendingResponse<>("id-1");
        PendingResponse<String> pending2 = new PendingResponse<>("id-2");
        pending1.complete("done");

        scope.writeState("key1", pending1);
        scope.writeState("key2", pending2);
        scope.writeState("key3", "regular-value");

        Set<String> ids = scope.pendingResponseIds();
        assertThat(ids).containsExactly("id-2");
    }

    // --- Per-step checkpointing tests ---

    @Test
    void checkpoint_savesForPersistentScope() {
        JsonInMemoryAgenticScopeStore store = new JsonInMemoryAgenticScopeStore();
        AgenticScopePersister.setStore(store);
        AgenticScopeRegistry registry = new AgenticScopeRegistry("test-agent");

        DefaultAgenticScope scope = registry.create("mem-1");

        // Initial save from create/register
        assertThat(store.getAllKeys()).hasSize(1);

        scope.writeState("step", "first");
        scope.checkpoint(registry);

        // Verify checkpoint was saved with updated state
        DefaultAgenticScope loaded = store.load(new AgenticScopeKey("test-agent", "mem-1")).orElse(null);
        assertThat(loaded).isNotNull();
        assertThat(loaded.state().get("step")).isEqualTo("first");
    }

    @Test
    void checkpoint_noOpForNonPersistentScope() {
        AgenticScopeRegistry registry = new AgenticScopeRegistry("test-agent");
        DefaultAgenticScope scope = registry.createEphemeralAgenticScope();

        // Should not throw
        scope.checkpoint(registry);
    }

    // --- Planner executionState / restoreExecutionState tests ---

    @Test
    void sequentialPlanner_executionStateAndRestore() {
        List<AgentInstance> subagents = List.of(
                mockAgentInstance("sub-1"),
                mockAgentInstance("sub-2"),
                mockAgentInstance("sub-3")
        );

        SequentialPlanner planner = new SequentialPlanner();
        AgenticScopeRegistry registry = new AgenticScopeRegistry("test-agent");
        DefaultAgenticScope scope = registry.create("test");

        planner.init(new InitPlanningContext(scope, mockAgentInstance("seq-agent"), subagents));

        // Initially no state to save (cursor=0)
        assertThat(planner.executionState()).isEmpty();

        // After firstAction (which calls nextAction), cursor advances to 1
        try {
            planner.nextAction(new dev.langchain4j.agentic.planner.PlanningContext(scope, null));
        } catch (ClassCastException ignored) {
        }
        // executionState returns cursor-1=0 (the agent that was just scheduled)
        assertThat(planner.executionState()).isEqualTo(Map.of("cursor", 0));

        try {
            planner.nextAction(new dev.langchain4j.agentic.planner.PlanningContext(scope, null));
        } catch (ClassCastException ignored) {
        }
        assertThat(planner.executionState()).isEqualTo(Map.of("cursor", 1));

        try {
            planner.nextAction(new dev.langchain4j.agentic.planner.PlanningContext(scope, null));
        } catch (ClassCastException ignored) {
        }
        assertThat(planner.executionState()).isEqualTo(Map.of("cursor", 2));
        assertThat(planner.terminated()).isTrue();
    }

    @Test
    void sequentialPlanner_restoreResumeFromCursor() {
        List<AgentInstance> subagents = List.of(
                mockAgentInstance("sub-1"),
                mockAgentInstance("sub-2"),
                mockAgentInstance("sub-3")
        );

        // Create a new planner and restore cursor=2 (agents[0] and [1] already ran)
        SequentialPlanner planner = new SequentialPlanner();
        AgenticScopeRegistry registry = new AgenticScopeRegistry("test-agent");
        DefaultAgenticScope scope = registry.create("test");

        planner.init(new InitPlanningContext(scope, mockAgentInstance("seq-agent"), subagents));
        planner.restoreExecutionState(Map.of("cursor", 2));

        // cursor=2, not terminated (size=3), nextAction should schedule agents[2]
        assertThat(planner.terminated()).isFalse();

        try {
            planner.nextAction(new dev.langchain4j.agentic.planner.PlanningContext(scope, null));
        } catch (ClassCastException ignored) {
        }
        // cursor is now 3, terminated
        assertThat(planner.terminated()).isTrue();
    }

    @Test
    void loopPlanner_executionStateAndRestore() {
        List<AgentInstance> subagents = List.of(
                mockAgentInstance("sub-1"),
                mockAgentInstance("sub-2")
        );

        LoopPlanner planner = new LoopPlanner(10, true, (s, i) -> false, "never");
        AgenticScopeRegistry registry = new AgenticScopeRegistry("test-agent");
        DefaultAgenticScope scope = registry.create("test");

        planner.init(new InitPlanningContext(scope, mockAgentInstance("loop-agent"), subagents));

        // Initial state
        assertThat(planner.executionState()).isEqualTo(Map.of("cursor", 0, "iteration", 1));

        // Simulate: firstAction calls agents[0], then nextAction advances cursor
        try {
            planner.firstAction(new dev.langchain4j.agentic.planner.PlanningContext(scope, null));
        } catch (ClassCastException ignored) {
        }

        try {
            planner.nextAction(new dev.langchain4j.agentic.planner.PlanningContext(scope, null));
        } catch (ClassCastException ignored) {
        }
        // After nextAction: cursor=(0+1)%2=1
        assertThat(planner.executionState()).isEqualTo(Map.of("cursor", 1, "iteration", 1));
    }

    @Test
    void loopPlanner_restoreResumesFromSavedState() {
        List<AgentInstance> subagents = List.of(
                mockAgentInstance("sub-1"),
                mockAgentInstance("sub-2")
        );

        LoopPlanner planner = new LoopPlanner(10, true, (s, i) -> false, "never");
        AgenticScopeRegistry registry = new AgenticScopeRegistry("test-agent");
        DefaultAgenticScope scope = registry.create("test");

        planner.init(new InitPlanningContext(scope, mockAgentInstance("loop-agent"), subagents));
        planner.restoreExecutionState(Map.of("cursor", 1, "iteration", 3));

        // firstAction should return call(agents[1]) since cursor=1
        assertThat(planner.executionState()).isEqualTo(Map.of("cursor", 1, "iteration", 3));
    }

    @Test
    void loopPlanner_exitConditionReturnsDone() {
        List<AgentInstance> subagents = List.of(mockAgentInstance("sub-1"));

        LoopPlanner planner = new LoopPlanner(5, true, (s, i) -> true, "always exit");
        AgenticScopeRegistry registry = new AgenticScopeRegistry("test-agent");
        DefaultAgenticScope scope = registry.create("test");

        planner.init(new InitPlanningContext(scope, mockAgentInstance("loop-agent"), subagents));
        planner.restoreExecutionState(Map.of("cursor", 0, "iteration", 5));

        // nextAction: cursor=(0+1)%1=0 wraps, agentCursor==0, checks exit:
        // exitCondition returns true → done
        var action = planner.nextAction(new dev.langchain4j.agentic.planner.PlanningContext(scope, null));
        assertThat(action.isDone()).isTrue();
    }

    // --- Helper methods ---

    private static AgentInstance mockAgentInstance(String agentId) {
        return new AgentInstance() {
            @Override public Class<?> type() { return Object.class; }
            @Override public Class<? extends Planner> plannerType() { return null; }
            @Override public String name() { return agentId; }
            @Override public String agentId() { return agentId; }
            @Override public String description() { return ""; }
            @Override public Type outputType() { return Object.class; }
            @Override public String outputKey() { return null; }
            @Override public boolean async() { return false; }
            @Override public List<dev.langchain4j.agentic.planner.AgentArgument> arguments() { return List.of(); }
            @Override public List<AgentInstance> subagents() { return List.of(); }
            @Override public AgentInstance parent() { return null; }
            @Override public AgenticSystemTopology topology() { return AgenticSystemTopology.SEQUENCE; }
        };
    }
}
