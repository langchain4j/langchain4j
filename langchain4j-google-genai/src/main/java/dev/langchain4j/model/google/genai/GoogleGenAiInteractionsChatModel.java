package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.google.genai.GoogleGenAiClientFactory.DEFAULT_TIMEOUT;
import static dev.langchain4j.model.google.genai.GoogleGenAiClientFactory.isVertexAiOrApiVersionSetIn;

import com.google.genai.Client;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.SafetySetting;
import com.google.genai.gaos.models.operations.CreateInteractionRequestBody;
import com.google.genai.gaos.models.operations.CreateInteractionResponse;
import com.google.genai.types.HttpRetryOptions;
import dev.langchain4j.Experimental;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Experimental
public class GoogleGenAiInteractionsChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiInteractionsChatModel.class);

    private final Client client;
    private final Integer maxRetries;
    private final List<ChatModelListener> listeners;
    private final GoogleGenAiInteractionsChatRequestParameters defaultRequestParameters;
    private final boolean logRequests;
    private final boolean logResponses;

    private final List<SafetySetting> safetySettings;
    private final String thinkingLevel;
    private final Boolean includeThoughts;
    private final boolean returnThinking;
    private final Integer seed;
    private final boolean googleSearchEnabled;
    private final boolean googleMapsEnabled;
    private final boolean urlContextEnabled;
    private final boolean codeExecutionEnabled;
    private final List<String> allowedFunctionNames;
    private final Map<String, String> labels;
    private final String serviceTier;
    private final Boolean automaticContinuation;
    private final Consumer<CreateModelInteraction.Builder> createInteractionCustomizer;

    private GoogleGenAiInteractionsChatModel(Builder builder) {
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.logRequests = getOrDefault(builder.logRequests, false);
        this.logResponses = getOrDefault(builder.logResponses, false);
        this.listeners = copy(builder.listeners);
        this.googleSearchEnabled = getOrDefault(builder.googleSearch, false);
        this.googleMapsEnabled = getOrDefault(builder.googleMaps, false);
        this.urlContextEnabled = getOrDefault(builder.urlContext, false);
        this.codeExecutionEnabled = getOrDefault(builder.codeExecution, false);
        this.allowedFunctionNames = copy(builder.allowedFunctionNames);
        this.thinkingLevel = builder.thinkingLevel;
        this.includeThoughts = builder.includeThoughts;
        this.returnThinking = getOrDefault(builder.returnThinking, false);
        this.seed = builder.seed;
        this.safetySettings = copy(builder.safetySettings);
        this.labels = builder.labels != null ? new HashMap<>(builder.labels) : null;
        this.serviceTier = builder.serviceTier;
        this.automaticContinuation = builder.automaticContinuation;
        this.createInteractionCustomizer = builder.createInteractionCustomizer;

        if (builder.client != null) {
            this.client = builder.client;
        } else {
            String effectiveApiVersion = builder.apiVersion != null
                    ? builder.apiVersion
                    : (isVertexAiOrApiVersionSetIn(System::getenv) ? null : "v1");
            // Disable SDK-level retries on self-created clients so maxRetries governs retry attempts
            HttpRetryOptions disabledSdkRetries =
                    HttpRetryOptions.builder().attempts(1).build();
            this.client = GoogleGenAiClientFactory.createClient(
                    builder.apiKey,
                    null,
                    null,
                    null,
                    getOrDefault(builder.timeout, DEFAULT_TIMEOUT),
                    builder.customHeaders,
                    builder.apiEndpoint,
                    effectiveApiVersion,
                    disabledSdkRetries);
        }

        ChatRequestParameters commonParameters =
                getOrDefault(builder.defaultRequestParameters, DefaultChatRequestParameters.EMPTY);

        GoogleGenAiInteractionsChatRequestParameters interactionsParameters =
                builder.defaultRequestParameters instanceof GoogleGenAiInteractionsChatRequestParameters p
                        ? p
                        : GoogleGenAiInteractionsChatRequestParameters.EMPTY;

        this.defaultRequestParameters = GoogleGenAiInteractionsChatRequestParameters.builder()
                .modelName(getOrDefault(builder.modelName, commonParameters.modelName()))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .topK(getOrDefault(builder.topK, commonParameters.topK()))
                .frequencyPenalty(getOrDefault(builder.frequencyPenalty, commonParameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(builder.presencePenalty, commonParameters.presencePenalty()))
                .maxOutputTokens(getOrDefault(builder.maxOutputTokens, commonParameters.maxOutputTokens()))
                .stopSequences(getOrDefault(builder.stopSequences, commonParameters.stopSequences()))
                .toolSpecifications(commonParameters.toolSpecifications())
                .toolChoice(commonParameters.toolChoice())
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                .thinkingLevel(getOrDefault(builder.thinkingLevel, interactionsParameters.thinkingLevel()))
                .includeThoughts(getOrDefault(builder.includeThoughts, interactionsParameters.includeThoughts()))
                .serviceTier(getOrDefault(builder.serviceTier, interactionsParameters.serviceTier()))
                .labels(getOrDefault(this.labels, interactionsParameters.labels()))
                .safetySettings(getOrDefault(this.safetySettings, interactionsParameters.safetySettings()))
                .automaticContinuation(
                        getOrDefault(builder.automaticContinuation, interactionsParameters.automaticContinuation()))
                .build();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        GoogleGenAiInteractionsChatRequestParameters parameters =
                defaultRequestParameters.overrideWith(chatRequest.parameters());

        CreateModelInteraction request = GoogleGenAiInteractionsMapper.toCreateModelInteraction(
                chatRequest,
                parameters,
                safetySettings,
                thinkingLevel,
                includeThoughts,
                seed,
                googleSearchEnabled,
                googleMapsEnabled,
                urlContextEnabled,
                codeExecutionEnabled,
                allowedFunctionNames,
                labels,
                serviceTier,
                createInteractionCustomizer,
                false);

        if (logRequests) {
            log.info("Request:\n- model: {}\n- interaction: {}", parameters.modelName(), request);
        }

        boolean enableAutoContinuation =
                getOrDefault(parameters.automaticContinuation(), getOrDefault(automaticContinuation, true));

        CreateInteractionResponse response = withRetryMappingExceptions(
                () -> client.interactions.create(CreateInteractionRequestBody.of(request), enableAutoContinuation),
                maxRetries,
                GoogleGenAiExceptionMapper.INSTANCE);

        Interaction interaction = response.interaction()
                .orElseThrow(() -> new LangChain4jException("Empty interaction returned from Gemini API"));

        ChatResponse chatResponse =
                GoogleGenAiInteractionsMapper.toChatResponse(interaction, parameters.modelName(), returnThinking);

        if (logResponses) {
            log.info("Response:\n- model: {}\n- response: {}", parameters.modelName(), chatResponse);
        }

        return chatResponse;
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.GOOGLE_GENAI;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of(RESPONSE_FORMAT_JSON_SCHEMA);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Client client;
        private String apiKey;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer maxOutputTokens;
        private String thinkingLevel;
        private Boolean includeThoughts;
        private Boolean returnThinking;
        private Integer seed;
        private Integer maxRetries;
        private List<String> stopSequences;
        private Duration timeout;
        private Boolean googleSearch;
        private Boolean googleMaps;
        private Boolean urlContext;
        private Boolean codeExecution;
        private List<SafetySetting> safetySettings;
        private ResponseFormat responseFormat;
        private List<String> allowedFunctionNames;
        private List<ChatModelListener> listeners;
        private ChatRequestParameters defaultRequestParameters;
        private Map<String, String> labels;
        private String serviceTier;
        private String apiEndpoint;
        private @Nullable String apiVersion;
        private Map<String, String> customHeaders;
        private Boolean logRequests;
        private Boolean logResponses;
        private Boolean automaticContinuation;
        private Consumer<CreateModelInteraction.Builder> createInteractionCustomizer;

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder thinkingLevel(String thinkingLevel) {
            this.thinkingLevel = thinkingLevel;
            return this;
        }

        public Builder includeThoughts(Boolean includeThoughts) {
            this.includeThoughts = includeThoughts;
            return this;
        }

        public Builder returnThinking(Boolean returnThinking) {
            this.returnThinking = returnThinking;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder safetySettings(List<SafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder enableGoogleSearch(boolean googleSearch) {
            this.googleSearch = googleSearch;
            return this;
        }

        public Builder enableGoogleMaps(boolean googleMaps) {
            this.googleMaps = googleMaps;
            return this;
        }

        public Builder enableUrlContext(boolean urlContext) {
            this.urlContext = urlContext;
            return this;
        }

        public Builder enableCodeExecution(boolean codeExecution) {
            this.codeExecution = codeExecution;
            return this;
        }

        public Builder allowedFunctionNames(List<String> allowedFunctionNames) {
            this.allowedFunctionNames = allowedFunctionNames;
            return this;
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        public Builder apiVersion(@Nullable String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequests = logRequestsAndResponses;
            this.logResponses = logRequestsAndResponses;
            return this;
        }

        public Builder automaticContinuation(Boolean automaticContinuation) {
            this.automaticContinuation = automaticContinuation;
            return this;
        }

        public Builder createInteractionCustomizer(
                Consumer<CreateModelInteraction.Builder> createInteractionCustomizer) {
            this.createInteractionCustomizer = createInteractionCustomizer;
            return this;
        }

        public GoogleGenAiInteractionsChatModel build() {
            return new GoogleGenAiInteractionsChatModel(this);
        }
    }
}
