package dev.langchain4j.model.chat.request;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonSchema;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static java.util.Arrays.asList;

public class DefaultChatRequestParameters implements ChatRequestParameters {

    public static final ChatRequestParameters EMPTY = DefaultChatRequestParameters.builder().build();

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

    protected DefaultChatRequestParameters(Builder<?> builder) {
        this.modelName = builder.modelName;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.presencePenalty = builder.presencePenalty;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.stopSequences = copy(builder.stopSequences);
        this.toolSpecifications = copy(builder.toolSpecifications);
        this.toolChoice = builder.toolChoice;
        this.responseFormat = builder.responseFormat;
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
    public ChatRequestParameters overrideWith(ChatRequestParameters that) {
        return DefaultChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultChatRequestParameters that = (DefaultChatRequestParameters) o;
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

    @Override
    public String toString() {
        return "DefaultChatRequestParameters{" +
                "modelName='" + modelName + '\'' +
                ", temperature=" + temperature +
                ", topP=" + topP +
                ", topK=" + topK +
                ", frequencyPenalty=" + frequencyPenalty +
                ", presencePenalty=" + presencePenalty +
                ", maxOutputTokens=" + maxOutputTokens +
                ", stopSequences=" + stopSequences +
                ", toolSpecifications=" + toolSpecifications +
                ", toolChoice=" + toolChoice +
                ", responseFormat=" + responseFormat +
                '}';
    }

    public static Builder<?> builder() {
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

        public T overrideWith(ChatRequestParameters parameters) {
            modelName(getOrDefault(parameters.modelName(), modelName));
            temperature(getOrDefault(parameters.temperature(), temperature));
            topP(getOrDefault(parameters.topP(), topP));
            topK(getOrDefault(parameters.topK(), topK));
            frequencyPenalty(getOrDefault(parameters.frequencyPenalty(), frequencyPenalty));
            presencePenalty(getOrDefault(parameters.presencePenalty(), presencePenalty));
            maxOutputTokens(getOrDefault(parameters.maxOutputTokens(), maxOutputTokens));
            stopSequences(getOrDefault(parameters.stopSequences(), stopSequences));
            toolSpecifications(getOrDefault(parameters.toolSpecifications(), toolSpecifications));
            toolChoice(getOrDefault(parameters.toolChoice(), toolChoice));
            responseFormat(getOrDefault(parameters.responseFormat(), responseFormat));
            return (T) this;
        }

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

        /**
         * @see #stopSequences(String...)
         */
        public T stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return (T) this;
        }

        /**
         * @see #stopSequences(List)
         */
        public T stopSequences(String... stopSequences) {
            return stopSequences(asList(stopSequences));
        }

        /**
         * @see #toolSpecifications(ToolSpecification...)
         */
        public T toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return (T) this;
        }

        /**
         * @see #toolSpecifications(List)
         */
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

        public ChatRequestParameters build() {
            return new DefaultChatRequestParameters(this);
        }
    }
}
