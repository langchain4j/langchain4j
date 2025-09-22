package dev.langchain4j.audit.api.event;

public interface AiServiceInvocationEvent {
    /**
     * Retrieves the source of the interaction, containing general information
     * about where and how the interaction originated.
     */
    AiServiceInvocationContext invocationContext();

    /**
     * Retrieves the class type of the event, representing the specific category
     * of the LLM interaction event.
     */
    // Implementation note: I implemented it this way on purpose (rather than defining an enum of "Event Types")
    // So that downstream frameworks/applications could define their own event types and still use the
    // registration/firing mechanisms provided here in LC4j
    <T extends AiServiceInvocationEvent> Class<T> eventClass();

    /**
     * Creates a new builder instance initialized with the properties of this {@link AiServiceInvocationEvent}.
     * This allows modification of the existing properties and reconstruction of the event.
     */
    <T extends AiServiceInvocationEvent> Builder<T> toBuilder();

    /**
     * An abstract base class for building instances of types that extend {@link AiServiceInvocationEvent}.
     * This class provides a fluent interface for setting properties necessary
     * for constructing an {@link AiServiceInvocationEvent}.
     *
     * @param <T> the specific type of {@link AiServiceInvocationEvent} being built
     */
    abstract class Builder<T extends AiServiceInvocationEvent> {
        private AiServiceInvocationContext invocationContext;

        protected Builder() {}

        protected Builder(T src) {
            this.invocationContext = src.invocationContext();
        }

        public AiServiceInvocationContext getInvocationContext() {
            return this.invocationContext;
        }

        public Builder<T> invocationContext(AiServiceInvocationContext invocationContext) {
            this.invocationContext = invocationContext;
            return this;
        }

        public abstract T build();
    }
}
