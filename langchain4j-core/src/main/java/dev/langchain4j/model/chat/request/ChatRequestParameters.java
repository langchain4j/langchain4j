package dev.langchain4j.model.chat.request;

import dev.langchain4j.agent.tool.ToolSpecification;

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

    static DefaultChatRequestParameters.Builder<?> builder() {
        return new DefaultChatRequestParameters.Builder<>();
    }
}
