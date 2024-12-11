package dev.langchain4j.model.chat.request;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Arrays.asList;

@Experimental
public class ChatRequest {

    private final List<ChatMessage> messages;
    private final ChatParameters parameters;

    protected ChatRequest(Builder builder) {
        this.messages = new ArrayList<>(ensureNotEmpty(builder.messages, "messages"));
        // TODO check either params or params builder is set
        if (builder.parameters != null) {
            this.parameters = builder.parameters;
        } else {
            this.parameters = builder.parametersBuilder.build();
        }
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    public ChatParameters parameters() { // TODO name
        return parameters;
    }

    /**
     * @deprecated use {@link #parameters()} and then {@link ChatParameters#toolSpecifications()}
     */
    @Deprecated(forRemoval = true)
    public List<ToolSpecification> toolSpecifications() {
        return parameters.toolSpecifications();
    }

    /**
     * @deprecated use {@link #parameters()} and then {@link ChatParameters#responseFormat()}
     */
    @Deprecated(forRemoval = true)
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
        return "ChatRequest {" + // TODO names
                " messages = " + messages +
                ", parameters = " + parameters +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<ChatMessage> messages;
        private ChatParameters parameters; // TODO validate that does not overlap with builder
        private DefaultChatParameters.Builder parametersBuilder = new DefaultChatParameters.Builder();

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder messages(ChatMessage... messages) {
            return messages(asList(messages));
        }

        public Builder parameters(ChatParameters parameters) {
            this.parameters = parameters;
            return this;
        }

        /**
         * @deprecated TODO
         */
        @Deprecated(forRemoval = true)
        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.parametersBuilder.toolSpecifications(toolSpecifications);
            return this;
        }

        /**
         * @deprecated TODO
         */
        @Deprecated(forRemoval = true)
        public Builder toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        /**
         * @deprecated TODO
         */
        @Deprecated(forRemoval = true)
        public Builder responseFormat(ResponseFormat responseFormat) {
            this.parametersBuilder.responseFormat(responseFormat);
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(this);
        }
    }
}
