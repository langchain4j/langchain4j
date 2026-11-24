package dev.langchain4j.observability.api.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiServiceInvocationSummaryEventTests {

    private static final InvocationContext INVOCATION_CONTEXT = InvocationContext.builder()
            .invocationId(UUID.randomUUID())
            .interfaceName("SomeInterface")
            .methodName("someMethod")
            .build();

    private static final AiServiceStartedEvent STARTED_EVENT = AiServiceStartedEvent.builder()
            .invocationContext(INVOCATION_CONTEXT)
            .userMessage(UserMessage.from("Hello"))
            .build();

    private static final AiServiceCompletedEvent COMPLETED_EVENT = AiServiceCompletedEvent.builder()
            .invocationContext(INVOCATION_CONTEXT)
            .result("Hi")
            .build();

    @Test
    void eventsReplacesPreviouslyAddedEvents() {
        var event = AiServiceInvocationSummaryEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .event(COMPLETED_EVENT)
                .events(List.of(STARTED_EVENT))
                .build();

        assertThat(event.events()).containsExactly(STARTED_EVENT);
    }

    @Test
    void eventAppendsAndIgnoresNulls() {
        var events = new ArrayList<AiServiceEvent>();
        events.add(STARTED_EVENT);
        events.add(null);

        var event = AiServiceInvocationSummaryEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .events(events)
                .event(null)
                .event(COMPLETED_EVENT)
                .build();

        assertThat(event.events()).containsExactly(STARTED_EVENT, COMPLETED_EVENT);
    }

    @Test
    void nullEventsClearsEvents() {
        var event = AiServiceInvocationSummaryEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .event(STARTED_EVENT)
                .events(null)
                .build();

        assertThat(event.events()).isEmpty();
    }

    @Test
    void eventsAreUnmodifiableAndIsolatedFromTheSourceList() {
        var source = new ArrayList<AiServiceEvent>(List.of(STARTED_EVENT));
        var event = AiServiceInvocationSummaryEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .events(source)
                .build();

        source.add(COMPLETED_EVENT);

        assertThat(event.events()).containsExactly(STARTED_EVENT);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> event.events().add(COMPLETED_EVENT));
    }

    @Test
    void toBuilderRoundTrips() {
        var event = AiServiceInvocationSummaryEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .events(List.of(STARTED_EVENT, COMPLETED_EVENT))
                .build();

        var copy = event.toBuilder().build();

        assertThat(copy)
                .isNotSameAs(event)
                .extracting(AiServiceEvent::invocationContext, AiServiceInvocationSummaryEvent::events)
                .containsExactly(INVOCATION_CONTEXT, List.of(STARTED_EVENT, COMPLETED_EVENT));
        assertThat(copy.eventClass()).isEqualTo(AiServiceInvocationSummaryEvent.class);
    }
}
