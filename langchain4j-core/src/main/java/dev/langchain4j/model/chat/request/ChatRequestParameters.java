package dev.langchain4j.model.chat.request;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;
import org.jspecify.annotations.Nullable;
import dev.langchain4j.model.HttpParameters;

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
     * Returns the parts of the HTTP request (for example custom HTTP headers) to send with this request,
     * on top of the request body that the other parameters make up.
     *
     * When parameters are combined with {@link #overrideWith(ChatRequestParameters)} or
     * {@link #defaultedBy(ChatRequestParameters)}, custom HTTP headers are merged by name (ignoring case)
     * instead of being replaced as a whole.
     *
     * @return the HTTP parameters, or {@code null} if none are set
     * @see HttpParameters
     * @since 1.24.0
     */
    @Experimental
    default @Nullable HttpParameters httpParameters() {
        return null;
    }

    /**
     * Creates a new {@link ChatRequestParameters} by combining the current parameters with the specified ones.
     * Values from the specified parameters override values from the current parameters when there is overlap.
     * Neither the current nor the specified {@link ChatRequestParameters} objects are modified.
     * Custom HTTP headers in {@link #httpParameters()} are merged by name (ignoring case), the specified ones winning.
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
     * Custom HTTP headers in {@link #httpParameters()} are merged by name (ignoring case), the current ones winning.
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
