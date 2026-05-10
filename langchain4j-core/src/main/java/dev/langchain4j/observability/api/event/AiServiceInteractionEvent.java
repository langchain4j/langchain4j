package dev.langchain4j.observability.api.event;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.event.DefaultAiServiceInteractionEvent;
import java.util.List;

/**
 * Fired when an AI Service interaction has fully completed (either successfully via
 * {@link AiServiceCompletedEvent} or with an error via {@link AiServiceErrorEvent}).
 *
 * <p>Contains the ordered list of all {@link AiServiceEvent}s that were fired during
 * the interaction, including the terminal event itself. Sub-interactions (e.g. a tool
 * that itself calls an LLM) are represented as nested {@link AiServiceInteractionEvent}
 * instances within the list.
 */
public interface AiServiceInteractionEvent extends AiServiceEvent {

    /**
     * Returns all events fired during this interaction, in the order they were fired.
     * The list includes the terminal {@link AiServiceCompletedEvent} or
     * {@link AiServiceErrorEvent}. Nested sub-interactions appear as
     * {@link AiServiceInteractionEvent} entries within the list.
     */
    List<AiServiceEvent> events();

    /**
     * Returns {@code true} if the interaction completed successfully.
     * This is determined by checking whether the last event in the list is an
     * {@link AiServiceCompletedEvent} — avoiding false positives from nested
     * sub-interaction events that may themselves contain completed events.
     */
    default boolean isSuccessful() {
        List<AiServiceEvent> all = events();
        return !all.isEmpty() && all.get(all.size() - 1) instanceof AiServiceCompletedEvent;
    }

    /**
     * Creates a new builder for {@link AiServiceInteractionEvent}.
     */
    static AiServiceInteractionEventBuilder builder() {
        return new AiServiceInteractionEventBuilder();
    }

    @Override
    default Class<AiServiceInteractionEvent> eventClass() {
        return AiServiceInteractionEvent.class;
    }

    @Override
    default AiServiceInteractionEventBuilder toBuilder() {
        return new AiServiceInteractionEventBuilder(this);
    }

    /**
     * Builder for {@link AiServiceInteractionEvent} instances.
     */
    class AiServiceInteractionEventBuilder extends Builder<AiServiceInteractionEvent> {
        private List<AiServiceEvent> events;

        protected AiServiceInteractionEventBuilder() {}

        protected AiServiceInteractionEventBuilder(AiServiceInteractionEvent src) {
            super(src);
            events(src.events());
        }

        public AiServiceInteractionEventBuilder invocationContext(InvocationContext invocationContext) {
            return (AiServiceInteractionEventBuilder) super.invocationContext(invocationContext);
        }

        public AiServiceInteractionEventBuilder events(List<AiServiceEvent> events) {
            this.events = events;
            return this;
        }

        public List<AiServiceEvent> events() {
            return events;
        }

        @Override
        public AiServiceInteractionEvent build() {
            return new DefaultAiServiceInteractionEvent(this);
        }
    }
}
