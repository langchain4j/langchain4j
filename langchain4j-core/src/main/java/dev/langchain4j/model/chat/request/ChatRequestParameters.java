package dev.langchain4j.model.chat.request;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;

/**
 * Represents common chat request parameters supported by most LLM providers.
 * Specific LLM provider integrations can extend this interface to add provider-specific parameters.
 *
 * @see DefaultChatRequestParameters
 */
@Experimental
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

    static DefaultChatRequestParameters.Builder<?> builder() { // TODO
        return new DefaultChatRequestParameters.Builder<>();
    }

    /**
     * Create a new builder, initialized from the given ChatRequestParameters instance.
     * This lets you clone or merge parameters easily:
     * <pre>
     *   ChatRequestParameters merged =
     *       ChatRequestParameters.builder(original)
     *           .temperature(0.9)
     *           .build();
     * </pre>
     */
    static DefaultChatRequestParameters.Builder<?> builder(ChatRequestParameters from) {
        return new DefaultChatRequestParameters.Builder<>().copyOf(from);
    }
}
