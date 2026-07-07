package dev.langchain4j.model.chat.request;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Arrays.asList;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.List;
import java.util.Objects;

public class ChatRequest {

    private final List<ChatMessage> messages;
    private final ChatRequestParameters parameters;

    protected ChatRequest(Builder builder) {
        this.messages = copy(ensureNotEmpty(builder.messages, "messages"));

        boolean individualParametersAreSpecified = builder.modelName != null
                || builder.temperature != null
                || builder.topP != null
                || builder.topK != null
                || builder.frequencyPenalty != null
                || builder.presencePenalty != null
                || builder.maxOutputTokens != null
                || !isNullOrEmpty(builder.stopSequences)
                || !isNullOrEmpty(builder.toolSpecifications)
                || builder.toolChoice != null
                || builder.responseFormat != null;

        if (!individualParametersAreSpecified) {
            this.parameters = builder.parameters != null ? builder.parameters : DefaultChatRequestParameters.EMPTY;
            return;
        }

        ChatRequestParameters overrides = ChatRequestParameters.builder()
                .modelName(builder.modelName)
                .temperature(builder.temperature)
                .topP(builder.topP)
                .topK(builder.topK)
                .frequencyPenalty(builder.frequencyPenalty)
                .presencePenalty(builder.presencePenalty)
                .maxOutputTokens(builder.maxOutputTokens)
                .stopSequences(builder.stopSequences)
                .toolSpecifications(builder.toolSpecifications)
                .toolChoice(builder.toolChoice)
                .responseFormat(builder.responseFormat)
                .build();

        this.parameters = builder.parameters != null ? builder.parameters.overrideWith(overrides) : overrides;
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    public ChatRequestParameters parameters() {
        return parameters;
    }

    public String modelName() {
        return parameters.modelName();
    }

    public Double temperature() {
        return parameters.temperature();
    }

    public Double topP() {
        return parameters.topP();
    }

    public Integer topK() {
        return parameters.topK();
    }

    public Double frequencyPenalty() {
        return parameters.frequencyPenalty();
    }

    public Double presencePenalty() {
        return parameters.presencePenalty();
    }

    public Integer maxOutputTokens() {
        return parameters.maxOutputTokens();
    }

    public List<String> stopSequences() {
        return parameters.stopSequences();
    }

    public List<ToolSpecification> toolSpecifications() {
        return parameters.toolSpecifications();
    }

    public ToolChoice toolChoice() {
        return parameters.toolChoice();
    }

    public ResponseFormat responseFormat() {
        return parameters.responseFormat();
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatRequest that = (ChatRequest) o;
        return Objects.equals(this.messages, that.messages) && Objects.equals(this.parameters, that.parameters);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(messages, parameters);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "ChatRequest {" + " messages = " + messages + ", parameters = " + parameters + " }";
    }

    /**
     * Transforms this instance to a {@link Builder} with all of the same field values.
     * <p>
     * Individual setters on the returned {@link Builder} (e.g., {@link Builder#modelName(String)},
     * {@link Builder#temperature(Double)}, etc.) can be used to override specific fields of the existing
     * {@link #parameters()} without rebuilding them from scratch. For example:
     * <pre>{@code
     * ChatRequest modified = chatRequest.toBuilder()
     *         .temperature(0.0)
     *         .build();
     * }</pre>
     * See {@link Builder} for details on the override semantics.
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ChatRequest}.
     * <p>
     * <b>Override semantics</b>: when {@link #parameters(ChatRequestParameters)} is set together with
     * one or more individual setters (e.g., {@link #modelName(String)},
     * {@link #temperature(Double)}, etc.), the values from the individual
     * setters take precedence over the corresponding fields of {@code parameters}, while all other
     * fields of {@code parameters} (including provider-specific fields on subclasses of
     * {@link ChatRequestParameters}) are preserved. Merging is performed via
     * {@link ChatRequestParameters#overrideWith(ChatRequestParameters)}, which only overrides with
     * non-null (and, for collections, non-empty) values — setting a field back to {@code null} via
     * an individual setter will <em>not</em> clear an existing value on {@code parameters}.
     * <p>
     * This makes it easy to modify a single field of an existing {@link ChatRequest} via
     * {@link ChatRequest#toBuilder()} without losing any other configuration.
     */
    public static class Builder {

        private List<ChatMessage> messages;
        private ChatRequestParameters parameters;

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

        public Builder() {}

        public Builder(ChatRequest chatRequest) {
            this.messages = chatRequest.messages;
            this.parameters = chatRequest.parameters;
        }

        /**
         * Sets the list of chat messages for this request.
         *
         * @param messages the chat messages
         * @return {@code this}
         */
        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        /**
         * Sets the chat messages for this request.
         *
         * @param messages the chat messages (varargs)
         * @return {@code this}
         */
        public Builder messages(ChatMessage... messages) {
            return messages(asList(messages));
        }

        /**
         * Sets the {@link ChatRequestParameters} to be used as the base for this request.
         * <p>
         * Any individual setters called on this {@link Builder} (e.g., {@link #modelName(String)},
         * {@link #temperature(Double)}, etc.) will override the corresponding fields of the supplied
         * {@code parameters}; all other fields of {@code parameters} are preserved.
         * See {@link Builder} for full override semantics.
         */
        public Builder parameters(ChatRequestParameters parameters) {
            this.parameters = parameters;
            return this;
        }

        /**
         * Sets the model name for this request, overriding the corresponding field in
         * {@link #parameters(ChatRequestParameters)}.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the sampling temperature, overriding the corresponding field in
         * {@link #parameters(ChatRequestParameters)}.
         *
         * @param temperature the sampling temperature
         * @return {@code this}
         */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the nucleus sampling probability (top-p), overriding the corresponding field in
         * {@link #parameters(ChatRequestParameters)}.
         *
         * @param topP the nucleus sampling probability
         * @return {@code this}
         */
        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * Sets the top-K sampling limit, overriding the corresponding field in
         * {@link #parameters(ChatRequestParameters)}.
         *
         * @param topK the top-K value
         * @return {@code this}
         */
        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        /**
         * Sets the frequency penalty, overriding the corresponding field in
         * {@link #parameters(ChatRequestParameters)}.
         *
         * @param frequencyPenalty the frequency penalty
         * @return {@code this}
         */
        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        /**
         * Sets the presence penalty, overriding the corresponding field in
         * {@link #parameters(ChatRequestParameters)}.
         *
         * @param presencePenalty the presence penalty
         * @return {@code this}
         */
        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        /**
         * Sets the maximum number of output tokens, overriding the corresponding field in
         * {@link #parameters(ChatRequestParameters)}.
         *
         * @param maxOutputTokens the maximum number of tokens to generate
         * @return {@code this}
         */
        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        /**
         * Sets the stop sequences, overriding the corresponding field in
         * {@link #parameters(ChatRequestParameters)}.
         *
         * @param stopSequences the sequences that stop generation when encountered
         * @return {@code this}
         */
        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        /**
         * Sets the tool specifications available to the model, overriding the corresponding field in
         * {@link #parameters(ChatRequestParameters)}.
         *
         * @param toolSpecifications the list of tool specifications
         * @return {@code this}
         */
        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        /**
         * Sets the tool specifications available to the model, overriding the corresponding field in
         * {@link #parameters(ChatRequestParameters)}.
         *
         * @param toolSpecifications the tool specifications (varargs)
         * @return {@code this}
         */
        public Builder toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        /**
         * Sets the tool choice strategy, overriding the corresponding field in
         * {@link #parameters(ChatRequestParameters)}.
         *
         * @param toolChoice the tool choice
         * @return {@code this}
         */
        public Builder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        /**
         * Sets the response format for structured output, overriding the corresponding field in
         * {@link #parameters(ChatRequestParameters)}.
         *
         * @param responseFormat the response format
         * @return {@code this}
         */
        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        /**
         * Builds the {@link ChatRequest}.
         *
         * @return the configured {@link ChatRequest}
         */
        public ChatRequest build() {
            return new ChatRequest(this);
        }
    }
}
