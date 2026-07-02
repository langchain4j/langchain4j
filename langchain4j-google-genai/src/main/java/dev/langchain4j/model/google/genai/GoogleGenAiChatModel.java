package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.SafetySetting;
import dev.langchain4j.Experimental;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Experimental
public class GoogleGenAiChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiChatModel.class);

    private final Client client;
    private final Integer maxRetries;
    private final List<ChatModelListener> listeners;
    private final ChatRequestParameters defaultRequestParameters;
    private final boolean logRequests;
    private final boolean logResponses;

    private final List<SafetySetting> safetySettings;
    private final Integer thinkingBudget;
    private final String thinkingLevel;
    private final Integer seed;
    private final boolean googleSearchEnabled;
    private final boolean googleMapsEnabled;
    private final boolean urlContextEnabled;
    private final List<String> allowedFunctionNames;
    private final String vertexSearchDatastore;
    private final Map<String, String> labels;
    private final String cachedContent;

    private GoogleGenAiChatModel(Builder builder) {
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.logRequests = getOrDefault(builder.logRequests, false);
        this.logResponses = getOrDefault(builder.logResponses, false);
        this.listeners = copy(builder.listeners);
        this.googleSearchEnabled = getOrDefault(builder.googleSearch, false);
        this.googleMapsEnabled = getOrDefault(builder.googleMaps, false);
        this.urlContextEnabled = getOrDefault(builder.urlContext, false);
        this.allowedFunctionNames = copy(builder.allowedFunctionNames);
        this.thinkingBudget = builder.thinkingBudget;
        this.thinkingLevel = builder.thinkingLevel;
        this.seed = builder.seed;
        this.safetySettings = copy(builder.safetySettings);
        this.vertexSearchDatastore = builder.vertexSearchDatastore;
        this.labels = builder.labels != null ? new HashMap<>(builder.labels) : null;
        this.cachedContent = builder.cachedContent;

        this.client = builder.client != null
                ? builder.client
                : GoogleGenAiClientFactory.createClient(
                        builder.apiKey,
                        builder.googleCredentials,
                        builder.projectId,
                        builder.location,
                        builder.timeout,
                        builder.customHeaders,
                        builder.apiEndpoint);

        ChatRequestParameters commonParameters =
                getOrDefault(builder.defaultRequestParameters, DefaultChatRequestParameters.EMPTY);

        this.defaultRequestParameters = DefaultChatRequestParameters.builder()
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
                .build();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        Content systemInstruction = GoogleGenAiContentMapper.toSystemInstruction(chatRequest.messages());
        List<Content> contents = GoogleGenAiContentMapper.toContents(chatRequest.messages());

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                chatRequest.parameters(),
                systemInstruction,
                safetySettings,
                thinkingBudget,
                thinkingLevel,
                seed,
                googleSearchEnabled,
                googleMapsEnabled,
                urlContextEnabled,
                allowedFunctionNames,
                vertexSearchDatastore,
                labels,
                cachedContent);

        if (logRequests) {
            log.info(
                    "Request:\n- model: {}\n- messages: {}\n- config: {}",
                    chatRequest.modelName(),
                    chatRequest.messages(),
                    config);
        }

        var result = withRetryMappingExceptions(
                () -> client.models.generateContent(chatRequest.modelName(), contents, config), maxRetries);

        ChatResponse response = GoogleGenAiContentMapper.toChatResponse(result, chatRequest.modelName());

        if (logResponses) {
            log.info("Response:\n- model: {}\n- response: {}", chatRequest.modelName(), response);
        }

        return response;
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
        private GoogleCredentials googleCredentials;
        private String apiKey;
        private String projectId;
        private String location;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer maxOutputTokens;
        private Integer thinkingBudget;
        private String thinkingLevel;
        private Integer seed;
        private Integer maxRetries;
        private List<String> stopSequences;
        private Duration timeout;
        private Boolean googleSearch;
        private Boolean googleMaps;
        private Boolean urlContext;
        private List<SafetySetting> safetySettings;
        private ResponseFormat responseFormat;
        private List<String> allowedFunctionNames;
        private List<ChatModelListener> listeners;
        private ChatRequestParameters defaultRequestParameters;
        private String vertexSearchDatastore;
        private Map<String, String> labels;
        private String apiEndpoint;
        private Map<String, String> customHeaders;
        private String cachedContent;
        private Boolean logRequests;
        private Boolean logResponses;

        /**
         * Sets a pre-configured Google GenAI {@link Client}.
         * <p>
         * Use this when you need full control over client configuration.
         * When set, {@link #apiKey(String)}, {@link #googleCredentials(GoogleCredentials)},
         * {@link #projectId(String)}, {@link #location(String)}, and {@link #timeout(Duration)} are ignored.
         *
         * @param client the pre-configured client
         * @return {@code this}
         */
        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        /**
         * Sets the Google OAuth2 credentials used to authenticate requests via Vertex AI.
         * <p>
         * Use this instead of {@link #apiKey(String)} when authenticating with a service account
         * or Application Default Credentials (ADC).
         * Requires {@link #projectId(String)} and {@link #location(String)} to be set.
         *
         * @param credentials the Google OAuth2 credentials
         * @return {@code this}
         */
        public Builder googleCredentials(GoogleCredentials credentials) {
            this.googleCredentials = credentials;
            return this;
        }

        /**
         * Sets the Google AI Gemini API key used to authenticate requests.
         * <p>
         * Use this for the Google AI Studio API. For Vertex AI, use {@link #googleCredentials(GoogleCredentials)}.
         * Alternatively, set the {@code GOOGLE_API_KEY} environment variable.
         *
         * @param apiKey the API key
         * @return {@code this}
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the Google Cloud project ID for Vertex AI access.
         * <p>
         * Required when using {@link #googleCredentials(GoogleCredentials)} for Vertex AI.
         *
         * @param projectId the GCP project ID
         * @return {@code this}
         */
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        /**
         * Sets the Google Cloud region/location for Vertex AI access (e.g. {@code "us-central1"}).
         * <p>
         * Required when using {@link #googleCredentials(GoogleCredentials)} for Vertex AI.
         *
         * @param location the GCP region
         * @return {@code this}
         */
        public Builder location(String location) {
            this.location = location;
            return this;
        }

        /**
         * Sets the model to use for chat completions.
         * <p>
         * Examples: {@code "gemini-2.0-flash"}, {@code "gemini-2.5-pro"}.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the HTTP request timeout for calls to the Google GenAI API.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the sampling temperature in the range {@code [0.0, 2.0]}.
         * Higher values produce more creative output; lower values produce more deterministic output.
         *
         * @param temperature the sampling temperature
         * @return {@code this}
         */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the nucleus sampling probability (top-p).
         * Only the tokens whose cumulative probability exceeds this threshold are considered.
         *
         * @param topP the nucleus sampling threshold
         * @return {@code this}
         */
        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * Sets the top-K sampling value. Only the {@code topK} most-likely next tokens are considered at each step.
         *
         * @param topK the number of top tokens to sample from
         * @return {@code this}
         */
        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        /**
         * Sets the frequency penalty, which reduces the likelihood of repeating tokens
         * proportionally to how often they have appeared in the response so far.
         *
         * @param frequencyPenalty the frequency penalty
         * @return {@code this}
         */
        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        /**
         * Sets the presence penalty, which reduces the likelihood of repeating any token
         * that has already appeared in the response, regardless of frequency.
         *
         * @param presencePenalty the presence penalty
         * @return {@code this}
         */
        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        /**
         * Sets the maximum number of tokens to generate in the response.
         *
         * @param maxOutputTokens the maximum number of output tokens
         * @return {@code this}
         */
        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        /**
         * The thinking budget to use. This is a legacy parameter. For Gemini 3.x models, use {@link #thinkingLevel(String)} instead.
         */
        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        /**
         * The thinking level to use. This is the recommended parameter for Gemini 3.x models.
         * Allowed values are {@code "MINIMAL"}, {@code "LOW"}, {@code "MEDIUM"}, {@code "HIGH"}.
         * Note that this cannot be used together with {@link #thinkingBudget(Integer)}.
         */
        public Builder thinkingLevel(String thinkingLevel) {
            this.thinkingLevel = thinkingLevel;
            return this;
        }

        /**
         * Sets the random seed for deterministic output.
         * Requests with the same seed and parameters should produce the same response.
         *
         * @param seed the random seed
         * @return {@code this}
         */
        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Sets sequences that, when generated, will cause the model to stop generating further tokens.
         *
         * @param stopSequences the list of stop sequences
         * @return {@code this}
         */
        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        /**
         * Sets the number of times to retry a request on transient errors (e.g. rate limits, server errors).
         * <p>
         * Defaults to {@code 2}.
         *
         * @param maxRetries the maximum number of retry attempts
         * @return {@code this}
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets safety content filter settings to block harmful content.
         * See the <a href="https://ai.google.dev/gemini-api/docs/safety-settings">safety settings docs</a>.
         *
         * @param safetySettings the list of safety settings
         * @return {@code this}
         */
        public Builder safetySettings(List<SafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        /**
         * Sets the response format, enabling structured output such as JSON mode or JSON Schema.
         *
         * @param responseFormat the desired response format
         * @return {@code this}
         */
        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        /**
         * Enables the Google Search grounding tool, allowing the model to retrieve
         * real-time information from the web.
         * See the <a href="https://ai.google.dev/gemini-api/docs/google-search">Google Search tool docs</a>.
         *
         * @param googleSearch whether to enable Google Search
         * @return {@code this}
         */
        public Builder enableGoogleSearch(boolean googleSearch) {
            this.googleSearch = googleSearch;
            return this;
        }

        /**
         * Enables the Google Maps grounding tool, allowing the model to retrieve
         * location-based information.
         * See the <a href="https://ai.google.dev/gemini-api/docs/maps-grounding">Google Maps tool docs</a>.
         *
         * @param googleMaps whether to enable Google Maps
         * @return {@code this}
         */
        public Builder enableGoogleMaps(boolean googleMaps) {
            this.googleMaps = googleMaps;
            return this;
        }

        /**
         * Enables the URL context tool, allowing the model to read and reason about
         * content at provided URLs.
         * See the <a href="https://ai.google.dev/gemini-api/docs/url-context">URL context tool docs</a>.
         *
         * @param urlContext whether to enable URL context
         * @return {@code this}
         */
        public Builder enableUrlContext(boolean urlContext) {
            this.urlContext = urlContext;
            return this;
        }

        /**
         * Restricts function calling to only the specified function names.
         * When set, the model can only call functions whose names appear in this list.
         *
         * @param allowedFunctionNames the list of allowed function names
         * @return {@code this}
         */
        public Builder allowedFunctionNames(List<String> allowedFunctionNames) {
            this.allowedFunctionNames = allowedFunctionNames;
            return this;
        }

        /**
         * Sets the list of {@link ChatModelListener}s to be notified on each request and response.
         * Useful for logging, metrics, and observability integrations.
         *
         * @param listeners the chat model listeners
         * @return {@code this}
         */
        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        /**
         * Sets default {@link ChatRequestParameters} that are merged into every request.
         * Individual request parameters take precedence over these defaults.
         *
         * @param defaultRequestParameters the default request parameters
         * @return {@code this}
         */
        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        /**
         * Sets the Vertex AI Search datastore to use for grounding the model's responses
         * with enterprise data.
         *
         * @param vertexSearchDatastore the Vertex AI Search datastore resource name
         * @return {@code this}
         */
        public Builder vertexSearchDatastore(String vertexSearchDatastore) {
            this.vertexSearchDatastore = vertexSearchDatastore;
            return this;
        }

        /**
         * Sets labels (key-value metadata) attached to the request.
         * Useful for cost attribution and billing tracking in Google Cloud.
         *
         * @param labels a map of label keys to values
         * @return {@code this}
         */
        public Builder labels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        /**
         * Overrides the default API endpoint.
         * <p>
         * Useful for pointing at a regional endpoint or a compatible proxy.
         *
         * @param apiEndpoint the custom API endpoint URL
         * @return {@code this}
         */
        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        /**
         * Sets extra HTTP headers to include in every request to the Google GenAI API.
         *
         * @param customHeaders a map of header names to values
         * @return {@code this}
         */
        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        /**
         * Sets the name of a previously created cached content resource to use with this model.
         * Using cached content can reduce latency and cost for repeated prompts.
         * See the <a href="https://ai.google.dev/gemini-api/docs/caching">context caching docs</a>.
         *
         * @param cachedContent the cached content resource name
         * @return {@code this}
         */
        public Builder cachedContent(String cachedContent) {
            this.cachedContent = cachedContent;
            return this;
        }

        /**
         * Enables debug logging of request details sent to the Google GenAI API.
         *
         * @param logRequests whether to log requests
         * @return {@code this}
         */
        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables debug logging of response details received from the Google GenAI API.
         *
         * @param logResponses whether to log responses
         * @return {@code this}
         */
        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * Enables debug logging of both request and response details.
         * Equivalent to calling both {@link #logRequests(Boolean)} and {@link #logResponses(Boolean)}.
         *
         * @param logRequestsAndResponses whether to log requests and responses
         * @return {@code this}
         */
        public Builder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequests = logRequestsAndResponses;
            this.logResponses = logRequestsAndResponses;
            return this;
        }

        public GoogleGenAiChatModel build() {
            return new GoogleGenAiChatModel(this);
        }
    }
}
