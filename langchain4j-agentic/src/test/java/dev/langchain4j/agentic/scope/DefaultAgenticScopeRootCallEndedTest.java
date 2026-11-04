package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.JsonInMemoryAgenticScopeStore;
import dev.langchain4j.agentic.internal.AsyncResponse;
import dev.langchain4j.agentic.internal.PendingResponse;
import dev.langchain4j.agentic.observability.AgentListener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DefaultAgenticScopeRootCallEndedTest {

    @Test
    void resolves_async_state_returning_null_without_throwing() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.REGISTERED);
        scope.writeState("nullOutput", new AsyncResponse<>(() -> null));
        scope.writeState("realOutput", new AsyncResponse<>(() -> "value"));
        scope.writeState("plainOutput", "kept");

        assertThatCode(() -> scope.rootCallEnded(null, null, false)).doesNotThrowAnyException();

        assertThat(scope.readState("nullOutput")).isNull();
        assertThat(scope.readState("realOutput")).isEqualTo("value");
        assertThat(scope.readState("plainOutput")).isEqualTo("kept");
    }

    @Test
    void ephemeral_scope_is_evicted_at_root_end_without_waiting_for_a_pending_async_agent() {
        AgenticScopeRegistry registry = new AgenticScopeRegistry("root");
        DefaultAgenticScope scope = registry.createEphemeralAgenticScope();
        List<String> destroyed = new CopyOnWriteArrayList<>();
        AgentListener listener = new AgentListener() {
            @Override
            public void beforeAgenticScopeDestroyed(AgenticScope agenticScope) {
                destroyed.add(String.valueOf(agenticScope.memoryId()));
            }
        };
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch ended = new CountDownLatch(1);
        AsyncResponse<String> response = new AsyncResponse<>(() -> {
            await(release);
            return "late";
        });
        scope.writeState("late", response);
        response.onComplete(completed -> {
            scope.asyncEnded("late", completed);
            ended.countDown();
        });

        scope.rootCallEnded(registry, listener, false);

        // the root call ended without waiting, and the scope was evicted with the response still pending
        assertThat(destroyed).containsExactly(String.valueOf(scope.memoryId()));
        assertThat(registry.get(scope.memoryId())).isNull();
        assertThat(scope.state().get("late")).isSameAs(response);

        release.countDown();
        await(ended);

        // the result still reaches the scope, for a caller holding it
        assertThat(scope.state().get("late")).isEqualTo("late");
    }

    @Test
    void overwritten_async_result_is_not_settled() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.REGISTERED);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch ended = new CountDownLatch(1);
        AsyncResponse<String> response = new AsyncResponse<>(() -> {
            await(release);
            return "stale";
        });
        response.onComplete(completed -> {
            scope.asyncEnded("key", completed);
            ended.countDown();
        });
        scope.writeState("key", "fresh");

        release.countDown();
        await(ended);

        assertThat(scope.state().get("key")).isEqualTo("fresh");
    }

    @Test
    void root_call_still_waits_for_the_other_delayed_responses() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.REGISTERED);
        scope.writeState("deferred", new PendingResponseCompletedBy("human", "approved"));

        scope.rootCallEnded(null, null, false);

        assertThat(scope.state().get("deferred")).isEqualTo("approved");
    }

    @Test
    void pending_async_response_is_not_serialized() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        CountDownLatch release = new CountDownLatch(1);
        scope.writeState("pending", new AsyncResponse<>(() -> {
            await(release);
            return "value";
        }));
        scope.writeState("plain", "kept");

        DefaultAgenticScope copy = scope.serializableCopy();

        assertThat(copy.state()).containsOnlyKeys("plain");
        release.countDown();
    }

    @Test
    void failed_async_response_is_rendered_and_persisted_as_failed_and_reported_once() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        AsyncResponse<String> response = new AsyncResponse<>(() -> {
            throw new IllegalStateException("sandbox unavailable");
        });
        assertThatThrownBy(response::blockingGet).isInstanceOf(IllegalStateException.class);
        scope.writeState("fix", response);
        scope.registerAgentInvocation(new AgentInvocation(Object.class, "remediate", "remediate", Map.of(), response));

        assertThat(scope.toString()).contains("<failed>");
        assertThat(scope.serializableCopy().agentInvocations())
                .extracting(AgentInvocation::output)
                .containsExactly("<failed>");
        assertThat(response.reportFailureOnce()).isTrue();
        assertThat(response.reportFailureOnce()).isFalse();
    }

    @Test
    void evicted_persistent_scope_is_not_saved_again_when_an_async_agent_completes() {
        JsonInMemoryAgenticScopeStore store = new JsonInMemoryAgenticScopeStore();
        AgenticScopePersister.setStore(store);
        try {
            AgenticScopeRegistry registry = new AgenticScopeRegistry("root");
            DefaultAgenticScope scope = registry.create("session-1");
            AgenticScopeKey key = new AgenticScopeKey("root", "session-1");
            CountDownLatch release = new CountDownLatch(1);
            AsyncResponse<String> response = new AsyncResponse<>(() -> {
                await(release);
                return "late";
            });
            scope.writeState("late", response);
            scope.rootCallEnded(registry, null, false);
            assertThat(store.getJson(key)).isNotNull();

            registry.evict("session-1", null);
            release.countDown();
            response.blockingGet();
            scope.asyncEnded("late", response);

            assertThat(store.getJson(key)).isNull();
        } finally {
            AgenticScopePersister.setStore(null);
        }
    }

    @Test
    void root_call_start_waits_for_the_async_agents_of_the_previous_call_and_drops_their_compensations() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.REGISTERED);
        CountDownLatch release = new CountDownLatch(1);
        List<String> compensated = new CopyOnWriteArrayList<>();
        AsyncResponse<String> response = new AsyncResponse<>(() -> {
            await(release);
            scope.registerCompensableExecution(null, execution -> compensated.add("late"));
            return "late";
        });
        scope.writeState("late", response);
        scope.asyncPending("lateAgent", "late", response);
        scope.rootCallEnded(null, null, false);

        CompletableFuture<Void> nextCall = CompletableFuture.runAsync(() -> scope.rootCallStarted(null));
        assertThat(nextCall).isNotDone();

        release.countDown();
        nextCall.join();

        // the result was settled before the next root call started, and its compensation is not the next call's
        assertThat(scope.state().get("late")).isEqualTo("late");
        scope.compensateAll();
        assertThat(compensated).isEmpty();
    }

    /** A deferred response completed as soon as it is awaited, like a human answering at once. */
    private static class PendingResponseCompletedBy extends PendingResponse<String> {
        private final String answer;

        PendingResponseCompletedBy(String responseId, String answer) {
            super(responseId);
            this.answer = answer;
        }

        @Override
        public String blockingGet() {
            complete(answer);
            return super.blockingGet();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).as("latch released").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
