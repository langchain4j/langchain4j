package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Arrays.asList;

/**
 * TODO
 */
public class ChatRequest {
    // TODO name

    // TODO api key? more generic auth type?
    private final ModelParameters parameters; // TODO name

    private final List<ChatMessage> messages;

    private final List<ToolSpecification> toolSpecifications; // TODO into tool section?
    private final ToolMode toolMode; // TODO into tool section?
    // TODO tool call mode (exact, any, auto, etc)

    @Builder
    private ChatRequest(ModelParameters parameters,
                        List<ChatMessage> messages,
                        List<ToolSpecification> toolSpecifications,  // TODO Collection<ToolSpecification>?
                        ToolMode toolMode) {
        this.parameters = getOrDefault(parameters, ModelParameters.builder().build()); // TODO
        this.messages = new ArrayList<>(ensureNotEmpty(messages, "messages"));
        this.toolSpecifications = copyIfNotNull(toolSpecifications);
        this.toolMode = toolMode;
    }

    public static class ChatRequestBuilder {

        public ChatRequestBuilder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public ChatRequestBuilder messages(ChatMessage... messages) {
            this.messages = asList(messages);
            return this;
        }

        public ChatRequestBuilder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        public ChatRequestBuilder toolSpecifications(ToolSpecification... toolSpecifications) {
            this.toolSpecifications = asList(toolSpecifications);
            return this;
        }
    }

    public ModelParameters modelParameters() {
        return parameters;
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    public ToolMode toolMode() {
        return toolMode;
    }

    // TODO default ctor? setters?
}
