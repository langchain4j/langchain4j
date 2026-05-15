package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.SafetySetting;
import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
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

@Experimental
public class GoogleGenAiStreamingChatModel implements StreamingChatModel {

    private final Client client;
    private final List<ChatModelListener> listeners;
    private final ChatRequestParameters defaultRequestParameters;

    private final List<SafetySetting> safetySettings;
    private final Integer thinkingBudget;
    private final Integer seed;
    private final boolean googleSearchEnabled;
    private final boolean googleMapsEnabled;
    private final boolean urlContextEnabled;
    private final List<String> allowedFunctionNames;

    private final ExecutorService executor;

    private GoogleGenAiStreamingChatModel(Builder builder) {
        this.listeners = copy(builder.listeners);
        this.googleSearchEnabled = getOrDefault(builder.googleSearch, false);
        this.googleMapsEnabled = getOrDefault(builder.googleMaps, false);
        this.urlContextEnabled = getOrDefault(builder.urlContext, false);
        this.allowedFunctionNames = copy(builder.allowedFunctionNames);
        this.thinkingBudget = builder.thinkingBudget;
        this.seed = builder.seed;
        this.safetySettings = copy(builder.safetySettings);

        this.client = builder.client != null
                ? builder.client
                : GoogleGenAiClientFactory.createClient(
                        builder.apiKey,
                        builder.googleCredentials,
                        builder.projectId,
                        builder.location,
                        builder.timeout);

        ChatRequestParameters commonParameters =
                getOrDefault(builder.defaultRequestParameters, DefaultChatRequestParameters.EMPTY);

        this.defaultRequestParameters = DefaultChatRequestParameters.builder()
                .modelName(getOrDefault(builder.modelName, commonParameters.modelName()))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .topK(getOrDefault(builder.topK, commonParameters.topK()))
                .maxOutputTokens(getOrDefault(builder.maxOutputTokens, commonParameters.maxOutputTokens()))
                .stopSequences(getOrDefault(builder.stopSequences, commonParameters.stopSequences()))
                .toolSpecifications(commonParameters.toolSpecifications())
                .toolChoice(commonParameters.toolChoice())
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                .build();

        this.executor = getOrDefault(builder.executor, DefaultExecutorProvider::getDefaultExecutorService);
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        String modelName = chatRequest.modelName();

        Content systemInstruction = GoogleGenAiContentMapper.toSystemInstruction(chatRequest.messages());
        List<Content> contents = GoogleGenAiContentMapper.toContents(chatRequest.messages());

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                chatRequest.parameters(),
                systemInstruction,
                safetySettings,
                thinkingBudget,
                seed,
                googleSearchEnabled,
                googleMapsEnabled,
                urlContextEnabled,
                allowedFunctionNames);

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
                    ChatResponse partialResponse = GoogleGenAiContentMapper.toChatResponse(chunk, modelName);
                    AiMessage aiMessage = partialResponse.aiMessage();

                    if (aiMessage.text() != null && !aiMessage.text().isEmpty()) {
                        textBuilder.append(aiMessage.text());
                        try {
                            handler.onPartialResponse(aiMessage.text());
                        } catch (Exception userException) {
                            handler.onError(userException);
                        }
                    }

                    if (aiMessage.toolExecutionRequests() != null) {
                        for (ToolExecutionRequest req : aiMessage.toolExecutionRequests()) {
                            toolRequests.add(req);
                            try {
                                handler.onCompleteToolCall(new CompleteToolCall(toolIndex++, req));
                            } catch (Exception userException) {
                                handler.onError(userException);
                            }
                        }
                    }

                    if (partialResponse.tokenUsage() != null) {
                        tokenUsage = partialResponse.tokenUsage();
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
                        .modelName(modelName)
                        .tokenUsage(tokenUsage)
                        .finishReason(
                                !toolRequests.isEmpty()
                                        ? FinishReason.TOOL_EXECUTION
                                        : (finishReason != null ? finishReason : FinishReason.STOP))
                        .rawResponse(lastChunk)
                        .build();

                ChatResponse finalChatResponse = ChatResponse.builder()
                        .aiMessage(finalAiMessage)
                        .metadata(metadata)
                        .build();

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
        private String apiKey, projectId, location, modelName;
        private Double temperature, topP;
        private Integer topK, maxOutputTokens, thinkingBudget, seed;
        private List<String> stopSequences;
        private Duration timeout;
        private Boolean googleSearch;
        private Boolean googleMaps;
        private Boolean urlContext;
        private List<SafetySetting> safetySettings;
        private ResponseFormat responseFormat;
        private List<String> allowedFunctionNames;
        private List<ChatModelListener> listeners;
        private ExecutorService executor;
        private ChatRequestParameters defaultRequestParameters;

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

        public Builder allowedFunctionNames(List<String> allowedFunctionNames) {
            this.allowedFunctionNames = allowedFunctionNames;
            return this;
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        /**
         * Executor used to drive the blocking {@link com.google.genai.ResponseStream} iteration off the calling
         * thread. If not set, a shared default executor from
         * {@link dev.langchain4j.internal.DefaultExecutorProvider} is used.
         *
         * <p><b>Strongly recommended:</b> supply an executor managed by your application (Spring/Quarkus task
         * executor, virtual-thread executor, bounded pool, etc.). The default executor is unbounded and not tied
         * to any application lifecycle, so it offers no back-pressure or graceful shutdown.
         */
        public Builder executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public GoogleGenAiStreamingChatModel build() {
            return new GoogleGenAiStreamingChatModel(this);
        }
    }
}
