package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Collections.emptyList;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.Schema;
import com.google.genai.types.ToolConfig;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleGenAiStreamingChatModel implements StreamingChatModel {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiStreamingChatModel.class);

    private final Client client;
    private final String modelName;
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
    private final boolean googleMapsEnabled;
    private final boolean urlContextEnabled;
    private final List<String> allowedFunctionNames;
    private final GenerateContentConfig generateContentConfig;

    private final ExecutorService executor;

    private GoogleGenAiStreamingChatModel(Builder builder) {
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.logRequests = builder.logRequests != null && builder.logRequests;
        this.logResponses = builder.logResponses != null && builder.logResponses;
        this.listeners = builder.listeners == null ? emptyList() : new ArrayList<>(builder.listeners);
        this.googleSearchEnabled = builder.googleSearch != null && builder.googleSearch;
        this.googleMapsEnabled = builder.googleMaps != null && builder.googleMaps;
        this.urlContextEnabled = builder.urlContext != null && builder.urlContext;
        this.allowedFunctionNames = builder.allowedFunctionNames;
        this.responseSchema = builder.responseSchema;
        this.responseMimeType = builder.responseMimeType;
        this.thinkingBudget = builder.thinkingBudget;
        this.seed = builder.seed;
        this.safetySettings =
                builder.safetySettings != null ? new ArrayList<>(builder.safetySettings) : new ArrayList<>();
        this.generateContentConfig = builder.generateContentConfig;

        this.client = builder.client != null
                ? builder.client
                : GoogleGenAiClientFactory.createClient(
                        builder.apiKey,
                        builder.googleCredentials,
                        builder.projectId,
                        builder.location,
                        builder.timeout);

        if (builder.defaultRequestParameters != null) {
            this.defaultRequestParameters = builder.defaultRequestParameters;
        } else {
            this.defaultRequestParameters = DefaultChatRequestParameters.builder()
                    .temperature(builder.temperature)
                    .maxOutputTokens(builder.maxOutputTokens)
                    .topP(builder.topP)
                    .topK(builder.topK)
                    .stopSequences(builder.stopSequences)
                    .build();
        }

        this.executor = builder.executor != null ? builder.executor : Executors.newCachedThreadPool();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestParameters parameters = chatRequest.parameters();

        Content systemInstruction = GoogleGenAiContentMapper.toSystemInstruction(chatRequest.messages());
        List<Content> contents = GoogleGenAiContentMapper.toContents(chatRequest.messages());

        GenerateContentConfig config = generateContentConfig != null
                ? generateContentConfig
                : GoogleGenAiConfigBuilder.buildConfig(
                        parameters,
                        systemInstruction,
                        safetySettings,
                        responseSchema,
                        responseMimeType,
                        thinkingBudget,
                        seed,
                        googleSearchEnabled,
                        googleMapsEnabled,
                        urlContextEnabled,
                        allowedFunctionNames);

        if (logRequests) {
            log.info(
                    "Google Streaming Request: model={}, msgCount={}",
                    modelName,
                    chatRequest.messages().size());
        }

        executor.execute(() -> {
            try {
                ResponseStream<GenerateContentResponse> stream =
                        client.models.generateContentStream(modelName, contents, config);

                StringBuilder textBuilder = new StringBuilder();
                List<ToolExecutionRequest> toolRequests = new ArrayList<>();
                TokenUsage tokenUsage = new TokenUsage();
                FinishReason finishReason = null;
                GenerateContentResponse lastChunk = null;

                int toolIndex = 0;

                for (GenerateContentResponse chunk : stream) {
                    lastChunk = chunk;
                    ChatResponse partialResponse = GoogleGenAiContentMapper.toChatResponse(chunk);
                    AiMessage aiMessage = partialResponse.aiMessage();

                    if (aiMessage.text() != null && !aiMessage.text().isEmpty()) {
                        textBuilder.append(aiMessage.text());
                        handler.onPartialResponse(aiMessage.text());
                    }

                    if (aiMessage.toolExecutionRequests() != null) {
                        for (ToolExecutionRequest req : aiMessage.toolExecutionRequests()) {
                            toolRequests.add(req);
                            handler.onCompleteToolCall(new CompleteToolCall(toolIndex++, req));
                        }
                    }

                    if (partialResponse.tokenUsage() != null) {
                        tokenUsage =
                                partialResponse.tokenUsage(); // Gemini usually sends cumulative usage in last chunk
                    }
                    if (partialResponse.finishReason() != null
                            && partialResponse.finishReason() != FinishReason.OTHER) {
                        finishReason = partialResponse.finishReason();
                    }
                }

                AiMessage finalAiMessage;
                if (!toolRequests.isEmpty() && textBuilder.length() > 0) {
                    finalAiMessage = new AiMessage(textBuilder.toString(), toolRequests);
                } else if (!toolRequests.isEmpty()) {
                    finalAiMessage = AiMessage.from(toolRequests);
                } else {
                    finalAiMessage = AiMessage.from(textBuilder.toString());
                }

                GoogleGenAiChatResponseMetadata metadata = GoogleGenAiChatResponseMetadata.builder()
                        .tokenUsage(tokenUsage)
                        .finishReason(finishReason != null ? finishReason : FinishReason.STOP)
                        .rawResponse(lastChunk)
                        .build();

                ChatResponse finalChatResponse = ChatResponse.builder()
                        .aiMessage(finalAiMessage)
                        .metadata(metadata)
                        .build();

                if (logResponses) {
                    log.info("Google Streaming Response: tokens={}", finalChatResponse.tokenUsage());
                }

                handler.onCompleteResponse(finalChatResponse);
            } catch (Exception e) {
                handler.onError(e);
            }
        });
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
        private Integer topK, maxOutputTokens, thinkingBudget, seed;
        private List<String> stopSequences;
        private Duration timeout;
        private Boolean googleSearch, googleMaps, urlContext, logRequests, logResponses;
        private List<SafetySetting> safetySettings;
        private Schema responseSchema;
        private String responseMimeType;
        private List<String> allowedFunctionNames;
        private ToolConfig toolConfig;
        private List<ChatModelListener> listeners;
        private ExecutorService executor;
        private ChatRequestParameters defaultRequestParameters;
        private GenerateContentConfig generateContentConfig;

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

        public Builder enableGoogleMaps(boolean googleMaps) {
            this.googleMaps = googleMaps;
            return this;
        }

        public Builder enableUrlContext(boolean urlContext) {
            this.urlContext = urlContext;
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

        public Builder executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public Builder generateContentConfig(GenerateContentConfig generateContentConfig) {
            this.generateContentConfig = generateContentConfig;
            return this;
        }

        public GoogleGenAiStreamingChatModel build() {
            return new GoogleGenAiStreamingChatModel(this);
        }
    }
}
