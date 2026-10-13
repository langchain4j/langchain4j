package dev.langchain4j.observability.api.event;

import dev.langchain4j.invocation.InvocationContext;
import java.time.Instant;

public interface AiServiceEvent {
    /**
     * Retrieves the invocation context, containing general information
     * about where and how the invocation originated.
     */
    InvocationContext invocationContext();

    /**
     * The instant at which this event was created.
     */
    Instant timestamp();

    /**
     * Retrieves the class type of the event, representing the specific category
     * of the AI Service invocation event.
     */
    // Implementation note: I implemented it this way on purpose (rather than defining an enum of "Event Types")
    // So that downstream frameworks/applications could define their own event types and still use the
    // registration/firing mechanisms provided here in LC4j
    <T extends AiServiceEvent> Class<T> eventClass();

    /**
     * Creates a new builder instance initialized with the properties of this {@link AiServiceEvent}.
     * This allows modification of the existing properties and reconstruction of the event.
     */
    <T extends AiServiceEvent> Builder<T> toBuilder();

    /**
     * An abstract base class for building instances of types that extend {@link AiServiceEvent}.
     * This class provides a fluent interface for setting properties necessary
     * for constructing an {@link AiServiceEvent}.
     *
     * @param <T> the specific type of {@link AiServiceEvent} being built
     */
    abstract class Builder<T extends AiServiceEvent> {
        private InvocationContext invocationContext;
        private Instant timestamp;

        protected Builder() {}

        protected Builder(T src) {
            this.invocationContext = src.invocationContext();
            this.timestamp = src.timestamp();
        }

        public InvocationContext invocationContext() {
            return this.invocationContext;
        }

        public Builder<T> invocationContext(InvocationContext invocationContext) {
            this.invocationContext = invocationContext;
            return this;
        }

        public Instant timestamp() {
            return this.timestamp;
        }

        /**
         * Overrides the event timestamp. If not set, the time of construction is used.
         */
        public Builder<T> timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public abstract T build();
    }
}
