package dev.langchain4j.model.chat;

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
    private final List<ToolSpecification> toolSpecifications;
    private final ResponseFormatSpecification responseFormatSpecification; // TODO name response vs result, output? OutputFormat?

    private ChatRequest(Builder builder) {
        this.messages = new ArrayList<>(ensureNotEmpty(builder.messages, "messages"));
        this.toolSpecifications = copyIfNotNull(builder.toolSpecifications);
        this.responseFormatSpecification = builder.responseFormatSpecification;
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    public ResponseFormatSpecification responseFormatSpecification() {
        return responseFormatSpecification;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatRequest that = (ChatRequest) o;
        return Objects.equals(this.messages, that.messages)
                && Objects.equals(this.toolSpecifications, that.toolSpecifications)
                && Objects.equals(this.responseFormatSpecification, that.responseFormatSpecification);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messages, toolSpecifications, responseFormatSpecification);
    }

    @Override
    public String toString() {
        return "ChatRequest {" +
                " messages = " + messages +
                ", toolSpecifications = " + toolSpecifications +
                ", responseFormatSpecification = " + responseFormatSpecification +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<ChatMessage> messages;
        private List<ToolSpecification> toolSpecifications;
        private ResponseFormatSpecification responseFormatSpecification;

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

        public Builder responseFormatSpecification(ResponseFormatSpecification responseFormatSpecification) {
            this.responseFormatSpecification = responseFormatSpecification;
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(this);
        }
    }
}
