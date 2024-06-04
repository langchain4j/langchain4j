package dev.langchain4j.model.chat.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.internal.Utils.copyIfNotNull;

/**
 * A request to the {@link ChatLanguageModel} or {@link StreamingChatLanguageModel},
 * intended to be used with {@link ChatModelListener}.
 */
@Experimental
public class ChatModelRequest {

    private final String model;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final List<ChatMessage> messages;
    private final List<ToolSpecification> toolSpecifications;

    @Builder
    public ChatModelRequest(String model,
                            Double temperature,
                            Double topP,
                            Integer maxTokens,
                            List<ChatMessage> messages,
                            List<ToolSpecification> toolSpecifications) {
        this.model = model;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.messages = copyIfNotNull(messages);
        this.toolSpecifications = copyIfNotNull(toolSpecifications);
    }

    public String model() {
        return model;
    }

    public Double temperature() {
        return temperature;
    }

    public Double topP() {
        return topP;
    }

    public Integer maxTokens() {
        return maxTokens;
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }
}
