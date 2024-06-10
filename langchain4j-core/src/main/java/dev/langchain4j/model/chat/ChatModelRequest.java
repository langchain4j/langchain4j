package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

/**
 * TODO
 */
public class ChatModelRequest {
    // TODO name

    // TODO api key? more generic auth type?
    private final ChatModelParameters parameters;
    private final List<ChatMessage> messages;
    private final List<ToolSpecification> toolSpecifications;
    // TODO tool call mode (exact, any, auto, etc)

    @Builder
    private ChatModelRequest(ChatModelParameters parameters,
                             List<ChatMessage> messages,
                             List<ToolSpecification> toolSpecifications) { // TODO Collection<ToolSpecification>?
        this.parameters = parameters;
        this.messages = new ArrayList<>(ensureNotEmpty(messages, "messages"));
        this.toolSpecifications = copyIfNotNull(toolSpecifications);
    }

    public ChatModelParameters parameters() {
        return parameters;
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    // TODO default ctor? setters?
}
