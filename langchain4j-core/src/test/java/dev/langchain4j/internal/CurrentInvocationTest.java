package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.invocation.InvocationContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentInvocationTest {

    @Test
    void should_set_the_current_invocation_only_during_the_action() {
        InvocationContext invocationContext = invocationContext();

        UUID seen = CurrentInvocation.callWith(invocationContext, CurrentInvocation::invocationId);

        assertThat(seen).isEqualTo(invocationContext.invocationId());
        assertThat(CurrentInvocation.invocationId()).isNull();
    }

    @Test
    void should_restore_the_previous_invocation_after_a_nested_one() {
        InvocationContext outer = invocationContext();
        InvocationContext inner = invocationContext();

        UUID seenAfterInner = CurrentInvocation.callWith(outer, () -> {
            assertThat(CurrentInvocation.callWith(inner, CurrentInvocation::invocationId)).isEqualTo(inner.invocationId());
            return CurrentInvocation.invocationId();
        });

        assertThat(seenAfterInner).isEqualTo(outer.invocationId());
        assertThat(CurrentInvocation.invocationId()).isNull();
    }

    @Test
    void should_restore_the_previous_invocation_when_the_action_fails() {
        InvocationContext outer = invocationContext();

        CurrentInvocation.callWith(outer, () -> {
            assertThatThrownBy(() -> CurrentInvocation.callWith(invocationContext(), () -> {
                        throw new IllegalStateException("failed");
                    }))
                    .hasMessage("failed");
            assertThat(CurrentInvocation.invocationId()).isEqualTo(outer.invocationId());
            return null;
        });

        assertThat(CurrentInvocation.invocationId()).isNull();
    }

    @Test
    void should_keep_the_current_invocation_when_called_without_one() {
        InvocationContext outer = invocationContext();

        UUID seen = CurrentInvocation.callWith(
                outer, () -> CurrentInvocation.callWith(null, CurrentInvocation::invocationId));

        assertThat(seen).isEqualTo(outer.invocationId());
    }

    @Test
    void should_clear_the_current_invocation_during_the_action_and_restore_it_after() {
        InvocationContext outer = invocationContext();

        UUID[] seen = CurrentInvocation.callWith(outer, () -> new UUID[] {
            CurrentInvocation.callWithout(CurrentInvocation::invocationId), CurrentInvocation.invocationId()
        });

        assertThat(seen[0]).isNull();
        assertThat(seen[1]).isEqualTo(outer.invocationId());
        assertThat(CurrentInvocation.invocationId()).isNull();
    }

    @Test
    void should_restore_the_current_invocation_when_the_action_without_it_fails() {
        InvocationContext outer = invocationContext();

        UUID seenAfterFailure = CurrentInvocation.callWith(outer, () -> {
            assertThatThrownBy(() -> CurrentInvocation.callWithout(() -> {
                        throw new IllegalStateException("failed");
                    }))
                    .hasMessage("failed");
            return CurrentInvocation.invocationId();
        });

        assertThat(seenAfterFailure).isEqualTo(outer.invocationId());
    }

    private static InvocationContext invocationContext() {
        return InvocationContext.builder().invocationId(UUID.randomUUID()).build();
    }
}
