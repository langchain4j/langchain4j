package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.chat.ChatProvider;
import com.ibm.watsonx.ai.chat.TextChatResponse;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.Thinking;
import com.ibm.watsonx.ai.chat.model.ThinkingEffort;
import com.ibm.watsonx.ai.chat.model.Tool;
import com.ibm.watsonx.ai.deployment.DeploymentChatRequest;
import com.ibm.watsonx.ai.deployment.DeploymentService;
import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;
import java.util.Set;

@Internal
abstract class WatsonxDeploymentChat extends WatsonxChatBase<DeploymentChatRequest> {

    protected final DeploymentService deploymentService;
    protected final String deploymentId;

    protected WatsonxDeploymentChat(Builder<?> builder) {
        super(builder, mergeParameters(builder));

        this.deploymentId = builder.deploymentId;

        var parameters = (WatsonxChatRequestParameters) defaultRequestParameters;

        var serviceBuilder = nonNull(builder.authenticator)
                ? DeploymentService.builder().authenticator(builder.authenticator)
                : DeploymentService.builder().apiKey(builder.apiKey);

        deploymentService = serviceBuilder
                .baseUrl(builder.baseUrl)
                .version(builder.version)
                .timeout(parameters.timeout())
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .httpClient(builder.httpClient)
                .verifySsl(builder.verifySsl)
                .build();
    }

    @Override
    protected DeploymentChatRequest buildChatRequest(
            List<ChatMessage> messages, List<Tool> tools, ChatRequestParameters parameters) {

        var requestBuilder = DeploymentChatRequest.builder()
                .deploymentId(deploymentId)
                .messages(messages)
                .tools(tools)
                .parameters(Converter.toChatParameters(parameters));

        if (parameters instanceof WatsonxChatRequestParameters watsonxParameters
                && nonNull(watsonxParameters.thinking())) {
            requestBuilder.thinking(watsonxParameters.thinking());
        }

        return requestBuilder.build();
    }

    @Override
    protected ChatProvider<DeploymentChatRequest, TextChatResponse> chatProvider() {
        return deploymentService;
    }

    private static WatsonxChatRequestParameters mergeParameters(Builder<?> builder) {

        var watsonxParameters = builder.defaultRequestParameters instanceof WatsonxChatRequestParameters parameters
                ? parameters
                : WatsonxChatRequestParameters.EMPTY;

        var target = WatsonxChatRequestParameters.builder();
        applyCommonParameters(target, builder);

        return target.logitBias(getOrDefault(builder.logitBias, watsonxParameters.logitBias()))
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
        protected String deploymentId;
        protected Thinking thinking;
        protected Set<String> guidedChoice;
        protected String guidedRegex;
        protected String guidedGrammar;
        protected Double repetitionPenalty;
        protected Double lengthPenalty;

        /**
         * Sets the id of the on-demand model to call.
         *
         * @param deploymentId the deployment id
         * @return {@code this}
         */
        public T deploymentId(String deploymentId) {
            this.deploymentId = deploymentId;
            return (T) this;
        }

        /**
         * Enables or disables thinking.
         *
         * @param enabled {@code true} to enable extended thinking
         * @return {@code this}
         */
        public T thinking(boolean enabled) {
            return thinking(Thinking.builder().enabled(enabled).build());
        }

        /**
         * Configures extended thinking with custom extraction tags for parsing the thinking block.
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
         * Constrains the model output to one of the given string choices.
         *
         * @param guidedChoice the allowed output values
         * @return {@code this}
         */
        public T guidedChoice(String... guidedChoice) {
            return guidedChoice(Set.of(guidedChoice));
        }

        /**
         * Constrains the model output to one of the given string choices.
         *
         * @param guidedChoices the set of allowed output values
         * @return {@code this}
         */
        public T guidedChoice(Set<String> guidedChoices) {
            this.guidedChoice = guidedChoices;
            return (T) this;
        }

        /**
         * Constrains the model output to match the given regular expression.
         *
         * @param guidedRegex the regular expression pattern
         * @return {@code this}
         */
        public T guidedRegex(String guidedRegex) {
            this.guidedRegex = guidedRegex;
            return (T) this;
        }

        /**
         * Constrains the model output to conform to the given EBNF grammar.
         *
         * @param guidedGrammar the EBNF grammar string
         * @return {@code this}
         */
        public T guidedGrammar(String guidedGrammar) {
            this.guidedGrammar = guidedGrammar;
            return (T) this;
        }

        /**
         * Sets the repetition penalty.
         *
         * @param repetitionPenalty the repetition penalty
         * @return {@code this}
         */
        public T repetitionPenalty(Double repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return (T) this;
        }

        /**
         * Sets the length penalty applied to the sequence score during beam search.
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
