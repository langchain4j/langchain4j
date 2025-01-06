package dev.langchain4j.model.chat.request;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Arrays.asList;

@Experimental
public class ChatRequest {

    private final List<ChatMessage> messages;
    private final ChatRequestParameters parameters;

    protected ChatRequest(Builder builder) {
        this.messages = new ArrayList<>(ensureNotEmpty(builder.messages, "messages"));

        DefaultChatRequestParameters.Builder<?> parametersBuilder = ChatRequestParameters.builder();

        if (!isNullOrEmpty(builder.toolSpecifications)) {
            if (builder.parameters != null) {
                throw new IllegalArgumentException(
                        "Cannot set both 'parameters' and 'toolSpecifications' on ChatRequest");
            }
            parametersBuilder.toolSpecifications(builder.toolSpecifications);
        }

        if (builder.responseFormat != null) {
            if (builder.parameters != null) {
                throw new IllegalArgumentException(
                        "Cannot set both 'parameters' and 'responseFormat' on ChatRequest");
            }
            parametersBuilder.responseFormat(builder.responseFormat);
        }

        if (builder.parameters != null) {
            this.parameters = builder.parameters;
        } else {
            this.parameters = parametersBuilder.build();
        }
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    @Experimental
    public ChatRequestParameters parameters() {
        return parameters;
    }

    // TODO deprecate
    public List<ToolSpecification> toolSpecifications() {
        return parameters.toolSpecifications();
    }

    // TODO deprecate
    public ResponseFormat responseFormat() {
        return parameters.responseFormat();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatRequest that = (ChatRequest) o;
        return Objects.equals(this.messages, that.messages)
                && Objects.equals(this.parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messages, parameters);
    }

    @Override
    public String toString() {
        return "ChatRequest {" +
                " messages = " + messages +
                ", parameters = " + parameters +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<ChatMessage> messages;
        private ChatRequestParameters parameters;
        private List<ToolSpecification> toolSpecifications;
        private ResponseFormat responseFormat;

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder messages(ChatMessage... messages) {
            return messages(asList(messages));
        }

        @Experimental
        public Builder parameters(ChatRequestParameters parameters) {
            this.parameters = parameters;
            return this;
        }

        // TODO deprecate
        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        // TODO deprecate
        public Builder toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        // TODO deprecate
        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(this);
        }
    }
}
