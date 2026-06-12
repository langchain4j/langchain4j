package dev.langchain4j.observability.api.event;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.event.DefaultAiServiceInteractionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates all AiServiceEvent instances for a single invocationId,
 * preserving the order in which they were fired.
 */
public interface AiServiceInteractionEvent extends AiServiceEvent {

    /**
    * Returns all events associated with this AI service invocation, in the order they were fired.
    * 
    * @return a list of events in execution order
    List<AiServiceEvent> events();

    @Override
    default Class<AiServiceInteractionEvent> eventClass() {
        return AiServiceInteractionEvent.class;
    }

    @Override
    default AiServiceInteractionEventBuilder toBuilder() {
        return new AiServiceInteractionEventBuilder(this);
    }

    /**
     * Creates a new builder for constructing an {@link AiServiceInteractionEvent}.
     * 
     * @return new builder instance
     */
    static AiServiceInteractionEventBuilder builder() {
        return new AiServiceInteractionEventBuilder();
    }

    /**
     * Builder for {@link AiServiceInteractionEvent} instances
     */
    class AiServiceInteractionEventBuilder extends Builder<AiServiceInteractionEvent> {
        private final List<AiServiceEvent> events = new ArrayList<>();

        public AiServiceInteractionEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceInteractionEvent}.
         */
        public AiServiceInteractionEventBuilder(AiServiceInteractionEvent src) {
            super(src);
            events(src.events());
        }

        /**
         * Sets the invocation context for the event being built.
         */
        public AiServiceInteractionEventBuilder invocationContext(InvocationContext invocationContext) {
            return (AiServiceInteractionEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Adds multiple events to this builder.
         */
        public AiServiceInteractionEventBuilder events(List<AiServiceEvent> events) {
            if (events != null) {
                this.events.addAll(events);
            }
            return this;
        }

        /**
         * Adds a single event to the list of events for the event being built.
         */
        public AiServiceInteractionEventBuilder event(AiServiceEvent event) {
            if (event != null) {
                this.events.add(event);
            }
            return this;
        }

        /**
         * Returns immutable copy of events added to this builder.
         */
        public List<AiServiceEvent> events() {
            return List.copyOf(events);
        }

        /**
         * Builds a new {@link AiServiceInteractionEvent}
         */
        @Override
        public AiServiceInteractionEvent build() {
            return new DefaultAiServiceInteractionEvent(this);
        }
    }
}