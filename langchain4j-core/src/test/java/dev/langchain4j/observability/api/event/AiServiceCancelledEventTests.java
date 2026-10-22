package dev.langchain4j.observability.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.invocation.InvocationContext;
import org.junit.jupiter.api.Test;

class AiServiceCancelledEventTests {

    private static final InvocationContext INVOCATION_CONTEXT = InvocationContext.builder()
            .interfaceName("SomeInterface")
            .methodName("someMethod")
            .build();

    @Test
    void toBuilderRoundTripsInvocationContext() {
        final AiServiceCancelledEvent event = AiServiceCancelledEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .build();

        final AiServiceCancelledEvent copy = event.toBuilder().build();

        assertThat(copy.invocationContext()).isEqualTo(INVOCATION_CONTEXT);
        assertThat(copy.eventClass()).isEqualTo(AiServiceCancelledEvent.class);
    }
}
