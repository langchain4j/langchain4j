package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.internal.PendingResponse;
import org.junit.jupiter.api.Test;

class CompletePendingResponseTest {

    @Test
    void should_complete_with_a_value() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        PendingResponse<String> pending = new PendingResponse<>("response-1");
        scope.writeState("humanInput", pending);

        boolean completed = scope.completePendingResponse("response-1", "yes");

        assertThat(completed).isTrue();
        assertThat(pending.blockingGet()).isEqualTo("yes");
        assertThat(scope.readState("humanInput")).isEqualTo("yes");
    }

    @Test
    void should_complete_with_null_value() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        PendingResponse<String> pending = new PendingResponse<>("response-1");
        scope.writeState("humanInput", pending);

        boolean completed = scope.completePendingResponse("response-1", null);

        assertThat(completed).isTrue();
        assertThat(pending.isDone()).isTrue();
        assertThat(pending.blockingGet()).isNull();
    }

    @Test
    void should_remove_the_key_when_completed_with_null_as_writeState_does() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState("humanInput", new PendingResponse<String>("response-1"));

        scope.completePendingResponse("response-1", null);

        assertThat(scope.hasState("humanInput")).isFalse();
        assertThat(scope.readState("humanInput")).isNull();
    }

    @Test
    void should_clear_the_pending_id_when_completed_with_null() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState("humanInput", new PendingResponse<String>("response-1"));
        assertThat(scope.pendingResponseIds()).containsExactly("response-1");

        scope.completePendingResponse("response-1", null);

        assertThat(scope.pendingResponseIds()).isEmpty();
    }

    @Test
    void should_complete_the_single_pending_response_with_null() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        PendingResponse<String> pending = new PendingResponse<>("response-1");
        scope.writeState("humanInput", pending);

        boolean completed = scope.completePendingResponse(null);

        assertThat(completed).isTrue();
        assertThat(pending.blockingGet()).isNull();
        assertThat(scope.hasState("humanInput")).isFalse();
    }

    @Test
    void should_not_touch_the_state_when_the_response_is_already_completed() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        PendingResponse<String> pending = new PendingResponse<>("response-1");
        pending.complete("first");
        scope.writeState("humanInput", pending);

        boolean completed = scope.completePendingResponse("response-1", null);

        assertThat(completed).isFalse();
        assertThat(scope.state()).containsEntry("humanInput", pending);
    }
}
