package dev.langchain4j.agentic;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.internal.AsyncResponse;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.MonitoredExecution;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import dev.langchain4j.spi.ExecutorProvider;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The root call of an agentic system does not wait for an asynchronous agent whose result nobody reads: it returns
 * while that agent is still running, and the result or the failure of the agent is reported to its listener, while
 * the scope is ended at the end of the root call, without waiting for it.
 */
class DetachedAsyncAgentTest {

    public interface RolloutAnalysis {
        @Agent
        ResultWithAgenticScope<String> analyze(@V("input") String input);
    }

    public interface RolloutAnalysisWithMemory extends AgenticScopeAccess {
        @Agent
        String analyze(@MemoryId String memoryId, @V("input") String input);
    }

    public static class Analysis {
        @Agent(outputKey = "decision")
        public String analyze(@V("input") String input) {
            return "promote " + input;
        }
    }

    public static class RecordingAnalysis {
        final List<String> inputs = new CopyOnWriteArrayList<>();

        @Agent(outputKey = "decision")
        public String analyze(@V("input") String input) {
            inputs.add(input);
            return "promote " + input;
        }
    }

    /**
     * The last step of the workflow, delegating a fix that takes minutes to a remote agent: held until the test
     * releases it.
     */
    public static class Remediation {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Agent(async = true, outputKey = "fix")
        public String remediate(@V("decision") String decision) {
            started.countDown();
            awaitLatch(release);
            return "fixed after " + decision;
        }
    }

    public static class FailingRemediation {
        final CountDownLatch release = new CountDownLatch(1);

        @Agent(async = true, outputKey = "fix")
        public String remediate(@V("decision") String decision) {
            awaitLatch(release);
            throw new IllegalStateException("sandbox unavailable");
        }
    }

    /** Decides nothing for a stable rollout, so that the agents needing the decision are skipped. */
    public static class AnalysisUnlessStable {
        @Agent(outputKey = "decision")
        public String analyze(@V("input") String input) {
            return "stable".equals(input) ? null : "promote " + input;
        }
    }

    public static class OptionalFailingRemediation {
        @Agent(async = true, outputKey = "fix", optional = true)
        public String remediate(@V("decision") String decision) {
            throw new IllegalStateException("sandbox unavailable");
        }
    }

    /** A gate asking for an approval, suspending the agentic system as an A2A client agent does. */
    public static class ApprovalGate {
        @Agent(outputKey = "approval")
        public String approve(AgenticScope scope, @V("decision") String decision) {
            scope.writeState("a2a.response.approve", new SuspendedResponse<>("approval"));
            throw new AgenticSystemSuspendedException(scope);
        }
    }

    /** Reads the scope while running, once released, as an agent taking the {@link AgenticScope} can do. */
    public static class InputReadingRemediation {
        final CountDownLatch release = new CountDownLatch(1);
        final List<String> results = new CopyOnWriteArrayList<>();

        @Agent(async = true, outputKey = "fix")
        public String remediate(AgenticScope scope, @V("decision") String decision) {
            awaitLatch(release);
            String result = "fixed for " + scope.readState("input");
            results.add(result);
            return result;
        }
    }

    public static class NullRemediation {
        @Agent(async = true, outputKey = "fix")
        public String remediate(@V("decision") String decision) {
            return null;
        }
    }

    /**
     * A remote agent asking for more input, as an A2A client agent does: it leaves a suspended response in the
     * scope and suspends the agentic system, then continues with the input provided when invoked again.
     */
    public static class SuspendingRemediation {
        static final String RESPONSE_KEY = "a2a.response.remediate";
        final CountDownLatch release = new CountDownLatch(1);

        @Agent(async = true, outputKey = "fix")
        public String remediate(AgenticScope scope, @V("decision") String decision) {
            awaitLatch(release);
            if (scope.state().get(RESPONSE_KEY) instanceof String input) {
                return "fixed with " + input;
            }
            scope.writeState(RESPONSE_KEY, new SuspendedResponse<>("remediate:task-1"));
            throw new AgenticSystemSuspendedException(scope);
        }
    }

    public static class FailingCheck {
        @Agent(outputKey = "check")
        public String check(@V("decision") String decision) {
            throw new IllegalStateException("check failed");
        }
    }

    public static class Report {
        @Agent(outputKey = "report")
        public String report(@V("fix") String fix) {
            return "report: " + fix;
        }
    }

    static class RecordingListener implements AgentListener {
        final List<String> events = new CopyOnWriteArrayList<>();
        final Map<String, String> beforeThreads = new ConcurrentHashMap<>();
        final Map<String, String> afterThreads = new ConcurrentHashMap<>();
        final Map<String, Object> outputs = new ConcurrentHashMap<>();

        @Override
        public void beforeAgentInvocation(AgentRequest agentRequest) {
            events.add("before:" + agentRequest.agent().name());
            beforeThreads.put(
                    agentRequest.agent().name(), Thread.currentThread().getName());
        }

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            events.add("after:" + agentResponse.agent().name());
            afterThreads.put(
                    agentResponse.agent().name(), Thread.currentThread().getName());
            if (agentResponse.output() != null) {
                outputs.put(agentResponse.agent().name(), agentResponse.output());
            }
        }

        @Override
        public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
            events.add("error:" + agentInvocationError.agentName());
        }

        @Override
        public void beforeAgenticScopeDestroyed(AgenticScope agenticScope) {
            events.add("destroyed");
        }

        @Override
        public void onAgenticSystemSuspended(AgenticScope agenticScope) {
            events.add("suspended");
        }

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }
    }

    @AfterEach
    void resetStore() {
        AgenticScopePersister.setStore(null);
    }

    @Test
    void root_returns_before_a_trailing_async_agent_completes_and_its_result_reaches_the_listener() {
        Remediation remediation = new Remediation();
        RecordingListener listener = new RecordingListener();
        RolloutAnalysis workflow = AgenticServices.sequenceBuilder(RolloutAnalysis.class)
                .subAgents(new Analysis(), remediation)
                .outputKey("decision")
                .listener(listener)
                .build();

        ResultWithAgenticScope<String> result = workflow.analyze("canary");

        assertThat(result.result()).isEqualTo("promote canary");
        // the ephemeral scope was destroyed at the end of the root call without waiting for the agent
        awaitLatch(remediation.started);
        assertThat(listener.events).contains("before:remediate", "destroyed").doesNotContain("after:remediate");
        // the live scope still holds the pending response
        AgenticScope scope = result.agenticScope();
        assertThat(scope.state().get("fix")).isInstanceOf(AsyncResponse.class);
        assertThat(((AsyncResponse<?>) scope.state().get("fix")).isDone()).isFalse();

        remediation.release.countDown();
        await().atMost(10, SECONDS).until(() -> scope.state().get("fix") instanceof String);

        assertThat(listener.outputs.get("remediate")).isEqualTo("fixed after promote canary");
        assertThat(listener.events).containsSubsequence("destroyed", "after:remediate");
        // both events of the agent were notified on the thread of its executor, as for every invocation
        assertThat(listener.beforeThreads.get("remediate"))
                .isEqualTo(listener.afterThreads.get("remediate"))
                .isNotEqualTo(Thread.currentThread().getName());
        assertThat(scope.state().get("fix")).isEqualTo("fixed after promote canary");
    }

    @Test
    void root_waits_for_an_async_agent_whose_result_it_reads() throws Exception {
        Remediation remediation = new Remediation();
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(new Analysis(), remediation)
                .outputKey("fix")
                .build();

        CompletableFuture<Object> rootCall =
                CompletableFuture.supplyAsync(() -> workflow.invoke(Map.of("input", "canary")));
        awaitLatch(remediation.started);

        assertThat(rootCall).isNotDone();
        remediation.release.countDown();
        assertThat(rootCall.get(5, SECONDS)).isEqualTo("fixed after promote canary");
    }

    @Test
    void a_later_agent_reading_the_async_result_waits_for_it() throws Exception {
        Remediation remediation = new Remediation();
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(new Analysis(), remediation, new Report())
                .outputKey("report")
                .build();

        CompletableFuture<Object> rootCall =
                CompletableFuture.supplyAsync(() -> workflow.invoke(Map.of("input", "canary")));
        awaitLatch(remediation.started);

        assertThat(rootCall).isNotDone();
        remediation.release.countDown();
        assertThat(rootCall.get(5, SECONDS)).isEqualTo("report: fixed after promote canary");
    }

    @Test
    void failed_root_call_does_not_wait_for_a_detached_agent_whose_result_still_reaches_the_listener() {
        Remediation remediation = new Remediation();
        RecordingListener listener = new RecordingListener();
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(new Analysis(), remediation, new FailingCheck())
                .listener(listener)
                .build();

        assertThatThrownBy(() -> workflow.invoke(Map.of("input", "canary")))
                .isInstanceOf(AgentInvocationException.class);
        assertThat(listener.events).doesNotContain("after:remediate");

        remediation.release.countDown();
        await().atMost(10, SECONDS).until(() -> listener.events.contains("after:remediate"));

        assertThat(listener.outputs.get("remediate")).isEqualTo("fixed after promote canary");
    }

    @Test
    void failure_of_a_detached_agent_is_reported_to_the_listener_and_the_error_handler_only() {
        FailingRemediation remediation = new FailingRemediation();
        RecordingListener listener = new RecordingListener();
        List<String> handledErrors = new CopyOnWriteArrayList<>();
        RolloutAnalysis workflow = AgenticServices.sequenceBuilder(RolloutAnalysis.class)
                .subAgents(new Analysis(), remediation)
                .outputKey("decision")
                .listener(listener)
                .errorHandler(errorContext -> {
                    handledErrors.add(errorContext.agentName());
                    return ErrorRecoveryResult.throwException();
                })
                .build();

        ResultWithAgenticScope<String> result = workflow.analyze("canary");
        assertThat(result.result()).isEqualTo("promote canary");

        remediation.release.countDown();
        await().atMost(10, SECONDS).until(() -> !handledErrors.isEmpty());

        assertThat(listener.events)
                .containsSubsequence("destroyed", "error:remediate")
                .doesNotContain("after:remediate");
        assertThat(handledErrors).containsExactly("remediate");
        // the failure is left in the scope, so that reading the result still fails
        AgenticScope scope = result.agenticScope();
        assertThat(scope.state().get("fix")).isInstanceOf(AsyncResponse.class);
        assertThatThrownBy(() -> scope.readState("fix")).isInstanceOf(AgentInvocationException.class);
    }

    @Test
    void recovered_failure_of_a_detached_agent_settles_the_recovery_result() {
        FailingRemediation remediation = new FailingRemediation();
        RecordingListener listener = new RecordingListener();
        RolloutAnalysis workflow = AgenticServices.sequenceBuilder(RolloutAnalysis.class)
                .subAgents(new Analysis(), remediation)
                .outputKey("decision")
                .listener(listener)
                .errorHandler(errorContext -> ErrorRecoveryResult.result("manual fix"))
                .build();

        ResultWithAgenticScope<String> result = workflow.analyze("canary");
        remediation.release.countDown();
        await().atMost(10, SECONDS)
                .until(() -> "manual fix".equals(result.agenticScope().state().get("fix")));

        assertThat(result.agenticScope().state().get("fix")).isEqualTo("manual fix");
        assertThat(listener.events).contains("error:remediate").doesNotContain("after:remediate");
    }

    @Test
    void null_result_of_a_detached_agent_removes_its_key() {
        RecordingListener listener = new RecordingListener();
        RolloutAnalysis workflow = AgenticServices.sequenceBuilder(RolloutAnalysis.class)
                .subAgents(new Analysis(), new NullRemediation())
                .outputKey("decision")
                .listener(listener)
                .build();

        ResultWithAgenticScope<String> result = workflow.analyze("canary");
        await().atMost(10, SECONDS).until(() -> !result.agenticScope().hasState("fix"));

        assertThat(result.agenticScope().hasState("fix")).isFalse();
    }

    @Test
    void registered_scope_settles_the_result_and_is_not_evicted() {
        Remediation remediation = new Remediation();
        RecordingListener listener = new RecordingListener();
        RolloutAnalysisWithMemory workflow = AgenticServices.sequenceBuilder(RolloutAnalysisWithMemory.class)
                .subAgents(new Analysis(), remediation)
                .outputKey("decision")
                .listener(listener)
                .build();

        assertThat(workflow.analyze("rollout-1", "canary")).isEqualTo("promote canary");
        AgenticScope scope = workflow.getAgenticScope("rollout-1");
        assertThat(scope.state().get("fix")).isInstanceOf(AsyncResponse.class);

        remediation.release.countDown();
        await().atMost(10, SECONDS)
                .until(() -> "fixed after promote canary".equals(scope.state().get("fix")));

        assertThat(workflow.getAgenticScope("rollout-1")).isSameAs(scope);
        assertThat(listener.events).doesNotContain("destroyed");
    }

    @Test
    void next_root_call_on_the_same_memory_id_waits_for_the_detached_agent_of_the_previous_one() throws Exception {
        Remediation remediation = new Remediation();
        RecordingAnalysis analysis = new RecordingAnalysis();
        AgentMonitor monitor = new AgentMonitor();
        RolloutAnalysisWithMemory workflow = AgenticServices.sequenceBuilder(RolloutAnalysisWithMemory.class)
                .subAgents(analysis, remediation)
                .outputKey("decision")
                .listener(monitor)
                .build();

        assertThat(workflow.analyze("rollout-1", "canary")).isEqualTo("promote canary");
        awaitLatch(remediation.started);
        assertThat(monitor.ongoingExecutions()).hasSize(1);

        // the second call on the same memory id does not run its agents while the remediation of the first one is
        // still running: it would share with it the chat memories, the context, the compensations and the monitoring
        CompletableFuture<String> secondCall =
                CompletableFuture.supplyAsync(() -> workflow.analyze("rollout-1", "stable"));
        await().during(300, MILLISECONDS).atMost(2, SECONDS).until(() -> !analysis.inputs.contains("stable"));
        assertThat(secondCall).isNotDone();

        remediation.release.countDown();
        assertThat(secondCall.get(10, SECONDS)).isEqualTo("promote stable");
        await().atMost(5, SECONDS).until(() -> monitor.successfulExecutions().size() == 2);

        assertThat(monitor.ongoingExecutions()).isEmpty();
        assertThat(monitor.failedExecutions()).isEmpty();
        MonitoredExecution first = monitor.successfulExecutions().get(0);
        MonitoredExecution second = monitor.successfulExecutions().get(1);
        AgentInvocation firstRemediation =
                first.topLevelInvocations().nestedInvocations().get(1);
        assertThat(firstRemediation.agent().name()).isEqualTo("remediate");
        assertThat(firstRemediation.output()).isEqualTo("fixed after promote canary");
        assertThat(second.topLevelInvocations().startTime())
                .isAfterOrEqualTo(firstRemediation.startTime().plus(firstRemediation.duration()));
        assertThat(second.topLevelInvocations().nestedInvocations().get(1).output())
                .isEqualTo("fixed after promote stable");
    }

    @Test
    void suspension_of_a_detached_agent_is_notified_and_resumed_by_the_next_call() {
        SuspendingRemediation remediation = new SuspendingRemediation();
        RecordingListener listener = new RecordingListener();
        RolloutAnalysisWithMemory workflow = AgenticServices.sequenceBuilder(RolloutAnalysisWithMemory.class)
                .subAgents(new Analysis(), remediation)
                .outputKey("decision")
                .listener(listener)
                .build();

        assertThat(workflow.analyze("rollout-1", "canary")).isEqualTo("promote canary");
        AgenticScope scope = workflow.getAgenticScope("rollout-1");
        remediation.release.countDown();
        await().atMost(10, SECONDS).until(() -> listener.events.contains("suspended"));

        // as for a synchronous agent: no failure reported, the pending response left in the scope for the next call,
        // and the result, which nobody read here, failed with the suspension so that reading it suspends the system
        assertThat(listener.events).doesNotContain("error:remediate", "after:remediate");
        assertThat(scope.pendingResponseIds()).containsExactly("remediate:task-1");
        assertThatThrownBy(() -> scope.readState("fix")).isInstanceOf(AgenticSystemSuspendedException.class);

        scope.completePendingResponse("remediate:task-1", "more input");
        assertThat(workflow.analyze("rollout-1", "canary")).isEqualTo("promote canary");
        await().atMost(10, SECONDS)
                .until(() -> "fixed with more input".equals(scope.state().get("fix")));
        assertThat(scope.pendingResponseIds()).isEmpty();
    }

    @Test
    void a_later_agent_reading_the_result_of_a_suspending_async_agent_suspends_the_root_call() {
        SuspendingRemediation remediation = new SuspendingRemediation();
        remediation.release.countDown();
        RecordingListener listener = new RecordingListener();
        RolloutAnalysisWithMemory workflow = AgenticServices.sequenceBuilder(RolloutAnalysisWithMemory.class)
                .subAgents(new Analysis(), remediation, new Report())
                .outputKey("report")
                .listener(listener)
                .build();

        assertThatThrownBy(() -> workflow.analyze("rollout-1", "canary"))
                .isInstanceOf(AgenticSystemSuspendedException.class);

        // the root call suspended as with a synchronous agent, notified once, and the reader never ran
        assertThat(listener.events).containsOnlyOnce("suspended").doesNotContain("error:remediate", "before:report");
        assertThat(workflow.getAgenticScope("rollout-1").pendingResponseIds()).containsExactly("remediate:task-1");
    }

    @Test
    void failed_result_of_a_detached_agent_is_dropped_at_the_next_root_call() {
        RolloutAnalysisWithMemory workflow = AgenticServices.sequenceBuilder(RolloutAnalysisWithMemory.class)
                .subAgents(new AnalysisUnlessStable(), new OptionalFailingRemediation())
                .outputKey("decision")
                .build();

        assertThat(workflow.analyze("rollout-1", "canary")).isEqualTo("promote canary");
        AgenticScope scope = workflow.getAgenticScope("rollout-1");
        // during the call that produced it, and until the next one, reading the result throws the failure
        assertThatThrownBy(() -> scope.readState("fix")).isInstanceOf(AgentInvocationException.class);

        // the next call skips the remediation, whose argument is missing, which returns the current value of its
        // output key: the stale failure of the previous call must not be it
        assertThat(workflow.analyze("rollout-1", "stable")).isNull();
        assertThat(scope.hasState("fix")).isFalse();
    }

    @Test
    void detached_agent_suspending_after_a_suspended_root_call_is_notified_too() {
        SuspendingRemediation remediation = new SuspendingRemediation();
        RecordingListener listener = new RecordingListener();
        RolloutAnalysisWithMemory workflow = AgenticServices.sequenceBuilder(RolloutAnalysisWithMemory.class)
                .subAgents(new Analysis(), remediation, new ApprovalGate())
                .outputKey("decision")
                .listener(listener)
                .build();

        // the root call suspends on the approval while the remediation is still running
        assertThatThrownBy(() -> workflow.analyze("rollout-1", "canary"))
                .isInstanceOf(AgenticSystemSuspendedException.class);
        assertThat(listener.events).containsOnlyOnce("suspended");

        // the remediation then suspends too: the root call already returned, so its listener is told
        remediation.release.countDown();
        await().atMost(10, SECONDS).until(() -> Collections.frequency(listener.events, "suspended") == 2);
        assertThat(workflow.getAgenticScope("rollout-1").pendingResponseIds())
                .containsExactlyInAnyOrder("approval", "remediate:task-1");
    }

    @Test
    void next_root_call_writes_its_inputs_only_after_the_detached_agents_of_the_previous_one_completed()
            throws Exception {
        InputReadingRemediation remediation = new InputReadingRemediation();
        RolloutAnalysisWithMemory workflow = AgenticServices.sequenceBuilder(RolloutAnalysisWithMemory.class)
                .subAgents(new Analysis(), remediation)
                .outputKey("decision")
                .build();

        assertThat(workflow.analyze("rollout-1", "canary")).isEqualTo("promote canary");
        AgenticScope scope = workflow.getAgenticScope("rollout-1");

        // the second call waits before writing its input, which the remediation of the first one still reads
        CompletableFuture<String> secondCall =
                CompletableFuture.supplyAsync(() -> workflow.analyze("rollout-1", "stable"));
        await().during(300, MILLISECONDS)
                .atMost(2, SECONDS)
                .until(() -> "canary".equals(scope.state().get("input")));

        remediation.release.countDown();
        assertThat(secondCall.get(10, SECONDS)).isEqualTo("promote stable");
        await().atMost(10, SECONDS).until(() -> remediation.results.size() == 2);
        assertThat(remediation.results).containsExactly("fixed for canary", "fixed for stable");
    }

    @Test
    void persistent_scope_is_saved_without_the_pending_result_and_again_with_it() {
        JsonInMemoryAgenticScopeStore store = new JsonInMemoryAgenticScopeStore();
        AgenticScopePersister.setStore(store);
        Remediation remediation = new Remediation();
        RolloutAnalysisWithMemory workflow = AgenticServices.sequenceBuilder(RolloutAnalysisWithMemory.class)
                .subAgents(new Analysis(), remediation)
                .outputKey("decision")
                .build();
        AgenticScopeKey key = new AgenticScopeKey(RolloutAnalysisWithMemory.class.getName(), "rollout-1");

        assertThat(workflow.analyze("rollout-1", "canary")).isEqualTo("promote canary");

        String savedAtRootEnd = store.getJson(key);
        assertThat(savedAtRootEnd).contains("\"decision\":\"promote canary\"").doesNotContain("\"fix\"");
        // the invocation of the pending agent is saved too, with its output still pending
        assertThat(savedAtRootEnd).contains("\"agentName\":\"remediate\"").contains("\"output\":\"<pending>\"");

        remediation.release.countDown();
        await().atMost(10, SECONDS).until(() -> store.getJson(key).contains("\"fix\""));

        assertThat(store.getJson(key))
                .contains("\"fix\":\"fixed after promote canary\"")
                .doesNotContain("<pending>");
    }

    @Test
    void late_failure_of_a_detached_agent_is_saved_in_a_persistent_scope() {
        JsonInMemoryAgenticScopeStore store = new JsonInMemoryAgenticScopeStore();
        AgenticScopePersister.setStore(store);
        FailingRemediation remediation = new FailingRemediation();
        RolloutAnalysisWithMemory workflow = AgenticServices.sequenceBuilder(RolloutAnalysisWithMemory.class)
                .subAgents(new Analysis(), remediation)
                .outputKey("decision")
                .build();
        AgenticScopeKey key = new AgenticScopeKey(RolloutAnalysisWithMemory.class.getName(), "rollout-1");

        assertThat(workflow.analyze("rollout-1", "canary")).isEqualTo("promote canary");
        assertThat(store.getJson(key)).contains("\"output\":\"<pending>\"");

        remediation.release.countDown();
        await().atMost(10, SECONDS).until(() -> store.getJson(key).contains("\"output\":\"<failed>\""));
    }

    @Test
    void failure_of_a_detached_agent_before_the_end_of_the_root_call_does_not_break_saving_a_persistent_scope() {
        // an executor running the agent inline makes it fail before the root call ends
        ExecutorProvider.set(() -> Runnable::run);
        try {
            JsonInMemoryAgenticScopeStore store = new JsonInMemoryAgenticScopeStore();
            AgenticScopePersister.setStore(store);
            FailingRemediation remediation = new FailingRemediation();
            remediation.release.countDown();
            RolloutAnalysisWithMemory workflow = AgenticServices.sequenceBuilder(RolloutAnalysisWithMemory.class)
                    .subAgents(new Analysis(), remediation)
                    .outputKey("decision")
                    .build();
            AgenticScopeKey key = new AgenticScopeKey(RolloutAnalysisWithMemory.class.getName(), "rollout-1");

            assertThat(workflow.analyze("rollout-1", "canary")).isEqualTo("promote canary");
            assertThat(store.getJson(key)).contains("\"output\":\"<failed>\"");

            assertThat(workflow.analyze("rollout-1", "rollback")).isEqualTo("promote rollback");
        } finally {
            ExecutorProvider.set(null);
        }
    }

    @Test
    void result_of_an_async_agent_completing_before_its_response_is_written_to_the_scope_is_settled_and_saved() {
        // an executor running the agent inline completes its response before the executor of the agent writes that
        // response to the scope: the result must take its place anyway
        ExecutorProvider.set(() -> Runnable::run);
        try {
            JsonInMemoryAgenticScopeStore store = new JsonInMemoryAgenticScopeStore();
            AgenticScopePersister.setStore(store);
            Remediation remediation = new Remediation();
            remediation.release.countDown();
            RolloutAnalysisWithMemory workflow = AgenticServices.sequenceBuilder(RolloutAnalysisWithMemory.class)
                    .subAgents(new Analysis(), remediation)
                    .outputKey("decision")
                    .build();
            AgenticScopeKey key = new AgenticScopeKey(RolloutAnalysisWithMemory.class.getName(), "rollout-1");

            assertThat(workflow.analyze("rollout-1", "canary")).isEqualTo("promote canary");

            assertThat(workflow.getAgenticScope("rollout-1").state().get("fix"))
                    .isEqualTo("fixed after promote canary");
            assertThat(store.getJson(key)).contains("\"fix\":\"fixed after promote canary\"");
        } finally {
            ExecutorProvider.set(null);
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, SECONDS)).as("latch released").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
