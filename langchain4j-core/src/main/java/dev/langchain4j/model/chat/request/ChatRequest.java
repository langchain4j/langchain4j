package dev.langchain4j.model.chat.request;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.json.JsonSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static java.util.Arrays.asList;

@Experimental
public class ChatRequest {

    private final String modelName; // TODO name: model?
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
    private final Double frequencyPenalty;
    private final Double presencePenalty;
    private final Integer maxOutputTokens;
    private final List<String> stopSequences;

    private final List<ChatMessage> messages;

    private final List<ToolSpecification> toolSpecifications;
    private final ToolChoice toolChoice;

    private final ResponseFormat responseFormat;

    private final Builder builder;

    protected ChatRequest(Builder builder) {
        this.modelName = builder.modelName;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.presencePenalty = builder.presencePenalty;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.stopSequences = copyIfNotNull(builder.stopSequences);
        this.messages = new ArrayList<>(ensureNotEmpty(builder.messages, "messages"));
        this.toolSpecifications = copyIfNotNull(builder.toolSpecifications);
        this.toolChoice = builder.toolChoice; // TODO set AUTO by default? only if toolSpecifications are present? validate: can be set only when tools are defined
        this.responseFormat = builder.responseFormat;
        this.builder = builder;
    }

    public Builder toBuilder() { // TODO not public
        return builder;
    }

    public String modelName() { // TODO name
        return modelName;
    }

    public Double temperature() {
        return temperature;
    }

    public Double topP() {
        return topP;
    }

    public Integer topK() {
        return topK;
    }

    public Double frequencyPenalty() {
        return frequencyPenalty;
    }

    public Double presencePenalty() {
        return presencePenalty;
    }

    public Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    public List<String> stopSequences() {
        return stopSequences;
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
        return Objects.equals(this.modelName, that.modelName)
                && Objects.equals(this.temperature, that.temperature)
                && Objects.equals(this.topP, that.topP)
                && Objects.equals(this.topK, that.topK)
                && Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
                && Objects.equals(this.presencePenalty, that.presencePenalty)
                && Objects.equals(this.maxOutputTokens, that.maxOutputTokens)
                && Objects.equals(this.stopSequences, that.stopSequences)
                && Objects.equals(this.messages, that.messages)
                && Objects.equals(this.toolSpecifications, that.toolSpecifications)
                && Objects.equals(this.toolChoice, that.toolChoice)
                && Objects.equals(this.responseFormat, that.responseFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                modelName,
                temperature,
                topP,
                topK,
                frequencyPenalty,
                presencePenalty,
                maxOutputTokens,
                stopSequences,
                messages,
                toolSpecifications,
                toolChoice,
                responseFormat
        );
    }

    @Override
    public String toString() {
        return "ChatRequest {" +
                " modelName = " + quoted(modelName) + // TODO name
                ", temperature = " + temperature +
                ", topP = " + topP +
                ", topK = " + topK +
                ", frequencyPenalty = " + frequencyPenalty +
                ", presencePenalty = " + presencePenalty +
                ", maxOutputTokens = " + maxOutputTokens +
                ", stopSequences = " + stopSequences +
                ", messages = " + messages +
                ", toolSpecifications = " + toolSpecifications +
                ", toolChoice = " + toolChoice +
                ", responseFormat = " + responseFormat +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder<T extends Builder<T>> {

        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer maxOutputTokens;
        private List<String> stopSequences;
        private List<ChatMessage> messages;
        private List<ToolSpecification> toolSpecifications;
        private ToolChoice toolChoice;
        private ResponseFormat responseFormat;

        public T modelName(String modelName) {
            this.modelName = modelName;
            return (T) this;
        }

        public T temperature(Double temperature) {
            this.temperature = temperature;
            return (T) this;
        }

        public T topP(Double topP) {
            this.topP = topP;
            return (T) this;
        }

        public T topK(Integer topK) {
            this.topK = topK;
            return (T) this;
        }

        public T frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return (T) this;
        }

        public T presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return (T) this;
        }

        public T maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return (T) this;
        }

        public T stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return (T) this;
        }

        public T messages(List<ChatMessage> messages) {
            this.messages = messages;
            return (T) this;
        }

        public T messages(ChatMessage... messages) {
            return messages(asList(messages));
        }

        public T toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return (T) this;
        }

        public T toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        public T toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return (T) this;
        }

        public T responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return (T) this;
        }

        public T responseFormat(JsonSchema jsonSchema) {
            if (jsonSchema != null) {
                this.responseFormat = ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(jsonSchema)
                        .build();
            }
            return (T) this;
        }

        public ChatRequest build() {
            return new ChatRequest(this);
        }
    }
}
