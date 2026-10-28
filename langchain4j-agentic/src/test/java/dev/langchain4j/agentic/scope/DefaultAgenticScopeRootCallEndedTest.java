package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.internal.AsyncResponse;
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
    void discards_failed_async_responses_without_throwing() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.REGISTERED);
        scope.writeState("failedOutput", new AsyncResponse<>(() -> {
            throw new IllegalStateException("async agent failed");
        }));
        scope.writeState("realOutput", new AsyncResponse<>(() -> "value"));

        assertThatCode(() -> scope.rootCallEnded(null, null)).doesNotThrowAnyException();

        assertThat(scope.state()).doesNotContainKey("failedOutput");
        assertThat(scope.readState("realOutput")).isEqualTo("value");
    }

    @Test
    void awaiting_pending_responses_rethrows_the_failure_of_an_async_agent() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.REGISTERED);
        scope.writeState("failedOutput", new AsyncResponse<>(() -> {
            throw new IllegalStateException("async agent failed");
        }));

        assertThatThrownBy(scope::awaitPendingResponses)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("async agent failed");
    }
}
