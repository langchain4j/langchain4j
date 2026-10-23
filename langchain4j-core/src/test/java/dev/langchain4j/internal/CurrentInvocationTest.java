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

        InvocationContext seen = CurrentInvocation.callWith(invocationContext, CurrentInvocation::get);

        assertThat(seen).isSameAs(invocationContext);
        assertThat(CurrentInvocation.get()).isNull();
    }

    @Test
    void should_restore_the_previous_invocation_after_a_nested_one() {
        InvocationContext outer = invocationContext();
        InvocationContext inner = invocationContext();

        InvocationContext seenAfterInner = CurrentInvocation.callWith(outer, () -> {
            assertThat(CurrentInvocation.callWith(inner, CurrentInvocation::get)).isSameAs(inner);
            return CurrentInvocation.get();
        });

        assertThat(seenAfterInner).isSameAs(outer);
        assertThat(CurrentInvocation.get()).isNull();
    }

    @Test
    void should_restore_the_previous_invocation_when_the_action_fails() {
        InvocationContext outer = invocationContext();

        CurrentInvocation.callWith(outer, () -> {
            assertThatThrownBy(() -> CurrentInvocation.callWith(invocationContext(), () -> {
                        throw new IllegalStateException("failed");
                    }))
                    .hasMessage("failed");
            assertThat(CurrentInvocation.get()).isSameAs(outer);
            return null;
        });

        assertThat(CurrentInvocation.get()).isNull();
    }

    @Test
    void should_keep_the_current_invocation_when_called_without_one() {
        InvocationContext outer = invocationContext();

        InvocationContext seen = CurrentInvocation.callWith(
                outer, () -> CurrentInvocation.callWith(null, CurrentInvocation::get));

        assertThat(seen).isSameAs(outer);
    }

    private static InvocationContext invocationContext() {
        return InvocationContext.builder().invocationId(UUID.randomUUID()).build();
    }
}
