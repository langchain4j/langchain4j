package dev.langchain4j.model.output;


import java.util.Set;

/**
 * Represents an output parser.
 * @param <T> the type of the output.
 */
public interface OutputParser<T> {
    /**
     * Get the supported types.
     * @return the supported types.
     */
    Set<Class<?>> getSupportedTypes();

    /**
     * Parse the given response using a context object.
     *
     * @param context the parsing context.
     * @return the parsed output.
     */
    T parse(final OutputParsingContext context);

    /**
     * Get the LLM-facing formatting instructions for the output.
     * @return the formatting instructions.
     */
    String formatInstructions();

    /**
     * Override this method to provide a custom prelude for the formatting instructions.
     * @return the custom prelude.
     */
    default String customFormatPrelude() {
        return null;
    }

    /**
     * Override this method to provide a custom postlude for the formatting instructions.
     * @return the custom postlude.
     */
    default String customFormatPostlude() {
        return null;
    }
}
