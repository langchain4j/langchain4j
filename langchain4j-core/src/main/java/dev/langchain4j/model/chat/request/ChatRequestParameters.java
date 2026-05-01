package dev.langchain4j.model.chat.request;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.time.Duration;
import java.util.List;

/**
 * Represents common chat request parameters supported by most LLM providers.
 * Specific LLM provider integrations can extend this interface to add provider-specific parameters.
 *
 * @see DefaultChatRequestParameters
 */
public interface ChatRequestParameters {

    String modelName();

    Double temperature();

    Double topP();

    Integer topK();

    Double frequencyPenalty();

    Double presencePenalty();

    Integer maxOutputTokens();

    List<String> stopSequences();

    List<ToolSpecification> toolSpecifications();

    ToolChoice toolChoice();

    ResponseFormat responseFormat();

    /**
     * Per-request HTTP read timeout. When set, it overrides the default read timeout configured
     * on the underlying HTTP client for the duration of this single request. {@code null} means
     * the client's default applies.
     *
     * <p>Useful when a single model instance needs different timeouts depending on the workload —
     * e.g. a short timeout for interactive requests and a longer one for background batch jobs —
     * without having to construct a new model per timeout.
     *
     * <p>Provider integrations are responsible for plumbing this value to the underlying HTTP layer.
     *
     * @since 1.14.0
     */
    default Duration timeout() {
        return null;
    }

    /**
     * Creates a new {@link ChatRequestParameters} by combining the current parameters with the specified ones.
     * Values from the specified parameters override values from the current parameters when there is overlap.
     * Neither the current nor the specified {@link ChatRequestParameters} objects are modified.
     *
     * <p>Example:
     * <pre>
     * Current parameters:
     *   temperature = 1.0
     *   maxOutputTokens = 100
     *
     * Specified parameters:
     *   temperature = 0.5
     *   modelName = my-model
     *
     * Result:
     *   temperature = 0.5        // Overridden from specified
     *   maxOutputTokens = 100    // Preserved from current
     *   modelName = my-model     // Added from specified
     * </pre>
     *
     * @param parameters the parameters whose values will override the current ones
     * @return a new {@link ChatRequestParameters} instance combining both sets of parameters
     */
    ChatRequestParameters overrideWith(ChatRequestParameters parameters);

    /**
     * Creates a new {@link ChatRequestParameters} by combining the current parameters with the specified ones.
     * Values from the current parameters take precedence over values from the specified parameters when there is overlap.
     * Neither the current nor the specified {@link ChatRequestParameters} objects are modified.
     *
     * <p>Example:
     * <pre>
     * Current parameters:
     *   temperature = 1.0
     *   maxOutputTokens = 100
     *
     * Specified parameters:
     *   temperature = 0.5
     *   modelName = my-model
     *
     * Result:
     *   temperature = 1.0        // Preserved from current
     *   maxOutputTokens = 100    // Preserved from current
     *   modelName = my-model     // Added from specified
     * </pre>
     *
     * @param parameters the parameters whose values will be used as a default for the current ones
     * @return a new {@link ChatRequestParameters} instance combining both sets of parameters
     */
    default ChatRequestParameters defaultedBy(ChatRequestParameters parameters) {
        throw new UnsupportedOperationException("Missing implementation, please override this method in " + this.getClass().getName());
    }

    static DefaultChatRequestParameters.Builder<?> builder() {
        return new DefaultChatRequestParameters.Builder<>();
    }
}
