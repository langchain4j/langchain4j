package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
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

        MistralAiChatCompletionRequestBuilder() {
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder messages(List<MistralAiChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder safePrompt(Boolean safePrompt) {
            this.safePrompt = safePrompt;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder randomSeed(Integer randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder tools(List<MistralAiTool> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder toolChoice(MistralAiToolChoiceName toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder responseFormat(MistralAiResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public MistralAiChatCompletionRequest build() {
            return new MistralAiChatCompletionRequest(
                    this.model,
                    this.messages,
                    this.temperature,
                    this.topP,
                    this.maxTokens,
                    this.stream,
                    this.safePrompt,
                    this.randomSeed,
                    this.tools,
                    this.toolChoice,
                    this.responseFormat);
        }

        public String toString() {
            return "MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder("
                    + "model=" + this.model
                    + ", messages=" + this.messages == null ? "0" : this.messages.size()
                    + ", temperature=" + this.temperature
                    + ", topP=" + this.topP
                    + ", maxTokens=" + this.maxTokens
                    + ", stream=" + this.stream
                    + ", safePrompt=" + this.safePrompt
                    + ", randomSeed=" + this.randomSeed
                    + ", tools=" + this.tools
                    + ", toolChoice=" + this.toolChoice
                    + ", responseFormat=" + this.responseFormat
                    + ")";
        }
    }

    public static MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder builder() {
        return new MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder();
    }

    public MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder toBuilder() {
        return new MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder()
                .model(this.model)
                .messages(this.messages)
                .temperature(this.temperature)
                .topP(this.topP)
                .maxTokens(this.maxTokens)
                .stream(this.stream)
                .safePrompt(this.safePrompt)
                .randomSeed(this.randomSeed)
                .tools(this.tools)
                .toolChoice(this.toolChoice)
                .responseFormat(this.responseFormat);
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

    public void setModel(String model) {
        this.model = model;
    }

    public void setMessages(List<MistralAiChatMessage> messages) {
        this.messages = messages;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public void setSafePrompt(Boolean safePrompt) {
        this.safePrompt = safePrompt;
    }

    public void setRandomSeed(Integer randomSeed) {
        this.randomSeed = randomSeed;
    }

    public void setTools(List<MistralAiTool> tools) {
        this.tools = tools;
    }

    public void setToolChoice(MistralAiToolChoiceName toolChoice) {
        this.toolChoice = toolChoice;
    }

    public void setResponseFormat(MistralAiResponseFormat responseFormat) {
        this.responseFormat = responseFormat;
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
                && Objects.equals(this.responseFormat, other.responseFormat);
    }

    public String toString() {
        return "MistralAiChatCompletionRequest("
                + "model=" + this.getModel()
                + ", messages=" + this.getMessages() == null ? "0" : this.getMessages().size()
                + ", temperature=" + this.getTemperature()
                + ", topP=" + this.getTopP()
                + ", maxTokens=" + this.getMaxTokens()
                + ", stream=" + this.getStream()
                + ", safePrompt=" + this.getSafePrompt()
                + ", randomSeed=" + this.getRandomSeed()
                + ", tools=" + this.getTools()
                + ", toolChoice=" + this.getToolChoice()
                + ", responseFormat=" + this.getResponseFormat()
                + ")";
    }

    public MistralAiChatCompletionRequest() {
    }

    public MistralAiChatCompletionRequest(String model,
            final List<MistralAiChatMessage> messages,
            final Double temperature,
            final Double topP,
            final Integer maxTokens,
            final Boolean stream,
            final Boolean safePrompt,
            final Integer randomSeed,
            final List<MistralAiTool> tools,
            final MistralAiToolChoiceName toolChoice,
            final MistralAiResponseFormat responseFormat) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.stream = stream;
        this.safePrompt = safePrompt;
        this.randomSeed = randomSeed;
        this.tools = tools;
        this.toolChoice = toolChoice;
        this.responseFormat = responseFormat;
    }
}
