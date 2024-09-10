package dev.langchain4j.model.googleai;

import com.google.gson.Gson;
import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromMessageToGContent;
import static dev.langchain4j.model.googleai.FinishReasonMapper.fromGFinishReasonToFinishReason;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromGPartsToAiMessage;
import static dev.langchain4j.model.googleai.SchemaMapper.fromJsonSchemaToGSchema;
import static java.util.Collections.emptyList;

@Experimental
public class GoogleAiGeminiChatModel implements ChatLanguageModel {
    private static final String GEMINI_AI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/";

    private static Gson GSON = new Gson();

    private final String apiKey;
    private final String modelName;

    private final Double temperature;
    private final Integer topK;
    private final Double topP;
    private final Integer maxOutputTokens;
    private final List<String> stopSequences;

    private final Integer candidateCount;

    private final String responseMimeType;
    private final ResponseFormat responseFormat;

    private final GeminiFunctionCallingConfig toolConfig;

    private final Boolean logRequestsAndResponses;

    private final boolean allowCodeExecution;
    private final boolean includeCodeExecutionOutput;

    private final List<GeminiSafetySetting> safetySettings;

    @Builder
    public GoogleAiGeminiChatModel(String apiKey, String modelName,
                                   Double temperature, Integer topK, Double topP,
                                   Integer maxOutputTokens, Integer candidateCount,
                                   String responseMimeType, ResponseFormat responseFormat,
                                   List<String> stopSequences, GeminiFunctionCallingConfig toolConfig,
                                   Boolean allowCodeExecution, Boolean includeCodeExecutionOutput,
                                   Boolean logRequestsAndResponses,
                                   List<GeminiSafetySetting> safetySettings
    ) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.modelName = ensureNotBlank(modelName, "modelName");

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
        if (responseFormat != null) {
            if (responseFormat.type().equals(ResponseFormatType.JSON)) {
                this.responseMimeType = "application/json";
            } else {
                this.responseMimeType = "text/plain";
            }
        } else if (responseMimeType != null) {
            this.responseMimeType = responseMimeType;
        } else {
            this.responseMimeType = "text/plain";
        }

        this.logRequestsAndResponses = getOrDefault(logRequestsAndResponses, false);
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

        GeminiService geminiModel = getGeminiService();

        GeminiContent systemInstruction = new GeminiContent(GeminiRole.MODEL.toString());
        List<GeminiContent> geminiContentList = fromMessageToGContent(chatRequest.messages(), systemInstruction);
        List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();

        GeminiSchema schema;
        String responseMimeType = this.responseMimeType;
        if (chatRequest.responseFormat() != null) {
            schema = fromJsonSchemaToGSchema(chatRequest.responseFormat().jsonSchema());
            responseMimeType = chatRequest.responseFormat().type().equals(ResponseFormatType.JSON)
                ? "application/json"
                : "text/plain";
        } else if (this.responseFormat != null) {
            schema = fromJsonSchemaToGSchema(this.responseFormat.jsonSchema());
        } else {
            schema = null;
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

        Call<GeminiGenerateContentResponse> responseCall =
            geminiModel.generateContent(this.modelName, this.apiKey, request);

        GeminiGenerateContentResponse geminiResponse;
        try {
            retrofit2.Response<GeminiGenerateContentResponse> executed = responseCall.execute();
            geminiResponse = executed.body();

            if (executed.code() >= 300) {
                try (ResponseBody errorBody = executed.errorBody()) {
                    GeminiError error = GSON.fromJson(errorBody.string(), GeminiErrorContainer.class).getError();

                    throw new RuntimeException(
                        String.format("%s (code %d) %s", error.getStatus(), error.getCode(), error.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("An error occurred when calling the Gemini API endpoint.", e);
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

            return ChatResponse.builder()
                .aiMessage(aiMessage)
                .finishReason(finishReason)
                .tokenUsage(new TokenUsage(tokenCounts.getPromptTokenCount(),
                    tokenCounts.getCandidatesTokenCount(),
                    tokenCounts.getTotalTokenCount()))
                .build();
        } else {
            throw new RuntimeException("Gemini response was null");
        }
    }

    private GeminiService getGeminiService() {
        Retrofit.Builder retrofitBuilder = new Retrofit.Builder()
            .baseUrl(GEMINI_AI_ENDPOINT)
            .addConverterFactory(GsonConverterFactory.create());

        if (this.logRequestsAndResponses) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            retrofitBuilder.client(new OkHttpClient.Builder().addInterceptor(logging).build());
        }

        Retrofit retrofit = retrofitBuilder.build();

        return retrofit.create(GeminiService.class);
    }

    public static class GoogleAiGeminiChatModelBuilder {
        public GoogleAiGeminiChatModelBuilder toolConfig(GeminiMode mode, String... allowedFunctionNames) {
            this.toolConfig = new GeminiFunctionCallingConfig(mode, Arrays.asList(allowedFunctionNames));
            return this;
        }

        public GoogleAiGeminiChatModelBuilder responseSchema(JsonSchema schema) {
            this.responseMimeType = "application/json";
            this.responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(schema)
                .build();
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