package dev.langchain4j.agentic.cognisphere;

/**
 * Holds the result of an agent invocation along with its associated {@link DefaultCognisphere}.
 * This is useful for returning results from agents while also providing access to the cognitive
 * context through which that result has been generated.
 *
 * @param <T> The type of the result.
 */
public record ResultWithCognisphere<T>(Cognisphere cognisphere, T result) { }
