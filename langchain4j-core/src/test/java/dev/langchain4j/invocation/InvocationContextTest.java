package dev.langchain4j.invocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvocationContextTest {

    @Test
    void should_default_to_no_parent() {
        assertThat(InvocationContext.builder().build().parentInvocationId()).isNull();
    }

    @Test
    void should_copy_the_parent_invocation_id() {
        UUID parentId = UUID.randomUUID();
        var builder = InvocationContext.builder().parentInvocationId(parentId);
        InvocationContext context = builder.build();
        InvocationContext copy = context.toBuilder().build();

        assertThat(builder.parentInvocationId()).isEqualTo(parentId);
        assertThat(context.parentInvocationId()).isEqualTo(parentId);
        assertThat(copy.parentInvocationId()).isEqualTo(parentId);
        assertThat(copy).isEqualTo(context).hasSameHashCodeAs(context).hasToString(context.toString());
        assertThat(context.toString()).contains("parentInvocationId=" + parentId);
        assertThat((Object) context.toBuilder().parentInvocationId(null).build())
                .isNotEqualTo(context);
        assertThat((Object) context.toBuilder()
                        .parentInvocationId(UUID.randomUUID())
                        .build())
                .isNotEqualTo(context);
    }
}
