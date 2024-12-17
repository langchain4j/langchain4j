package dev.langchain4j.model.chat.request;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

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
}
