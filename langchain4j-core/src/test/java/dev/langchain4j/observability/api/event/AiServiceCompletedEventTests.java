package dev.langchain4j.observability.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.invocation.InvocationContext;
import org.junit.jupiter.api.Test;

class AiServiceCompletedEventTests {

    private static final InvocationContext INVOCATION_CONTEXT = InvocationContext.builder()
            .interfaceName("SomeInterface")
            .methodName("someMethod")
            .build();

    @Test
    void toBuilderRoundTripsPresentResult() {
        final AiServiceCompletedEvent event = AiServiceCompletedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .result("hello")
                .build();

        final AiServiceCompletedEvent copy = event.toBuilder().build();

        assertThat(copy.invocationContext()).isEqualTo(event.invocationContext());
        assertThat(copy.result()).contains("hello");
    }

    @Test
    void toBuilderRoundTripsAbsentResult() {
        final AiServiceCompletedEvent event = AiServiceCompletedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .build();

        final AiServiceCompletedEvent copy = event.toBuilder().build();

        assertThat(event.result()).isEmpty();
        assertThat(copy.result()).isEmpty();
    }

    @Test
    void toBuilderAllowsOverridingResult() {
        final AiServiceCompletedEvent event = AiServiceCompletedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .result("hello")
                .build();

        final AiServiceCompletedEvent copy = event.toBuilder().result("bye").build();

        assertThat(copy.result()).contains("bye");
        assertThat(event.result()).contains("hello");
    }
}
