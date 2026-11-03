package dev.langchain4j.agentic;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.internal.AsyncResponse;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import dev.langchain4j.spi.ExecutorProvider;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The root call of an agentic system does not wait for an asynchronous agent whose result nobody reads: it returns
 * while that agent is still running, and the result or the failure of the agent is reported to its listener, while
 * the scope is finalized only after its completion.
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
            await(release);
            return "fixed after " + decision;
        }
    }

    public static class FailingRemediation {
        final CountDownLatch release = new CountDownLatch(1);

        @Agent(async = true, outputKey = "fix")
        public String remediate(@V("decision") String decision) {
            await(release);
            throw new IllegalStateException("sandbox unavailable");
        }
    }

    public static class NullRemediation {
        @Agent(async = true, outputKey = "fix")
        public String remediate(@V("decision") String decision) {
            return null;
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
        assertThat(remediation.release.getCount()).isEqualTo(1);
        // the agent was notified before its submission, on the calling thread, and the ephemeral scope was destroyed
        // at the end of the root call without waiting for it
        assertThat(listener.events).contains("before:remediate", "destroyed").doesNotContain("after:remediate");
        assertThat(listener.beforeThreads.get("remediate"))
                .isEqualTo(Thread.currentThread().getName());
        // the live scope still holds the pending response
        AgenticScope scope = result.agenticScope();
        assertThat(scope.state().get("fix")).isInstanceOf(AsyncResponse.class);

        remediation.release.countDown();
        awaitUntil(() -> scope.state().get("fix") instanceof String);

        assertThat(listener.outputs.get("remediate")).isEqualTo("fixed after promote canary");
        assertThat(listener.events).containsSubsequence("destroyed", "after:remediate");
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
        await(remediation.started);

        assertThat(rootCall).isNotDone();
        remediation.release.countDown();
        assertThat(rootCall.get(5, SECONDS)).isEqualTo("fixed after promote canary");
    }

    @Test
    void a_later_agent_reading_the_async_result_waits_for_it() {
        Remediation remediation = new Remediation();
        remediation.release.countDown();
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(new Analysis(), remediation, new Report())
                .outputKey("report")
                .build();

        assertThat(workflow.invoke(Map.of("input", "canary"))).isEqualTo("report: fixed after promote canary");
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
        awaitUntil(() -> !handledErrors.isEmpty());

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
        awaitUntil(() -> "manual fix".equals(result.agenticScope().state().get("fix")));

        assertThat(result.agenticScope().state().get("fix")).isEqualTo("manual fix");
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
        awaitUntil(() -> !result.agenticScope().hasState("fix"));

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
        awaitUntil(() -> "fixed after promote canary".equals(scope.state().get("fix")));

        assertThat(workflow.getAgenticScope("rollout-1")).isSameAs(scope);
        assertThat(listener.events).doesNotContain("destroyed");
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
        awaitUntil(() -> store.getJson(key).contains("\"fix\""));

        assertThat(store.getJson(key))
                .contains("\"fix\":\"fixed after promote canary\"")
                .doesNotContain("<pending>");
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

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, SECONDS)).as("latch released").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static void awaitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("condition met in time").isLessThan(deadline);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}
