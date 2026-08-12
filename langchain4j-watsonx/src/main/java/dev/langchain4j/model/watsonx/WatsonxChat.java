package dev.langchain4j.model.watsonx;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Arrays.asList;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.chat.ChatProvider;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.Thinking;
import com.ibm.watsonx.ai.chat.model.ThinkingEffort;
import com.ibm.watsonx.ai.deployment.DeploymentService;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Internal
abstract class WatsonxChat {

    protected final ChatProvider chatProvider;
    protected final List<ChatModelListener> listeners;
    protected final ChatRequestParameters defaultRequestParameters;
    protected final Set<Capability> supportedCapabilities;

    protected WatsonxChat(Builder<?> builder) {
        listeners = copy(builder.listeners);
        supportedCapabilities = copy(builder.supportedCapabilities);

        ChatRequestParameters commonParameters;
        if (builder.defaultRequestParameters != null) {
            validate(builder.defaultRequestParameters);
            commonParameters = builder.defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.EMPTY;
        }

        WatsonxChatRequestParameters watsonxParameters =
                builder.defaultRequestParameters instanceof WatsonxChatRequestParameters watsonxChatRequestParameters
                        ? watsonxChatRequestParameters
                        : WatsonxChatRequestParameters.EMPTY;

        var modelName = getOrDefault(builder.modelName, commonParameters.modelName());
        var projectId = getOrDefault(builder.projectId, watsonxParameters.projectId());
        var spaceId = getOrDefault(builder.spaceId, watsonxParameters.spaceId());
        var timeout = getOrDefault(builder.timeout, watsonxParameters.timeout());
        var thinking = getOrDefault(builder.thinking, watsonxParameters.thinking());
        var deploymentId = getOrDefault(builder.deploymentId, watsonxParameters.deploymentId());

        defaultRequestParameters = WatsonxChatRequestParameters.builder()
                // Common parameters
                .modelName(modelName)
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .frequencyPenalty(getOrDefault(builder.frequencyPenalty, commonParameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(builder.presencePenalty, commonParameters.presencePenalty()))
                .maxOutputTokens(getOrDefault(builder.maxOutputTokens, commonParameters.maxOutputTokens()))
                .stopSequences(getOrDefault(builder.stopSequences, commonParameters.stopSequences()))
                .toolSpecifications(getOrDefault(builder.toolSpecifications, commonParameters.toolSpecifications()))
                .toolChoice(getOrDefault(builder.toolChoice, commonParameters.toolChoice()))
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                // Watsonx parameters
                .projectId(projectId)
                .spaceId(spaceId)
                .logitBias(getOrDefault(builder.logitBias, watsonxParameters.logitBias()))
                .logprobs(getOrDefault(builder.logprobs, watsonxParameters.logprobs()))
                .topLogprobs(getOrDefault(builder.topLogprobs, watsonxParameters.topLogprobs()))
                .seed(getOrDefault(builder.seed, watsonxParameters.seed()))
                .toolChoiceName(getOrDefault(builder.toolChoiceName, watsonxParameters.toolChoiceName()))
                .timeout(timeout)
                .thinking(thinking)
                .guidedChoice(getOrDefault(builder.guidedChoice, watsonxParameters.guidedChoice()))
                .guidedGrammar(getOrDefault(builder.guidedGrammar, watsonxParameters.guidedGrammar()))
                .guidedRegex(getOrDefault(builder.guidedRegex, watsonxParameters.guidedRegex()))
                .lengthPenalty(getOrDefault(builder.lengthPenalty, watsonxParameters.lengthPenalty()))
                .repetitionPenalty(getOrDefault(builder.repetitionPenalty, watsonxParameters.repetitionPenalty()))
                .deploymentId(deploymentId)
                .build();

        if (nonNull(deploymentId)) {

            var deploymentBuilder = nonNull(builder.authenticator)
                    ? DeploymentService.builder().authenticator(builder.authenticator)
                    : DeploymentService.builder().apiKey(builder.apiKey);

            chatProvider = deploymentBuilder
                    .baseUrl(builder.baseUrl)
                    .version(builder.version)
                    .timeout(timeout)
                    .logRequests(builder.logRequests)
                    .logResponses(builder.logResponses)
                    .httpClient(builder.httpClient)
                    .verifySsl(builder.verifySsl)
                    .build();

        } else {

            var chatServiceBuilder = nonNull(builder.authenticator)
                    ? ChatService.builder().authenticator(builder.authenticator)
                    : ChatService.builder().apiKey(builder.apiKey);

            chatProvider = chatServiceBuilder
                    .baseUrl(builder.baseUrl)
                    .modelId(modelName)
                    .version(builder.version)
                    .projectId(projectId)
                    .spaceId(spaceId)
                    .timeout(timeout)
                    .logRequests(builder.logRequests)
                    .logResponses(builder.logResponses)
                    .httpClient(builder.httpClient)
                    .verifySsl(builder.verifySsl)
                    .build();
        }
    }

    final void validate(ChatRequestParameters parameters) {
        if (nonNull(parameters.topK()))
            throw new UnsupportedFeatureException("'topK' parameter is not supported by watsonx.ai");
    }

    @SuppressWarnings("unchecked")
    abstract static class Builder<T extends Builder<T>> extends WatsonxBuilder<T> {
        private String modelName;
        private Double temperature;
        private Double topP;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer maxOutputTokens;
        private List<String> stopSequences;
        private ToolChoice toolChoice;
        private ResponseFormat responseFormat;
        private Map<String, Integer> logitBias;
        private Boolean logprobs;
        private Integer topLogprobs;
        private Integer seed;
        private String toolChoiceName;
        private List<ToolSpecification> toolSpecifications;
        private List<ChatModelListener> listeners;
        private ChatRequestParameters defaultRequestParameters;
        private Set<Capability> supportedCapabilities;
        private Set<String> guidedChoice;
        private String guidedRegex;
        private String guidedGrammar;
        private Double repetitionPenalty;
        private Double lengthPenalty;
        private String deploymentId;
        private Thinking thinking;

        /**
         * Sets the watsonx.ai model ID, e.g. {@code "ibm/granite-3-8b-instruct"}.
         *
         * @param modelName the model ID
         * @return {@code this}
         */
        public T modelName(String modelName) {
            this.modelName = modelName;
            return (T) this;
        }

        /**
         * Sets the sampling temperature in the range {@code [0.0, 2.0]}.
         * Higher values produce more random output; lower values are more deterministic.
         *
         * @param temperature the sampling temperature
         * @return {@code this}
         */
        public T temperature(Double temperature) {
            this.temperature = temperature;
            return (T) this;
        }

        /**
         * Sets the nucleus sampling probability in the range {@code (0.0, 1.0]}.
         * The model considers only the tokens whose cumulative probability reaches this threshold.
         *
         * @param topP the nucleus sampling threshold
         * @return {@code this}
         */
        public T topP(Double topP) {
            this.topP = topP;
            return (T) this;
        }

        /**
         * Sets the frequency penalty in the range {@code [-2.0, 2.0]}.
         * Positive values reduce the likelihood of repeating tokens proportional to how often they have appeared.
         *
         * @param frequencyPenalty the frequency penalty
         * @return {@code this}
         */
        public T frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return (T) this;
        }

        /**
         * Sets the presence penalty in the range {@code [-2.0, 2.0]}.
         * Positive values reduce the likelihood of repeating any token that has already appeared in the output.
         *
         * @param presencePenalty the presence penalty
         * @return {@code this}
         */
        public T presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return (T) this;
        }

        /**
         * Sets the maximum number of tokens to generate in the response.
         *
         * @param maxOutputTokens the maximum number of output tokens
         * @return {@code this}
         */
        public T maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return (T) this;
        }

        /**
         * Sets the sequences that will stop generation when encountered.
         *
         * @param stopSequences the stop sequences
         * @return {@code this}
         */
        public T stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return (T) this;
        }

        /**
         * Sets the sequences that will stop generation when encountered.
         *
         * @param stopSequences the stop sequences
         * @return {@code this}
         */
        public T stopSequences(String... stopSequences) {
            return stopSequences(asList(stopSequences));
        }

        /**
         * Sets how the model selects tools. Controls whether tool use is automatic, forced, or disabled.
         *
         * @param toolChoice the tool choice mode
         * @return {@code this}
         */
        public T toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return (T) this;
        }

        /**
         * Sets the response format to control structured output, e.g. JSON mode.
         *
         * @param responseFormat the response format
         * @return {@code this}
         */
        public T responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return (T) this;
        }

        /**
         * Sets per-token logit biases to increase or decrease the likelihood of specific tokens.
         * Keys are token IDs; values are bias offsets in the range {@code [-100, 100]}.
         *
         * @param logitBias the logit bias map
         * @return {@code this}
         */
        public T logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return (T) this;
        }

        /**
         * Enables returning log probabilities of the output tokens.
         *
         * @param logprobs {@code true} to include log probabilities in the response
         * @return {@code this}
         */
        public T logprobs(Boolean logprobs) {
            this.logprobs = logprobs;
            return (T) this;
        }

        /**
         * Sets the number of most likely tokens to return log probabilities for at each position.
         * Requires {@link #logprobs} to be {@code true}. Value must be between 0 and 20.
         *
         * @param topLogprobs the number of top log probabilities to return
         * @return {@code this}
         */
        public T topLogprobs(Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return (T) this;
        }

        /**
         * Sets the random seed for deterministic sampling. Using the same seed and parameters
         * should produce the same output across calls.
         *
         * @param seed the random seed
         * @return {@code this}
         */
        public T seed(Integer seed) {
            this.seed = seed;
            return (T) this;
        }

        /**
         * Sets the name of the specific tool to force when {@link #toolChoice} is set to force a particular tool.
         *
         * @param toolChoiceName the tool name to force
         * @return {@code this}
         */
        public T toolChoiceName(String toolChoiceName) {
            this.toolChoiceName = toolChoiceName;
            return (T) this;
        }

        /**
         * Declares the capabilities supported by this model instance, e.g. vision or token-level streaming.
         *
         * @param supportedCapabilities the set of supported capabilities
         * @return {@code this}
         */
        public T supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return (T) this;
        }

        /**
         * Declares the capabilities supported by this model instance.
         *
         * @param supportedCapabilities the supported capabilities
         * @return {@code this}
         */
        public T supportedCapabilities(Capability... supportedCapabilities) {
            return supportedCapabilities(new HashSet<>(asList(supportedCapabilities)));
        }

        /**
         * Sets the tool definitions available to the model for function calling.
         *
         * @param toolSpecifications the list of tool specifications
         * @return {@code this}
         */
        public T toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return (T) this;
        }

        /**
         * Sets the tool definitions available to the model for function calling.
         *
         * @param toolSpecifications the tool specifications
         * @return {@code this}
         */
        public T toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        /**
         * Sets the list of {@link ChatModelListener} instances for observing chat model interactions.
         *
         * @param listeners the listeners to register
         * @return {@code this}
         */
        public T listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return (T) this;
        }

        /**
         * Sets default request parameters that are merged into every chat request.
         *
         * @param defaultRequestParameters the default request parameters
         * @return {@code this}
         */
        public T defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return (T) this;
        }

        public T deploymentId(String deploymentId) {
            this.deploymentId = deploymentId;
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
         * Configures extended thinking with custom extraction tags for parsing the thinking block.
         * Passing {@code null} disables thinking.
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
         * Passing {@code null} disables thinking.
         *
         * @param thinkingEffort the thinking effort level, or {@code null} to disable thinking
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
         * Sets the repetition penalty. Values greater than {@code 1.0} discourage repetition;
         * values less than {@code 1.0} encourage it.
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
         * Values greater than {@code 1.0} favor longer sequences.
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
