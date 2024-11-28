package dev.langchain4j.model.chat.request;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Arrays.asList;

@Experimental
public class ChatRequest {
    // TODO reconsider structure of this class

    private final String modelName; // TODO model? move to ChatParameters?

    private final List<ChatMessage> messages;

    private final ChatParameters parameters;

    // TODO separate section for tools?
    private final List<ToolSpecification> toolSpecifications;
    private final ToolChoice toolChoice;

    private final ResponseFormat responseFormat;

    // TODO custom map of params? to be used for new params/features before typesafe versions are released? at least for popular providers

    private ChatRequest(Builder builder) {
        this.modelName = builder.modelName;
        this.messages = new ArrayList<>(ensureNotEmpty(builder.messages, "messages"));
        this.parameters = builder.parameters;
        this.toolSpecifications = copyIfNotNull(builder.toolSpecifications);
        this.toolChoice = builder.toolChoice; // TODO set AUTO by default? only if toolSpecifications are present? validate: can be set only when tools are defined
        this.responseFormat = builder.responseFormat;
    }

    public String modelName() { // TODO names
        return modelName;
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    public ChatParameters parameters() { // TODO names
        return parameters;
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
                && Objects.equals(this.messages, that.messages)
                && Objects.equals(this.parameters, that.parameters)
                && Objects.equals(this.toolSpecifications, that.toolSpecifications)
                && Objects.equals(this.toolChoice, that.toolChoice)
                && Objects.equals(this.responseFormat, that.responseFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                modelName,
                messages,
                parameters,
                toolSpecifications,
                toolChoice,
                responseFormat
        );
    }

    @Override
    public String toString() {
        return "ChatRequest {" +
                " modelName = " + quoted(modelName) + // TODO names
                ", messages = " + messages +
                ", parameters = " + parameters + // TODO names
                ", toolSpecifications = " + toolSpecifications +
                ", toolChoice = " + toolChoice +
                ", responseFormat = " + responseFormat +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String modelName;
        private List<ChatMessage> messages;
        private ChatParameters parameters;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer maxOutputTokens;
        private List<String> stopSequences;
        private List<ToolSpecification> toolSpecifications;
        private ToolChoice toolChoice;
        private ResponseFormat responseFormat;

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder messages(ChatMessage... messages) {
            return messages(asList(messages));
        }

        public Builder parameters(ChatParameters parameters) { // TODO names
            this.parameters = parameters;
            return this;
        }

        public Builder temperature(Double temperature) { // TODO remove?
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) { // TODO remove?
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) { // TODO remove?
            this.topK = topK;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) { // TODO remove?
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) { // TODO remove?
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) { // TODO remove?
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) { // TODO remove?
            this.stopSequences = stopSequences;
            return this;
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
            if (this.parameters == null) {
                this.parameters = ChatParameters.builder()
                        .temperature(temperature)
                        .topP(topP)
                        .topK(topK)
                        .frequencyPenalty(frequencyPenalty)
                        .presencePenalty(presencePenalty)
                        .maxOutputTokens(maxOutputTokens)
                        .stopSequences(stopSequences)
                        .build();
            }
            return new ChatRequest(this);
        }
    }
}
