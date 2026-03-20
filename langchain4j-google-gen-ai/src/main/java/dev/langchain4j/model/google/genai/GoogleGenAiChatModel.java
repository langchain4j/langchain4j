package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Collections.emptyList;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.Schema;
import com.google.genai.types.ToolConfig;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleGenAiChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiChatModel.class);

    private final Client client;
    private final String modelName;
    private final Integer maxRetries;
    private final Boolean logRequests;
    private final Boolean logResponses;
    private final List<ChatModelListener> listeners;
    private final ChatRequestParameters defaultRequestParameters;

    private final List<SafetySetting> safetySettings;
    private final Schema responseSchema;
    private final Integer thinkingBudget;
    private final Integer seed;
    private final String responseMimeType;
    private final boolean googleSearchEnabled;
    private final List<String> allowedFunctionNames;

    private GoogleGenAiChatModel(Builder builder) {
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = builder.maxRetries == null ? 3 : builder.maxRetries;
        this.logRequests = builder.logRequests != null && builder.logRequests;
        this.logResponses = builder.logResponses != null && builder.logResponses;
        this.listeners = builder.listeners == null ? emptyList() : new ArrayList<>(builder.listeners);
        this.googleSearchEnabled = builder.googleSearch != null && builder.googleSearch;
        this.allowedFunctionNames = builder.allowedFunctionNames;
        this.responseSchema = builder.responseSchema;
        this.responseMimeType = builder.responseMimeType;
        this.thinkingBudget = builder.thinkingBudget;
        this.seed = builder.seed;
        this.safetySettings =
                builder.safetySettings != null ? new ArrayList<>(builder.safetySettings) : new ArrayList<>();

        this.client = builder.client != null
                ? builder.client
                : GoogleGenAiClientFactory.createClient(
                        builder.apiKey,
                        builder.googleCredentials,
                        builder.projectId,
                        builder.location,
                        builder.timeout);

        this.defaultRequestParameters = DefaultChatRequestParameters.builder()
                .temperature(builder.temperature)
                .maxOutputTokens(builder.maxOutputTokens)
                .topP(builder.topP)
                .topK(builder.topK)
                .stopSequences(builder.stopSequences)
                .build();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        ChatRequestParameters parameters = chatRequest.parameters();

        Content systemInstruction = GoogleGenAiContentMapper.toSystemInstruction(chatRequest.messages());
        List<Content> contents = GoogleGenAiContentMapper.toContents(chatRequest.messages());

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters,
                systemInstruction,
                safetySettings,
                responseSchema,
                responseMimeType,
                thinkingBudget,
                seed,
                googleSearchEnabled,
                allowedFunctionNames);

        if (logRequests)
            log.info(
                    "Google Request: model={}, msgCount={}",
                    modelName,
                    chatRequest.messages().size());

        var result = GoogleGenAiRetryHelper.executeWithRetry(
                () -> client.models.generateContent(modelName, contents, config), maxRetries, log);

        ChatResponse chatResponse = GoogleGenAiContentMapper.toChatResponse(result);

        if (logResponses) log.info("Google Response: tokens={}", chatResponse.tokenUsage());

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
        return ModelProvider.GOOGLE_AI_GEMINI;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Client client;
        private GoogleCredentials googleCredentials;
        private String apiKey, projectId, location, modelName;
        private Double temperature, topP;
        private Integer topK, maxOutputTokens, thinkingBudget, seed, maxRetries = 3;
        private List<String> stopSequences;
        private Duration timeout;
        private Boolean googleSearch, logRequests, logResponses;
        private List<SafetySetting> safetySettings;
        private Schema responseSchema;
        private String responseMimeType;
        private List<String> allowedFunctionNames;
        private ToolConfig toolConfig;
        private List<ChatModelListener> listeners;

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder googleCredentials(GoogleCredentials credentials) {
            this.googleCredentials = credentials;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
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

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
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

        public Builder responseSchema(Schema responseSchema) {
            this.responseSchema = responseSchema;
            return this;
        }

        public Builder responseMimeType(String responseMimeType) {
            this.responseMimeType = responseMimeType;
            return this;
        }

        public Builder enableGoogleSearch(boolean googleSearch) {
            this.googleSearch = googleSearch;
            return this;
        }

        public Builder toolConfig(ToolConfig toolConfig) {
            this.toolConfig = toolConfig;
            return this;
        }

        public Builder allowedFunctionNames(List<String> allowedFunctionNames) {
            this.allowedFunctionNames = allowedFunctionNames;
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

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public GoogleGenAiChatModel build() {
            return new GoogleGenAiChatModel(this);
        }
    }
}
