package dev.langchain4j.exception;

/**
 * Thrown when a requested feature is not supported by the current model provider
 * or the specific model being used.
 * <p>
 * Examples include requesting structured output from a model that does not support
 * it, or enabling tool-use on a provider whose API does not offer function calling.
 * The caller should either switch to a compatible model or adjust the request to
 * avoid the unsupported feature.
 * <p>
 * It is a {@link NonRetriableException}: a provider that does not support a feature will not begin to support it
 * on a retry, so retrying (with back-off) would only add latency before failing.
 *
 * @see AsyncNotSupportedException
 */
public class UnsupportedFeatureException extends NonRetriableException {

    public UnsupportedFeatureException(String message) {
        super(message);
    }
}
