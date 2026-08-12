package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.chat.BaseChatRequest;
import com.ibm.watsonx.ai.chat.NativeChatRequest;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.Thinking;
import com.ibm.watsonx.ai.chat.model.ThinkingEffort;
import com.ibm.watsonx.ai.chat.model.Tool;
import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;
import java.util.Set;

/**
 * Base class for the services that call the watsonx.ai text chat API, either on a foundation model
 * ({@link WatsonxChat}) or on an on-demand deployment ({@link WatsonxDeploymentChat}).
 */
@Internal
abstract class WatsonxTextChatBase<R extends BaseChatRequest> extends WatsonxChatBase<R> {

    protected WatsonxTextChatBase(Builder<?> builder, WatsonxChatRequestParameters defaultRequestParameters) {
        super(builder, defaultRequestParameters);
    }

    /**
     * Fills the parts of the request that are the same for both services.
     *
     * @param requestBuilder the request builder to fill
     * @param messages the messages to send
     * @param tools the tools available to the model, or {@code null}
     * @param parameters the parameters of the current request
     * @return {@code requestBuilder}
     */
    final <B extends NativeChatRequest.Builder<B>> B applyChatRequest(
            B requestBuilder, List<ChatMessage> messages, List<Tool> tools, ChatRequestParameters parameters) {

        requestBuilder
                .messages(messages)
                .tools(tools)
                .parameters(Converter.toChatParameters(parameters, strictJsonSchema));

        if (parameters instanceof WatsonxChatRequestParameters watsonxParameters
                && nonNull(watsonxParameters.thinking())) {
            requestBuilder.thinking(watsonxParameters.thinking());
        }

        return requestBuilder;
    }

    /**
     * Merges into {@code target} the builder values and the default request parameters that both services accept.
     *
     * @param target the parameters builder to fill
     * @param builder the model builder holding the values set by the caller
     */
    protected static void applyTextChatParameters(WatsonxChatRequestParameters.Builder target, Builder<?> builder) {

        var watsonxParameters = builder.defaultRequestParameters instanceof WatsonxChatRequestParameters parameters
                ? parameters
                : WatsonxChatRequestParameters.EMPTY;

        applyCommonParameters(target, builder);

        target.logitBias(getOrDefault(builder.logitBias, watsonxParameters.logitBias()))
                .logprobs(getOrDefault(builder.logprobs, watsonxParameters.logprobs()))
                .topLogprobs(getOrDefault(builder.topLogprobs, watsonxParameters.topLogprobs()))
                .seed(getOrDefault(builder.seed, watsonxParameters.seed()))
                .toolChoiceName(getOrDefault(builder.toolChoiceName, watsonxParameters.toolChoiceName()))
                .timeout(getOrDefault(builder.timeout, watsonxParameters.timeout()))
                .thinking(getOrDefault(builder.thinking, watsonxParameters.thinking()))
                .guidedChoice(getOrDefault(builder.guidedChoice, watsonxParameters.guidedChoice()))
                .guidedGrammar(getOrDefault(builder.guidedGrammar, watsonxParameters.guidedGrammar()))
                .guidedRegex(getOrDefault(builder.guidedRegex, watsonxParameters.guidedRegex()))
                .lengthPenalty(getOrDefault(builder.lengthPenalty, watsonxParameters.lengthPenalty()))
                .repetitionPenalty(getOrDefault(builder.repetitionPenalty, watsonxParameters.repetitionPenalty()));
    }

    @SuppressWarnings("unchecked")
    abstract static class Builder<T extends Builder<T>> extends WatsonxChatBase.Builder<T> {
        protected Thinking thinking;
        protected Set<String> guidedChoice;
        protected String guidedRegex;
        protected String guidedGrammar;
        protected Double repetitionPenalty;
        protected Double lengthPenalty;

        /**
         * Enables or disables thinking.
         *
         * @param enabled {@code true} to enable thinking
         * @return {@code this}
         */
        public T thinking(boolean enabled) {
            return thinking(Thinking.builder().enabled(enabled).build());
        }

        /**
         * Configures thinking with custom extraction tags for parsing the thinking block.
         *
         * @param tags the extraction tags
         * @return {@code this}
         */
        public T thinking(ExtractionTags tags) {
            if (nonNull(tags)) return thinking(Thinking.of(tags));

            this.thinking = null;
            return (T) this;
        }

        /**
         * Configures thinking with a specific effort level.
         *
         * @param thinkingEffort the thinking effort level
         * @return {@code this}
         */
        public T thinking(ThinkingEffort thinkingEffort) {
            if (nonNull(thinkingEffort)) return thinking(Thinking.of(thinkingEffort));

            this.thinking = null;
            return (T) this;
        }

        /**
         * Sets a fully configured {@link Thinking} object for thinking.
         *
         * @param thinking the thinking configuration
         * @return {@code this}
         */
        public T thinking(Thinking thinking) {
            this.thinking = thinking;
            return (T) this;
        }

        /**
         * Constrains the model output to one of the given string choices (guided decoding).
         *
         * @param guidedChoice the allowed output values
         * @return {@code this}
         */
        public T guidedChoice(String... guidedChoice) {
            return guidedChoice(Set.of(guidedChoice));
        }

        /**
         * Constrains the model output to one of the given string choices (guided decoding).
         *
         * @param guidedChoices the set of allowed output values
         * @return {@code this}
         */
        public T guidedChoice(Set<String> guidedChoices) {
            this.guidedChoice = guidedChoices;
            return (T) this;
        }

        /**
         * Constrains the model output to match the given regular expression (guided decoding).
         *
         * @param guidedRegex the regular expression pattern
         * @return {@code this}
         */
        public T guidedRegex(String guidedRegex) {
            this.guidedRegex = guidedRegex;
            return (T) this;
        }

        /**
         * Constrains the model output to conform to the given EBNF grammar (guided decoding).
         *
         * @param guidedGrammar the EBNF grammar string
         * @return {@code this}
         */
        public T guidedGrammar(String guidedGrammar) {
            this.guidedGrammar = guidedGrammar;
            return (T) this;
        }

        /**
         * Sets the repetition penalty. Values greater than {@code 1.0} discourage repetition; values less than {@code 1.0} encourage it.
         *
         * @param repetitionPenalty the repetition penalty
         * @return {@code this}
         */
        public T repetitionPenalty(Double repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return (T) this;
        }

        /**
         * Sets the length penalty applied to the sequence score during beam search. Values greater than {@code 1.0} favor longer sequences.
         *
         * @param lengthPenalty the length penalty
         * @return {@code this}
         */
        public T lengthPenalty(Double lengthPenalty) {
            this.lengthPenalty = lengthPenalty;
            return (T) this;
        }
    }
}
