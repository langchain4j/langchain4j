package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.chat.ChatProvider;
import com.ibm.watsonx.ai.chat.ChatRequest;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.TextChatResponse;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.Thinking;
import com.ibm.watsonx.ai.chat.model.ThinkingEffort;
import com.ibm.watsonx.ai.chat.model.Tool;
import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;
import java.util.Set;

@Internal
abstract class WatsonxChat extends WatsonxChatBase<ChatRequest> {

    protected final ChatService chatService;

    protected WatsonxChat(Builder<?> builder) {
        super(builder, mergeParameters(builder));

        var parameters = (WatsonxChatRequestParameters) defaultRequestParameters;

        var serviceBuilder = nonNull(builder.authenticator)
                ? ChatService.builder().authenticator(builder.authenticator)
                : ChatService.builder().apiKey(builder.apiKey);

        chatService = serviceBuilder
                .baseUrl(builder.baseUrl)
                .modelId(parameters.modelName())
                .version(builder.version)
                .projectId(parameters.projectId())
                .spaceId(parameters.spaceId())
                .timeout(parameters.timeout())
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .httpClient(builder.httpClient)
                .verifySsl(builder.verifySsl)
                .build();
    }

    @Override
    protected ChatRequest buildChatRequest(
            List<ChatMessage> messages, List<Tool> tools, ChatRequestParameters parameters) {

        var requestBuilder = ChatRequest.builder()
                .messages(messages)
                .tools(tools)
                .parameters(Converter.toChatParameters(parameters, strictJsonSchema));

        if (parameters instanceof WatsonxChatRequestParameters watsonxParameters
                && nonNull(watsonxParameters.thinking())) {
            requestBuilder.thinking(watsonxParameters.thinking());
        }

        return requestBuilder.build();
    }

    @Override
    protected ChatProvider<ChatRequest, TextChatResponse> chatProvider() {
        return chatService;
    }

    private static WatsonxChatRequestParameters mergeParameters(Builder<?> builder) {

        var watsonxParameters = builder.defaultRequestParameters instanceof WatsonxChatRequestParameters parameters
                ? parameters
                : WatsonxChatRequestParameters.EMPTY;

        var target = WatsonxChatRequestParameters.builder();
        applyCommonParameters(target, builder);

        return target.projectId(getOrDefault(builder.projectId, watsonxParameters.projectId()))
                .spaceId(getOrDefault(builder.spaceId, watsonxParameters.spaceId()))
                .logitBias(getOrDefault(builder.logitBias, watsonxParameters.logitBias()))
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
                .repetitionPenalty(getOrDefault(builder.repetitionPenalty, watsonxParameters.repetitionPenalty()))
                .build();
    }

    @SuppressWarnings("unchecked")
    abstract static class Builder<T extends Builder<T>> extends WatsonxChatBase.Builder<T> {
        protected String projectId;
        protected String spaceId;
        protected Thinking thinking;
        protected Set<String> guidedChoice;
        protected String guidedRegex;
        protected String guidedGrammar;
        protected Double repetitionPenalty;
        protected Double lengthPenalty;

        /**
         * Sets the foundation model id, e.g. {@code "ibm/granite-4-h-small"}.
         *
         * @param modelName the model id
         * @return {@code this}
         */
        public T modelName(String modelName) {
            this.modelName = modelName;
            return (T) this;
        }

        /**
         * Sets the IBM Cloud project ID that owns the watsonx.ai resources. Exactly one of {@code projectId} or {@code spaceId} must be set.
         *
         * @param projectId the IBM Cloud project ID
         * @return {@code this}
         */
        public T projectId(String projectId) {
            this.projectId = projectId;
            return (T) this;
        }

        /**
         * Sets the IBM Cloud deployment space ID. Exactly one of {@code projectId} or {@code spaceId} must be set.
         *
         * @param spaceId the IBM Cloud deployment space ID
         * @return {@code this}
         */
        public T spaceId(String spaceId) {
            this.spaceId = spaceId;
            return (T) this;
        }

        /**
         * Enables or disables extended thinking (chain-of-thought reasoning before the response).
         *
         * @param enabled {@code true} to enable extended thinking
         * @return {@code this}
         */
        public T thinking(boolean enabled) {
            return thinking(Thinking.builder().enabled(enabled).build());
        }

        /**
         * Configures extended thinking with custom extraction tags for parsing the thinking block. Passing {@code null} disables thinking.
         *
         * @param tags the extraction tags, or {@code null} to disable thinking
         * @return {@code this}
         */
        public T thinking(ExtractionTags tags) {
            if (nonNull(tags)) return thinking(Thinking.of(tags));

            this.thinking = null;
            return (T) this;
        }

        /**
         * Configures extended thinking with a specific effort level.
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
         * Sets a fully configured {@link Thinking} object for extended thinking.
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
