package dev.langchain4j.model.azure;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;

import java.util.List;

import static java.util.Arrays.asList;

public class AzureAiMessage extends AiMessage {

    public AzureAiMessage(List<ToolExecutionRequest> toolExecutionRequests) {
        super(toolExecutionRequests);
    }

    public static AiMessage aiMessage(ToolExecutionRequest... toolExecutionRequests) {
        return aiMessage(asList(toolExecutionRequests));
    }

    /**
     * Returns an empty String as Azure OpenAI requires a non-Null object.
     */
    @Override
    public String text() {
        return "";
    }
}
