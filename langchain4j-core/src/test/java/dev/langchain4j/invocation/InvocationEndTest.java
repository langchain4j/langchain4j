package dev.langchain4j.invocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvocationEndTest {

    @Test
    void should_mark_an_invocation_as_ended_only_once() {
        InvocationContext invocationContext =
                InvocationContext.builder().invocationId(UUID.randomUUID()).build();

        assertThat(InvocationEnd.markEnded(invocationContext)).isTrue();
        assertThat(InvocationEnd.markEnded(invocationContext)).isFalse();
    }

    @Test
    void should_share_the_end_with_copies_of_the_same_invocation() {
        InvocationContext invocationContext =
                InvocationContext.builder().invocationId(UUID.randomUUID()).build();
        InvocationContext copy = invocationContext.toBuilder().chatMemoryId("memory").build();

        assertThat(InvocationEnd.markEnded(copy)).isTrue();

        assertThat(InvocationEnd.markEnded(invocationContext)).isFalse();
    }

    @Test
    void should_not_share_the_end_with_a_copy_that_has_another_invocation_id() {
        InvocationContext invocationContext =
                InvocationContext.builder().invocationId(UUID.randomUUID()).build();
        InvocationContext otherInvocation =
                invocationContext.toBuilder().invocationId(UUID.randomUUID()).build();

        assertThat(InvocationEnd.markEnded(invocationContext)).isTrue();

        assertThat(InvocationEnd.markEnded(otherInvocation)).isTrue();
    }

    @Test
    void should_not_guard_an_invocation_context_that_was_not_built_by_langchain4j() {
        InvocationContext invocationContext = mock(InvocationContext.class);

        assertThat(InvocationEnd.markEnded(invocationContext)).isTrue();
        assertThat(InvocationEnd.markEnded(invocationContext)).isTrue();
    }

    @Test
    void should_not_guard_an_invocation_without_id() {
        InvocationContext invocationContext = InvocationContext.builder().build();

        assertThat(InvocationEnd.markEnded(invocationContext)).isTrue();
        assertThat(InvocationEnd.markEnded(invocationContext)).isTrue();
    }

    @Test
    void should_not_take_the_end_into_account_in_equals() {
        UUID invocationId = UUID.randomUUID();
        InvocationContext invocationContext =
                InvocationContext.builder().invocationId(invocationId).build();
        InvocationContext copy = invocationContext.toBuilder().build();

        InvocationEnd.markEnded(invocationContext);

        assertThat((Object) copy).isEqualTo(invocationContext);
        assertThat(copy.hashCode()).isEqualTo(invocationContext.hashCode());
    }
}
