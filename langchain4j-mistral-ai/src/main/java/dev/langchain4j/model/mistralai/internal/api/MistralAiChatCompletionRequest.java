package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
@JsonDeserialize(builder = MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder.class)
public class MistralAiChatCompletionRequest {
    private String model;
    private List<MistralAiChatMessage> messages;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private Boolean stream;
    private Boolean safePrompt;
    private Integer randomSeed;
    private List<MistralAiTool> tools;
    private MistralAiToolChoiceName toolChoice;
    private MistralAiResponseFormat responseFormat;
    private String[] stop;
    private Double frequencyPenalty;
    private Double presencePenalty;

    private MistralAiChatCompletionRequest(MistralAiChatCompletionRequestBuilder builder) {
        this.model = builder.model;
        this.messages = builder.messages;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.maxTokens = builder.maxTokens;
        this.stream = builder.stream;
        this.safePrompt = builder.safePrompt;
        this.randomSeed = builder.randomSeed;
        this.tools = builder.tools;
        this.toolChoice = builder.toolChoice;
        this.responseFormat = builder.responseFormat;
        this.stop = builder.stop;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.presencePenalty = builder.presencePenalty;
    }

    public String getModel() {
        return this.model;
    }

    public List<MistralAiChatMessage> getMessages() {
        return this.messages;
    }

    public Double getTemperature() {
        return this.temperature;
    }

    public Double getTopP() {
        return this.topP;
    }

    public Integer getMaxTokens() {
        return this.maxTokens;
    }

    public Boolean getStream() {
        return this.stream;
    }

    public Boolean getSafePrompt() {
        return this.safePrompt;
    }

    public Integer getRandomSeed() {
        return this.randomSeed;
    }

    public List<MistralAiTool> getTools() {
        return this.tools;
    }

    public MistralAiToolChoiceName getToolChoice() {
        return this.toolChoice;
    }

    public MistralAiResponseFormat getResponseFormat() {
        return this.responseFormat;
    }

    public String[] getStop() {
        return stop;
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.model);
        hash = 83 * hash + Objects.hashCode(this.messages);
        hash = 83 * hash + Objects.hashCode(this.temperature);
        hash = 83 * hash + Objects.hashCode(this.topP);
        hash = 83 * hash + Objects.hashCode(this.maxTokens);
        hash = 83 * hash + Objects.hashCode(this.stream);
        hash = 83 * hash + Objects.hashCode(this.safePrompt);
        hash = 83 * hash + Objects.hashCode(this.randomSeed);
        hash = 83 * hash + Objects.hashCode(this.tools);
        hash = 83 * hash + Objects.hashCode(this.toolChoice);
        hash = 83 * hash + Objects.hashCode(this.responseFormat);
        hash = 83 * hash + Objects.hashCode(this.stop);
        hash = 83 * hash + Objects.hashCode(this.frequencyPenalty);
        hash = 83 * hash + Objects.hashCode(this.presencePenalty);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiChatCompletionRequest other = (MistralAiChatCompletionRequest) obj;
        return Objects.equals(this.model, other.model)
                && Objects.equals(this.messages, other.messages)
                && Objects.equals(this.temperature, other.temperature)
                && Objects.equals(this.topP, other.topP)
                && Objects.equals(this.maxTokens, other.maxTokens)
                && Objects.equals(this.stream, other.stream)
                && Objects.equals(this.safePrompt, other.safePrompt)
                && Objects.equals(this.randomSeed, other.randomSeed)
                && Objects.equals(this.tools, other.tools)
                && this.toolChoice == other.toolChoice
                && Objects.equals(this.responseFormat, other.responseFormat)
                && Arrays.equals(this.stop, other.stop)
                && Objects.equals(this.frequencyPenalty, other.frequencyPenalty)
                && Objects.equals(this.presencePenalty, other.presencePenalty);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralAiChatCompletionRequest [", "]")
                .add("model=" + this.getModel())
                .add("messages="
                        + (this.getMessages() == null ? 0 : this.getMessages().size()))
                .add("temperature=" + this.getTemperature())
                .add("topP=" + this.getTopP())
                .add("maxTokens=" + this.getMaxTokens())
                .add("stream=" + this.getStream())
                .add("safePrompt=" + this.getSafePrompt())
                .add("randomSeed=" + this.getRandomSeed())
                .add("tools=" + this.getTools())
                .add("toolChoice=" + this.getToolChoice())
                .add("responseFormat=" + this.getResponseFormat())
                .toString();
    }

    public static MistralAiChatCompletionRequestBuilder builder() {
        return new MistralAiChatCompletionRequestBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiChatCompletionRequestBuilder {
        private String model;
        private List<MistralAiChatMessage> messages;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private Boolean stream;
        private Boolean safePrompt;
        private Integer randomSeed;
        private List<MistralAiTool> tools;
        private MistralAiToolChoiceName toolChoice;
        private MistralAiResponseFormat responseFormat;
        private String[] stop;
        private Double frequencyPenalty;
        private Double presencePenalty;

        private MistralAiChatCompletionRequestBuilder() {}

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequestBuilder messages(List<MistralAiChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequestBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequestBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequestBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequestBuilder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequestBuilder safePrompt(Boolean safePrompt) {
            this.safePrompt = safePrompt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequestBuilder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequestBuilder tools(List<MistralAiTool> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequestBuilder toolChoice(MistralAiToolChoiceName toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequestBuilder responseFormat(MistralAiResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public MistralAiChatCompletionRequestBuilder stop(String ... stop) {
            this.stop = stop;
            return this;
        }

        public MistralAiChatCompletionRequestBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public MistralAiChatCompletionRequestBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public MistralAiChatCompletionRequest build() {
            return new MistralAiChatCompletionRequest(this);
        }
    }
}
