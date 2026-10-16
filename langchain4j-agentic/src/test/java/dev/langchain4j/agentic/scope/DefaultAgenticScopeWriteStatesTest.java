package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.langchain4j.agentic.internal.DeferredResponse;
import dev.langchain4j.agentic.internal.PendingResponse;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultAgenticScopeWriteStatesTest {

    @ParameterizedTest
    @MethodSource("deferredResponses")
    void should_complete_replaced_deferred_responses(DefaultAgenticScope.Kind kind, DeferredResponse<String> response)
            throws Exception {
        DefaultAgenticScope scope = new DefaultAgenticScope(kind);
        scope.writeState("approval", response);

        scope.writeStates(Map.of("approval", "approved"));

        assertThat(response.isDone()).isTrue();
        assertThat(response.blockingGet(1, TimeUnit.SECONDS)).isEqualTo("approved");
        assertThat(scope.readState("approval")).isEqualTo("approved");
        assertThat(scope.pendingResponseIds()).isEmpty();
    }

    static Stream<Arguments> deferredResponses() {
        return Arrays.stream(DefaultAgenticScope.Kind.values())
                .flatMap(kind -> Stream.of(
                        arguments(kind, new PendingResponse<String>("response-1")),
                        arguments(kind, new SuspendedResponse<String>("response-1"))));
    }

    @Test
    void should_release_a_reader_waiting_on_the_replaced_response() throws Exception {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        CountDownLatch reading = new CountDownLatch(1);
        PendingResponse<String> pending = new PendingResponse<>("response-1") {
            @Override
            public String blockingGet() {
                reading.countDown();
                return super.blockingGet();
            }
        };
        scope.writeState("approval", pending);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Object> result = executor.submit(() -> scope.readState("approval"));
            assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();

            scope.writeStates(Map.of("approval", "approved"));

            assertThat(pending.isDone()).isTrue();
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("approved");
        } finally {
            pending.complete("cleanup");
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void should_leave_the_same_deferred_response_pending() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        PendingResponse<String> pending = new PendingResponse<>("response-1");
        scope.writeState("approval", pending);

        scope.writeStates(Map.of("approval", pending));

        assertThat(scope.state()).containsEntry("approval", pending);
        assertThat(pending.isDone()).isFalse();
        assertThat(scope.pendingResponseIds()).containsExactly("response-1");
    }

    @Test
    void should_preserve_the_result_of_an_already_completed_response() throws Exception {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        PendingResponse<String> pending = new PendingResponse<>("response-1");
        pending.complete("first");
        scope.writeState("approval", pending);

        scope.writeStates(Map.of("approval", "second"));

        assertThat(pending.blockingGet(1, TimeUnit.SECONDS)).isEqualTo("first");
        assertThat(scope.readState("approval")).isEqualTo("second");
    }

    @Test
    void should_update_plain_state_without_completing_unrelated_responses() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        PendingResponse<String> pending = new PendingResponse<>("response-1");
        scope.writeState("approval", pending);
        scope.writeState("existing", "old");

        scope.writeStates(Map.of("existing", "updated", "new", "inserted"));

        assertThat(scope.readState("existing")).isEqualTo("updated");
        assertThat(scope.readState("new")).isEqualTo("inserted");
        assertThat(scope.state()).containsEntry("approval", pending);
        assertThat(pending.isDone()).isFalse();
        assertThat(scope.pendingResponseIds()).containsExactly("response-1");
    }

    @Test
    void should_continue_rejecting_null_values_without_completing_the_response() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        PendingResponse<String> pending = new PendingResponse<>("response-1");
        scope.writeState("approval", pending);

        assertThatThrownBy(() -> scope.writeStates(Collections.singletonMap("approval", null)))
                .isInstanceOf(NullPointerException.class);

        assertThat(scope.state()).containsEntry("approval", pending);
        assertThat(pending.isDone()).isFalse();
    }
}
