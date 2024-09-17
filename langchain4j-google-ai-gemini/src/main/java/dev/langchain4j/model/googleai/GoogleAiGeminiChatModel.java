package dev.langchain4j.model.googleai;

import com.google.gson.Gson;
import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import retrofit2.Call;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromMessageToGContent;
import static dev.langchain4j.model.googleai.FinishReasonMapper.fromGFinishReasonToFinishReason;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromGPartsToAiMessage;
import static dev.langchain4j.model.googleai.SchemaMapper.fromJsonSchemaToGSchema;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;

@Experimental
@Slf4j
public class GoogleAiGeminiChatModel implements ChatLanguageModel {
    private static final Gson GSON = new Gson();

    private final GeminiService geminiService;

    private final String apiKey;
    private final String modelName;

    private final Integer maxRetries;
    private final Double temperature;
    private final Integer topK;
    private final Double topP;
    private final Integer maxOutputTokens;
    private final List<String> stopSequences;

    private final Integer candidateCount;

    private final ResponseFormat responseFormat;

    private final GeminiFunctionCallingConfig toolConfig;

    private final boolean allowCodeExecution;
    private final boolean includeCodeExecutionOutput;

    private final List<GeminiSafetySetting> safetySettings;
    private final List<ChatModelListener> listeners;

    @Builder
    public GoogleAiGeminiChatModel(String apiKey, String modelName,
                                   Integer maxRetries,
                                   Double temperature, Integer topK, Double topP,
                                   Integer maxOutputTokens, Integer candidateCount,
                                   Duration timeout,
                                   ResponseFormat responseFormat,
                                   List<String> stopSequences, GeminiFunctionCallingConfig toolConfig,
                                   Boolean allowCodeExecution, Boolean includeCodeExecutionOutput,
                                   Boolean logRequestsAndResponses,
                                   List<GeminiSafetySetting> safetySettings,
                                   List<ChatModelListener> listeners
    ) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.modelName = ensureNotBlank(modelName, "modelName");

        this.maxRetries = getOrDefault(maxRetries, 3);

        // using Gemini's default values
        this.temperature = getOrDefault(temperature, 1.0);
        this.topK = getOrDefault(topK, 64);
        this.topP = getOrDefault(topP, 0.95);
        this.maxOutputTokens = getOrDefault(maxOutputTokens, 8192);
        this.candidateCount = getOrDefault(candidateCount, 1);
        this.stopSequences = getOrDefault(stopSequences, emptyList());

        this.toolConfig = toolConfig;

        this.allowCodeExecution = allowCodeExecution != null ? allowCodeExecution : false;
        this.includeCodeExecutionOutput = includeCodeExecutionOutput != null ? includeCodeExecutionOutput : false;

        this.safetySettings = copyIfNotNull(safetySettings);

        this.responseFormat = responseFormat;

        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);

        this.geminiService = GeminiService.getGeminiService(
            getOrDefault(logRequestsAndResponses, false) ? this.log : null,
            getOrDefault(timeout, ofSeconds(60))
        );
    }

    private static String computeMimeType(ResponseFormat responseFormat) {
        if (responseFormat == null || ResponseFormatType.TEXT.equals(responseFormat.type())) {
            return "text/plain";
        }

        if (ResponseFormatType.JSON.equals(responseFormat.type()) &&
            responseFormat.jsonSchema() != null &&
            responseFormat.jsonSchema().rootElement() != null &&
            responseFormat.jsonSchema().rootElement() instanceof JsonEnumSchema) {

            return "text/x.enum";
        }

        return "application/json";
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ChatRequest request = ChatRequest.builder()
            .messages(messages)
            .build();

        ChatResponse response = chat(request);

        return Response.from(response.aiMessage(),
            response.tokenUsage(),
            response.finishReason());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, Collections.singletonList(toolSpecification));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ChatRequest request = ChatRequest.builder()
            .messages(messages)
            .toolSpecifications(toolSpecifications)
            .build();

        ChatResponse response = chat(request);

        return Response.from(response.aiMessage(),
            response.tokenUsage(),
            response.finishReason());
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        GeminiContent systemInstruction = new GeminiContent(GeminiRole.MODEL.toString());
        List<GeminiContent> geminiContentList = fromMessageToGContent(chatRequest.messages(), systemInstruction);
        List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();

        ResponseFormat format = chatRequest.responseFormat() != null ? chatRequest.responseFormat() : this.responseFormat;
        GeminiSchema schema = null;

        String responseMimeType = computeMimeType(format);

        if (format != null && format.jsonSchema() != null) {
            schema = fromJsonSchemaToGSchema(format.jsonSchema());
        }

        GeminiGenerateContentRequest request = GeminiGenerateContentRequest.builder()
            .contents(geminiContentList)
            .systemInstruction(!systemInstruction.getParts().isEmpty() ? systemInstruction : null)
            .generationConfig(GeminiGenerationConfig.builder()
                .candidateCount(this.candidateCount)
                .maxOutputTokens(this.maxOutputTokens)
                .responseMimeType(responseMimeType)
                .responseSchema(schema)
                .stopSequences(this.stopSequences)
                .temperature(this.temperature)
                .topK(this.topK)
                .topP(this.topP)
                .build())
            .safetySettings(this.safetySettings)
            .tools(FunctionMapper.fromToolSepcsToGTool(toolSpecifications, this.allowCodeExecution))
            .toolConfig(new GeminiToolConfig(this.toolConfig))
            .build();

        ChatModelRequest chatModelRequest = ChatModelRequest.builder()
            .model(modelName)
            .temperature(temperature)
            .topP(topP)
            .maxTokens(maxOutputTokens)
            .messages(chatRequest.messages())
            .toolSpecifications(chatRequest.toolSpecifications())
            .build();
        ConcurrentHashMap<Object, Object> listenerAttributes = new ConcurrentHashMap<>();
        ChatModelRequestContext chatModelRequestContext = new ChatModelRequestContext(chatModelRequest, listenerAttributes);
        listeners.forEach((listener) -> {
            try {
                listener.onRequest(chatModelRequestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener (onRequest)", e);
            }
        });

        Call<GeminiGenerateContentResponse> responseCall =
            withRetry(() -> this.geminiService.generateContent(this.modelName, this.apiKey, request), this.maxRetries);

        GeminiGenerateContentResponse geminiResponse;
        try {
            retrofit2.Response<GeminiGenerateContentResponse> executed = responseCall.execute();
            geminiResponse = executed.body();

            if (executed.code() >= 300) {
                try (ResponseBody errorBody = executed.errorBody()) {
                    GeminiError error = GSON.fromJson(errorBody.string(), GeminiErrorContainer.class).getError();

                    RuntimeException runtimeException = new RuntimeException(
                        String.format("%s (code %d) %s", error.getStatus(), error.getCode(), error.getMessage()));

                    ChatModelErrorContext chatModelErrorContext = new ChatModelErrorContext(
                        runtimeException, chatModelRequest, null, listenerAttributes
                    );
                    listeners.forEach((listener) -> {
                        try {
                            listener.onError(chatModelErrorContext);
                        } catch (Exception e) {
                            log.warn("Exception while calling model listener (onError)", e);
                        }
                    });

                    throw runtimeException;
                }
            }
        } catch (IOException e) {
            RuntimeException runtimeException = new RuntimeException("An error occurred when calling the Gemini API endpoint.", e);

            ChatModelErrorContext chatModelErrorContext = new ChatModelErrorContext(
                e, chatModelRequest, null, listenerAttributes
            );
            listeners.forEach((listener) -> {
                try {
                    listener.onError(chatModelErrorContext);
                } catch (Exception ex) {
                    log.warn("Exception while calling model listener (onError)", ex);
                }
            });

            throw runtimeException;
        }

        if (geminiResponse != null) {
            GeminiCandidate firstCandidate = geminiResponse.getCandidates().get(0); //TODO handle n
            GeminiUsageMetadata tokenCounts = geminiResponse.getUsageMetadata();

            AiMessage aiMessage;

            FinishReason finishReason = fromGFinishReasonToFinishReason(firstCandidate.getFinishReason());
            if (firstCandidate.getContent() == null) {
                aiMessage = AiMessage.from("No text was returned by the model. " +
                    "The model finished generating because of the following reason: " + finishReason);
            } else {
                aiMessage = fromGPartsToAiMessage(firstCandidate.getContent().getParts(), this.includeCodeExecutionOutput);
            }

            TokenUsage tokenUsage = new TokenUsage(tokenCounts.getPromptTokenCount(),
                tokenCounts.getCandidatesTokenCount(),
                tokenCounts.getTotalTokenCount());

            ChatModelResponse chatModelResponse = ChatModelResponse.builder()
                .model(modelName)
                .tokenUsage(tokenUsage)
                .finishReason(finishReason)
                .aiMessage(aiMessage)
                .build();
            ChatModelResponseContext chatModelResponseContext = new ChatModelResponseContext(
                chatModelResponse, chatModelRequest, listenerAttributes);
            listeners.forEach((listener) -> {
                try {
                    listener.onResponse(chatModelResponseContext);
                } catch (Exception e) {
                    log.warn("Exception while calling model listener (onResponse)", e);
                }
            });

            return ChatResponse.builder()
                .aiMessage(aiMessage)
                .finishReason(finishReason)
                .tokenUsage(tokenUsage)
                .build();
        } else {
            throw new RuntimeException("Gemini response was null");
        }
    }

    public int countTokens(List<ChatMessage> messages) {
        List<GeminiContent> geminiContentList = fromMessageToGContent(messages, null);
        GeminiCountTokensRequest countTokensRequest = new GeminiCountTokensRequest();
        countTokensRequest.setContents(geminiContentList);

        Call<GeminiCountTokensResponse> responseCall =
            withRetry(() -> this.geminiService.countTokens(this.modelName, this.apiKey, countTokensRequest), this.maxRetries);

        GeminiCountTokensResponse countTokensResponse;
        try {
            retrofit2.Response<GeminiCountTokensResponse> executed = responseCall.execute();
            countTokensResponse = executed.body();

            if (executed.code() >= 300) {
                try (ResponseBody errorBody = executed.errorBody()) {
                    GeminiError error = GSON.fromJson(errorBody.string(), GeminiErrorContainer.class).getError();

                    throw new RuntimeException(
                        String.format("%s (code %d) %s", error.getStatus(), error.getCode(), error.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("An error occurred when calling the Gemini API endpoint to calculate tokens count", e);
        }

        return countTokensResponse.getTotalTokens();
    }

    public int countTokens(String message) {
        return countTokens(Collections.singletonList(UserMessage.from(message)));
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        Set<Capability> capabilities = new HashSet<>();
        // when response format is not null, it's JSON, either application/json or text/x.enum
        if (this.responseFormat != null && ResponseFormatType.JSON.equals(this.responseFormat.type())) {
            capabilities.add(RESPONSE_FORMAT_JSON_SCHEMA);
        }
        return capabilities;
    }

    public static class GoogleAiGeminiChatModelBuilder {
        public GoogleAiGeminiChatModelBuilder toolConfig(GeminiMode mode, String... allowedFunctionNames) {
            this.toolConfig = new GeminiFunctionCallingConfig(mode, Arrays.asList(allowedFunctionNames));
            return this;
        }

        public GoogleAiGeminiChatModelBuilder safetySettings(Map<GeminiHarmCategory, GeminiHarmBlockThreshold> safetySettingMap) {
            this.safetySettings = safetySettingMap.entrySet().stream()
                .map(entry -> new GeminiSafetySetting(entry.getKey(), entry.getValue())
            ).collect(Collectors.toList());
            return this;
        }
    }
}