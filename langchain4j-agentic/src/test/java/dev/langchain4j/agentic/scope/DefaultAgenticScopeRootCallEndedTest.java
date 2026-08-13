package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
}
