package dev.langchain4j.agentic.scope;

/**
 * Holds the result of an agent invocation along with its associated {@link AgenticScope}.
 * This is useful for returning results from agents while also providing access to the cognitive
 * scope through which that result has been generated.
 *
 * @param <T> The type of the result.
 */
public record ResultWithAgenticScope<T>(AgenticScope agenticScope, T result) { }
