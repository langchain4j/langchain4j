package dev.langchain4j.model.bedrock.internal;

import static java.util.stream.Collectors.joining;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;

public abstract class AbstractSharedBedrockChatModel {

    private static final Logger log = LoggerFactory.getLogger(AbstractSharedBedrockChatModel.class);

    // Claude requires you to enclose the prompt as follows:
    // String enclosedPrompt = "Human: " + prompt + "\n\nAssistant:";
    protected static final String HUMAN_PROMPT = "Human:";
    protected static final String ASSISTANT_PROMPT = "Assistant:";
    protected static final String DEFAULT_ANTHROPIC_VERSION = "bedrock-2023-05-31";
    protected static final Integer DEFAULT_MAX_RETRIES = 2;
    protected static final Region DEFAULT_REGION = Region.US_EAST_1;
    protected static final AwsCredentialsProvider DEFAULT_CREDENTIALS_PROVIDER =
            DefaultCredentialsProvider.builder().build();
    protected static final int DEFAULT_MAX_TOKENS = 300;
    protected static final double DEFAULT_TEMPERATURE = 1.0;
    protected static final float DEFAULT_TOP_P = 0.999f;
    protected static final String[] DEFAULT_STOP_SEQUENCES = new String[] {};
    protected static final int DEFAULT_TOP_K = 250;
    protected static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1L);
    protected static final List<ChatModelListener> DEFAULT_LISTENERS = Collections.emptyList();

    protected final String humanPrompt;
    protected final String assistantPrompt;
    protected final Integer maxRetries;
    protected final Region region;
    protected final AwsCredentialsProvider credentialsProvider;
    protected final int maxTokens;
    protected final double temperature;
    protected final float topP;
    protected final String[] stopSequences;
    protected final int topK;
    protected final Duration timeout;
    protected final String anthropicVersion;
    protected final List<ChatModelListener> listeners;

    protected AbstractSharedBedrockChatModel(AbstractSharedBedrockChatModelBuilder<?, ?> builder) {
        if (builder.isHumanPromptSet) {
            this.humanPrompt = builder.humanPrompt;
        } else {
            this.humanPrompt = HUMAN_PROMPT;
        }

        if (builder.isAssistantPromptSet) {
            this.assistantPrompt = builder.assistantPrompt;
        } else {
            this.assistantPrompt = ASSISTANT_PROMPT;
        }

        if (builder.isMaxRetriesSet) {
            this.maxRetries = builder.maxRetries;
        } else {
            this.maxRetries = DEFAULT_MAX_RETRIES;
        }

        if (builder.isRegionSet) {
            this.region = builder.region;
        } else {
            this.region = DEFAULT_REGION;
        }

        if (builder.isCredentialsProviderSet) {
            this.credentialsProvider = builder.credentialsProvider;
        } else {
            this.credentialsProvider = DEFAULT_CREDENTIALS_PROVIDER;
        }

        if (builder.isMaxTokensSet) {
            this.maxTokens = builder.maxTokens;
        } else {
            this.maxTokens = DEFAULT_MAX_TOKENS;
        }

        if (builder.isTemperatureSet) {
            this.temperature = builder.temperature;
        } else {
            this.temperature = DEFAULT_TEMPERATURE;
        }

        if (builder.isTopPSet) {
            this.topP = builder.topP;
        } else {
            this.topP = DEFAULT_TOP_P;
        }

        if (builder.isStopSequencesSet) {
            this.stopSequences = builder.stopSequences;
        } else {
            this.stopSequences = DEFAULT_STOP_SEQUENCES;
        }

        if (builder.isTopKSet) {
            this.topK = builder.topK;
        } else {
            this.topK = DEFAULT_TOP_K;
        }

        if (builder.isTimeoutSet) {
            this.timeout = builder.timeout;
        } else {
            this.timeout = DEFAULT_TIMEOUT;
        }

        if (builder.isAnthropicVersionSet) {
            this.anthropicVersion = builder.anthropicVersion;
        } else {
            this.anthropicVersion = DEFAULT_ANTHROPIC_VERSION;
        }

        if (builder.isListenersSet) {
            this.listeners = builder.listeners;
        } else {
            this.listeners = DEFAULT_LISTENERS;
        }
    }

    /**
     * Convert chat message to string
     *
     * @param message chat message
     * @return string
     */
    protected String chatMessageToString(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        } else if (message instanceof UserMessage userMessage) {
            return humanPrompt + " " + userMessage.singleText();
        } else if (message instanceof AiMessage aiMessage) {
            return assistantPrompt + " " + aiMessage.text();
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + message.type());
        }
    }

    protected String convertMessagesToAwsBody(List<ChatMessage> messages) {
        final String context = messages.stream()
                .filter(message -> message.type() == ChatMessageType.SYSTEM)
                .map(message -> ((SystemMessage) message).text())
                .collect(joining("\n"));

        final String userMessages = messages.stream()
                .filter(message -> message.type() != ChatMessageType.SYSTEM)
                .map(this::chatMessageToString)
                .collect(joining("\n"));

        final String prompt = String.format("%s\n\n%s\n%s", context, userMessages, ASSISTANT_PROMPT);
        final Map<String, Object> requestParameters = getRequestParameters(prompt);
        final String body = Json.toJson(requestParameters);
        return body;
    }

    protected Map<String, Object> getRequestParameters(String prompt) {
        final Map<String, Object> parameters = new HashMap<>(7);

        parameters.put("prompt", prompt);
        parameters.put("max_tokens_to_sample", getMaxTokens());
        parameters.put("temperature", getTemperature());
        parameters.put("top_k", topK);
        parameters.put("top_p", getTopP());
        parameters.put("stop_sequences", getStopSequences());
        parameters.put("anthropic_version", anthropicVersion);

        return parameters;
    }

    protected void listenerErrorResponse(
            Throwable e, ChatRequest listenerRequest, ModelProvider modelProvider, Map<Object, Object> attributes) {
        Throwable error;
        if (e.getCause() instanceof SdkClientException) {
            error = e.getCause();
        } else {
            error = e;
        }

        ChatModelErrorContext errorContext =
                new ChatModelErrorContext(error, listenerRequest, modelProvider, attributes);

        listeners.forEach(listener -> {
            try {
                listener.onError(errorContext);
            } catch (Exception e2) {
                log.warn("Exception while calling model listener", e2);
            }
        });
    }

    protected ChatRequest createListenerRequest(
            InvokeModelRequest invokeModelRequest,
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications) {
        return ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .modelName(invokeModelRequest.modelId())
                        .temperature(this.temperature)
                        .topP((double) this.topP)
                        .maxOutputTokens(this.maxTokens)
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
    }

    protected ChatRequest createListenerRequest(
            InvokeModelWithResponseStreamRequest invokeModelRequest,
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications) {
        return ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .modelName(invokeModelRequest.modelId())
                        .temperature(this.temperature)
                        .topP((double) this.topP)
                        .maxOutputTokens(this.maxTokens)
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
    }

    protected ChatResponse createListenerResponse(
            String responseId, String responseModel, Response<AiMessage> response) {
        if (response == null) {
            return null;
        }

        return ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .id(responseId)
                        .modelName(responseModel)
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }

    /**
     * Get model id
     *
     * @return model id
     */
    protected abstract String getModelId();

    public String getHumanPrompt() {
        return humanPrompt;
    }

    public String getAssistantPrompt() {
        return assistantPrompt;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public Region getRegion() {
        return region;
    }

    public AwsCredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public float getTopP() {
        return topP;
    }

    public String[] getStopSequences() {
        return stopSequences;
    }

    public int getTopK() {
        return topK;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public String getAnthropicVersion() {
        return anthropicVersion;
    }

    public List<ChatModelListener> getListeners() {
        return listeners;
    }

    public abstract static class AbstractSharedBedrockChatModelBuilder<
            C extends AbstractSharedBedrockChatModel, B extends AbstractSharedBedrockChatModelBuilder<C, B>> {
        private boolean isHumanPromptSet;
        private String humanPrompt;
        private boolean isAssistantPromptSet;
        private String assistantPrompt;
        private boolean isMaxRetriesSet;
        private Integer maxRetries;
        private boolean isRegionSet;
        private Region region;
        private boolean isCredentialsProviderSet;
        private AwsCredentialsProvider credentialsProvider;
        private boolean isMaxTokensSet;
        private int maxTokens;
        private boolean isTemperatureSet;
        private double temperature;
        private boolean isTopPSet;
        private float topP;
        private boolean isStopSequencesSet;
        private String[] stopSequences;
        private boolean isTopKSet;
        private int topK;
        private boolean isTimeoutSet;
        private Duration timeout;
        private boolean isAnthropicVersionSet;
        private String anthropicVersion;
        private boolean isListenersSet;
        private List<ChatModelListener> listeners;

        public B humanPrompt(String humanPrompt) {
            this.humanPrompt = humanPrompt;
            this.isHumanPromptSet = true;
            return (B) this.self();
        }

        public B assistantPrompt(String assistantPrompt) {
            this.assistantPrompt = assistantPrompt;
            this.isAssistantPromptSet = true;
            return (B) this.self();
        }

        public B maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            this.isMaxRetriesSet = true;
            return (B) this.self();
        }

        public B region(Region region) {
            this.region = region;
            this.isRegionSet = true;
            return (B) this.self();
        }

        public B credentialsProvider(AwsCredentialsProvider credentialsProvider) {
            this.credentialsProvider = credentialsProvider;
            this.isCredentialsProviderSet = true;
            return (B) this.self();
        }

        public B maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            this.isMaxTokensSet = true;
            return (B) this.self();
        }

        public B temperature(double temperature) {
            this.temperature = temperature;
            this.isTemperatureSet = true;
            return (B) this.self();
        }

        public B topP(float topP) {
            this.topP = topP;
            this.isTopPSet = true;
            return (B) this.self();
        }

        public B stopSequences(String[] stopSequences) {
            this.stopSequences = stopSequences;
            this.isStopSequencesSet = true;
            return (B) this.self();
        }

        public B topK(int topK) {
            this.topK = topK;
            this.isTopKSet = true;
            return (B) this.self();
        }

        public B timeout(Duration timeout) {
            this.timeout = timeout;
            this.isTimeoutSet = true;
            return (B) this.self();
        }

        public B anthropicVersion(String anthropicVersion) {
            this.anthropicVersion = anthropicVersion;
            this.isAnthropicVersionSet = true;
            return (B) this.self();
        }

        public B listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            this.isListenersSet = true;
            return (B) this.self();
        }

        protected abstract B self();

        public abstract C build();

        public String toString() {
            return "AbstractSharedBedrockChatModel.AbstractSharedBedrockChatModelBuilder(" + "humanPrompt$value="
                    + this.humanPrompt + ", assistantPrompt$value="
                    + this.assistantPrompt + ", maxRetries$value="
                    + this.maxRetries + ", region$value="
                    + this.region + ", credentialsProvider$value="
                    + this.credentialsProvider + ", maxTokens$value="
                    + this.maxTokens + ", temperature$value="
                    + this.temperature + ", topP$value="
                    + this.topP + ", stopSequences$value="
                    + Arrays.deepToString(this.stopSequences) + ", topK$value="
                    + this.topK + ", timeout$value="
                    + this.timeout + ", anthropicVersion$value="
                    + this.anthropicVersion + ", listeners$value="
                    + this.listeners + ")";
        }
    }
}
