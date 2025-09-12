package dev.langchain4j.audit.api.event;

public interface LLMInteractionEvent {
    /**
     * Retrieves the source of the interaction, containing general information
     * about where and how the interaction originated.
     */
    InteractionSource interactionSource();

    /**
     * Retrieves the class type of the event, representing the specific category
     * of the LLM interaction event.
     */
    // Implementation note: I implemented it this way on purpose (rather than defining an enum of "Event Types")
    // So that downstream frameworks/applications could define their own event types and still use the
    // registration/firing mechanisms provided here in LC4j
    <T extends LLMInteractionEvent> Class<T> eventClass();

    /**
     * Creates a new builder instance initialized with the properties of this {@link LLMInteractionEvent}.
     * This allows modification of the existing properties and reconstruction of the event.
     */
    <T extends LLMInteractionEvent> Builder<T> toBuilder();

    /**
     * An abstract base class for building instances of types that extend {@link LLMInteractionEvent}.
     * This class provides a fluent interface for setting properties necessary
     * for constructing an {@link LLMInteractionEvent}.
     *
     * @param <T> the specific type of {@link LLMInteractionEvent} being built
     */
    abstract class Builder<T extends LLMInteractionEvent> {
        private InteractionSource interactionSource;

        protected Builder() {}

        protected Builder(T src) {
            this.interactionSource = src.interactionSource();
        }

        public InteractionSource getInteractionSource() {
            return this.interactionSource;
        }

        public Builder<T> interactionSource(InteractionSource interactionSource) {
            this.interactionSource = interactionSource;
            return this;
        }

        public abstract T build();
    }
}
