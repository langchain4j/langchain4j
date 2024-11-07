package dev.langchain4j.model.chat.request;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Arrays.asList;

@Experimental
public class ChatRequest {

    private final List<ChatMessage> messages;
    private final List<ToolSpecification> toolSpecifications; // TODO introduce "tools" section?
    private final ToolChoice toolChoice;
    private final ResponseFormat responseFormat;

    private ChatRequest(Builder builder) {
        this.messages = new ArrayList<>(ensureNotEmpty(builder.messages, "messages"));
        this.toolSpecifications = copyIfNotNull(builder.toolSpecifications);
        this.toolChoice = builder.toolChoice; // TODO set AUTO by default? only if toolSpecifications are present? validate: can be set only when tools are defined
        this.responseFormat = builder.responseFormat;
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    public ToolChoice toolChoice() {
        return toolChoice;
    }

    public ResponseFormat responseFormat() {
        return responseFormat;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatRequest that = (ChatRequest) o;
        return Objects.equals(this.messages, that.messages)
            && Objects.equals(this.toolSpecifications, that.toolSpecifications)
            && Objects.equals(this.toolChoice, that.toolChoice)
            && Objects.equals(this.responseFormat, that.responseFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messages, toolSpecifications, toolChoice, responseFormat);
    }

    @Override
    public String toString() {
        return "ChatRequest {" +
            " messages = " + messages +
            ", toolSpecifications = " + toolSpecifications +
            ", toolChoice = " + toolChoice +
            ", responseFormat = " + responseFormat +
            " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<ChatMessage> messages;
        private List<ToolSpecification> toolSpecifications;
        private ToolChoice toolChoice;
        private ResponseFormat responseFormat;

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder messages(ChatMessage... messages) {
            return messages(asList(messages));
        }

        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        public Builder toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        public Builder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        // TODO consider adding responseFormat(JsonSchema) or jsonSchema(JsonSchema)

        public ChatRequest build() {
            return new ChatRequest(this);
        }
    }
}
