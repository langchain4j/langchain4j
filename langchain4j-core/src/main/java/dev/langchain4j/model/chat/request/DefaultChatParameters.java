package dev.langchain4j.model.chat.request;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonSchema;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static java.util.Arrays.asList;

@Experimental
public class DefaultChatParameters implements ChatParameters {
    // TODO name
    // TODO place

    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
    private final Double frequencyPenalty;
    private final Double presencePenalty;
    private final Integer maxOutputTokens;
    private final List<String> stopSequences;
    private final List<ToolSpecification> toolSpecifications;
    private final ToolChoice toolChoice;
    private final ResponseFormat responseFormat;

    protected DefaultChatParameters(Builder<?> builder) { // TODO visibility
        this.modelName = builder.modelName;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.presencePenalty = builder.presencePenalty;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.stopSequences = copyIfNotNull(builder.stopSequences);
        this.toolSpecifications = copyIfNotNull(builder.toolSpecifications);
        this.toolChoice = builder.toolChoice; // TODO set AUTO by default? only if toolSpecifications are present? validate: can be set only when tools are defined
        this.responseFormat = builder.responseFormat;
    }

    protected DefaultChatParameters(ChatParameters chatParameters) { // TODO visibility
        this.modelName = chatParameters.modelName();
        this.temperature = chatParameters.temperature();
        this.topP = chatParameters.topP();
        this.topK = chatParameters.topK();
        this.frequencyPenalty = chatParameters.frequencyPenalty();
        this.presencePenalty = chatParameters.presencePenalty();
        this.maxOutputTokens = chatParameters.maxOutputTokens();
        this.stopSequences = copyIfNotNull(chatParameters.stopSequences());
        this.toolSpecifications = copyIfNotNull(chatParameters.toolSpecifications());
        this.toolChoice = chatParameters.toolChoice();
        this.responseFormat = chatParameters.responseFormat();
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public Double temperature() {
        return temperature;
    }

    @Override
    public Double topP() {
        return topP;
    }

    @Override
    public Integer topK() {
        return topK;
    }

    @Override
    public Double frequencyPenalty() {
        return frequencyPenalty;
    }

    @Override
    public Double presencePenalty() {
        return presencePenalty;
    }

    @Override
    public Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    @Override
    public List<String> stopSequences() {
        return stopSequences;
    }

    @Override
    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    @Override
    public ToolChoice toolChoice() {
        return toolChoice;
    }

    @Override
    public ResponseFormat responseFormat() {
        return responseFormat;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultChatParameters that = (DefaultChatParameters) o;
        return Objects.equals(modelName, that.modelName)
                && Objects.equals(temperature, that.temperature)
                && Objects.equals(topP, that.topP)
                && Objects.equals(topK, that.topK)
                && Objects.equals(frequencyPenalty, that.frequencyPenalty)
                && Objects.equals(presencePenalty, that.presencePenalty)
                && Objects.equals(maxOutputTokens, that.maxOutputTokens)
                && Objects.equals(stopSequences, that.stopSequences)
                && Objects.equals(toolSpecifications, that.toolSpecifications)
                && Objects.equals(toolChoice, that.toolChoice)
                && Objects.equals(responseFormat, that.responseFormat);
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
                toolSpecifications,
                toolChoice,
                responseFormat
        );
    }

    public static Builder<?> builder() { // TODO?
        return new Builder<>();
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
        private List<ToolSpecification> toolSpecifications;
        private ToolChoice toolChoice;
        private ResponseFormat responseFormat;

        public T modelName(String modelName) { // TODO another one accepting enum?
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

        public T stopSequences(String... stopSequences) {
            return stopSequences(asList(stopSequences));
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

        /**
         * @see #responseFormat(JsonSchema)
         */
        public T responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return (T) this;
        }

        /**
         * @see #responseFormat(ResponseFormat)
         */
        public T responseFormat(JsonSchema jsonSchema) {
            if (jsonSchema != null) {
                ResponseFormat responseFormat = ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(jsonSchema)
                        .build();
                return responseFormat(responseFormat);
            }
            return (T) this;
        }

        public DefaultChatParameters build() {
            return new DefaultChatParameters(this);
        }
    }
}
