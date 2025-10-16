package dev.langchain4j.model.watsonx;

import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static java.util.Arrays.asList;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.Thinking;
import com.ibm.watsonx.ai.chat.model.ThinkingEffort;
import com.ibm.watsonx.ai.core.auth.iam.IAMAuthenticator;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Internal
abstract class WatsonxChat {

    protected final ChatService chatService;
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
        var timeLimit = getOrDefault(builder.timeLimit, watsonxParameters.timeLimit());
        var thinking = getOrDefault(builder.thinking, watsonxParameters.thinking());

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
                .timeLimit(timeLimit)
                .thinking(thinking)
                .build();

        var chatServiceBuilder = ChatService.builder();
        if (nonNull(builder.authenticationProvider)) {
            chatServiceBuilder.authenticationProvider(builder.authenticationProvider);
        } else {
            chatServiceBuilder.authenticationProvider(
                    IAMAuthenticator.builder().apiKey(builder.apiKey).build());
        }

        chatService = chatServiceBuilder
                .baseUrl(builder.url)
                .modelId(modelName)
                .version(builder.version)
                .projectId(projectId)
                .spaceId(spaceId)
                .timeout(timeLimit)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .build();
    }

    void validateThinkingIsAllowedForGraniteModel(
            String modelName, List<ChatMessage> messages, List<ToolSpecification> tools) throws LangChain4jException {

        if (!"ibm/granite-3-3-8b-instruct".equals(modelName)) return;

        if (!isNullOrEmpty(tools))
            throw new InvalidRequestException("The thinking/reasoning cannot be activated when tools are used");

        var systemMessageIsPresent = messages.stream().map(ChatMessage::type).anyMatch(SYSTEM::equals);

        if (systemMessageIsPresent)
            throw new InvalidRequestException(
                    "The thinking/reasoning cannot be activated when a system message is present");
    }

    void validate(ChatRequestParameters parameters) {
        if (nonNull(parameters.topK()))
            throw new UnsupportedFeatureException("'topK' parameter is not supported by watsonx.ai");
    }

    @SuppressWarnings("unchecked")
    abstract static class Builder<T extends Builder<T>> extends WatsonxBuilder<T> {
        private String modelName;
        private String projectId;
        private String spaceId;
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
        private Duration timeLimit;
        private List<ToolSpecification> toolSpecifications;
        private List<ChatModelListener> listeners;
        private ChatRequestParameters defaultRequestParameters;
        private Set<Capability> supportedCapabilities;
        private Thinking thinking;

        public T url(CloudRegion cloudRegion) {
            return (T) super.url(cloudRegion.getMlEndpoint());
        }

        public T modelName(String modelName) {
            this.modelName = modelName;
            return (T) this;
        }

        public T projectId(String projectId) {
            this.projectId = projectId;
            return (T) this;
        }

        public T spaceId(String spaceId) {
            this.spaceId = spaceId;
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

        public T toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return (T) this;
        }

        public T responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return (T) this;
        }

        public T logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return (T) this;
        }

        public T logprobs(Boolean logprobs) {
            this.logprobs = logprobs;
            return (T) this;
        }

        public T topLogprobs(Integer topLogprobs) {
            this.topLogprobs = topLogprobs;
            return (T) this;
        }

        public T seed(Integer seed) {
            this.seed = seed;
            return (T) this;
        }

        public T toolChoiceName(String toolChoiceName) {
            this.toolChoiceName = toolChoiceName;
            return (T) this;
        }

        public T timeLimit(Duration timeLimit) {
            this.timeLimit = timeLimit;
            return (T) this;
        }

        public T supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return (T) this;
        }

        public T supportedCapabilities(Capability... supportedCapabilities) {
            return supportedCapabilities(new HashSet<>(asList(supportedCapabilities)));
        }

        public T toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return (T) this;
        }

        public T toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        public T listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return (T) this;
        }

        public T defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return (T) this;
        }

        public T thinking(boolean enabled) {
            if (enabled) {
                return thinking(Thinking.builder().build());
            }
            this.thinking = null;
            return (T) this;
        }

        public T thinking(ExtractionTags tags) {
            return thinking(Thinking.of(tags));
        }

        public T thinking(ThinkingEffort thinkingEffort) {
            return thinking(Thinking.of(thinkingEffort));
        }

        public T thinking(Thinking thinking) {
            this.thinking = thinking;
            return (T) this;
        }
    }
}
