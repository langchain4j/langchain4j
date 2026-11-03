package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.agentic.internal.AsyncResponse;
import dev.langchain4j.agentic.observability.AgentListener;
import java.util.List;
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

        assertThatCode(() -> scope.rootCallEnded(null, null)).doesNotThrowAnyException();

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
        AsyncResponse<String> response = new AsyncResponse<>(
                () -> {
                    await(release);
                    return "late";
                },
                completed -> {
                    scope.asyncEnded("late", completed);
                    ended.countDown();
                });
        scope.writeState("late", response);

        scope.rootCallEnded(registry, listener);

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
        new AsyncResponse<>(
                () -> {
                    await(release);
                    return "stale";
                },
                completed -> {
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

        scope.rootCallEnded(null, null);

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

    /** A deferred response completed as soon as it is awaited, like a human answering at once. */
    private static class PendingResponseCompletedBy extends dev.langchain4j.agentic.internal.PendingResponse<String> {
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
