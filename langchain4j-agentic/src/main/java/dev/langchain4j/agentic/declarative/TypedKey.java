package dev.langchain4j.agentic.declarative;

/**
 * A class implementing this interface represents the input or output key of an agent in a strongly typed way.
 * This corresponds to a state of the agentic system that can be used to store and retrieve information during the system's operation.
 *
 * @param <T> The type of the state value.
 */
public interface TypedKey<T> {

    /**
     * Returns the default value for this state.
     * This method can be overridden to provide a specific default value.
     *
     * @return the default value of type T, or null if not overridden.
     */
    default T defaultValue() {
        return null;
    }

    /**
     * Returns the name of this state used inside the agentic system and for prompt templating.
     * By default, it is the simple name of the implementing class.
     *
     * @return the name of the state.
     */
    default String name() {
        return this.getClass().getSimpleName();
    }
}
