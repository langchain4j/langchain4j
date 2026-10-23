package dev.langchain4j.invocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultInvocationContextTest {

    @Test
    void should_have_no_parent_invocation_by_default() {
        InvocationContext invocationContext =
                InvocationContext.builder().invocationId(UUID.randomUUID()).build();

        assertThat(invocationContext.parentInvocationId()).isNull();
    }

    @Test
    void should_keep_parent_invocation_id_in_copies() {
        UUID parentInvocationId = UUID.randomUUID();
        InvocationContext invocationContext = InvocationContext.builder()
                .invocationId(UUID.randomUUID())
                .parentInvocationId(parentInvocationId)
                .build();

        InvocationContext copy =
                invocationContext.toBuilder().invocationId(UUID.randomUUID()).build();

        assertThat(copy.parentInvocationId()).isEqualTo(parentInvocationId);
    }

    @Test
    void should_include_parent_invocation_id_in_equals_hash_code_and_to_string() {
        UUID invocationId = UUID.randomUUID();
        UUID parentInvocationId = UUID.randomUUID();
        InvocationContext withParent = InvocationContext.builder()
                .invocationId(invocationId)
                .parentInvocationId(parentInvocationId)
                .build();
        InvocationContext sameWithParent =
                withParent.toBuilder().parentInvocationId(parentInvocationId).build();
        InvocationContext withoutParent =
                withParent.toBuilder().parentInvocationId(null).build();

        assertThat(withParent).isEqualTo(sameWithParent).hasSameHashCodeAs(sameWithParent);
        assertThat(withParent).isNotEqualTo(withoutParent);
        assertThat(withParent.toString()).contains("parentInvocationId=" + parentInvocationId);
    }
}
