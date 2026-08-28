package dev.langchain4j.exception;

/**
 * Thrown when a requested feature is not supported by the current model provider
 * or the specific model being used.
 * <p>
 * Examples include requesting structured output from a model that does not support
 * it, or enabling tool-use on a provider whose API does not offer function calling.
 * The caller should either switch to a compatible model or adjust the request to
 * avoid the unsupported feature.
 */
public class UnsupportedFeatureException extends LangChain4jException {

    public UnsupportedFeatureException(String message) {
        super(message);
    }
}
