package dev.langchain4j.model.chat.request;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.json.JsonSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
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

        public Builder modelName(String modelName) {
            this.parametersBuilder.modelName(modelName);
            return this;
        }

        // TODO convenience methods
        public Builder temperature(Double temperature) {
            this.parametersBuilder.temperature(temperature);
            return this;
        }

        public Builder topP(Double topP) {
            this.parametersBuilder.topP(topP);
            return this;
        }

        public Builder topK(Integer topK) {
            this.parametersBuilder.topK(topK);
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.parametersBuilder.frequencyPenalty(frequencyPenalty);
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.parametersBuilder.presencePenalty(presencePenalty);
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.parametersBuilder.maxOutputTokens(maxOutputTokens);
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.parametersBuilder.stopSequences(stopSequences);
            return this;
        }

        // TODO deprecate?
        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.parametersBuilder.toolSpecifications(toolSpecifications);
            return this;
        }

        // TODO deprecate?
        public Builder toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        // TODO remove/move to params?
        public Builder toolChoice(ToolChoice toolChoice) {
            this.parametersBuilder.toolChoice(toolChoice);
            return this;
        }

        // TODO deprecate?
        public Builder responseFormat(ResponseFormat responseFormat) {
            this.parametersBuilder.responseFormat(responseFormat);
            return this;
        }

        // TODO remove/move to params?
        public Builder responseFormat(JsonSchema jsonSchema) {
            if (jsonSchema != null) {
                ResponseFormat responseFormat = ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(jsonSchema)
                        .build();
                this.parametersBuilder.responseFormat(responseFormat);
            }
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(this);
        }
    }
}
