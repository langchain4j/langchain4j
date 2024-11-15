package dev.langchain4j.model.watsonx;

import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.cloud.sdk.core.security.IamToken;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.watsonx.internal.api.DefaultWatsonxAiClient;
import dev.langchain4j.model.watsonx.internal.api.WatsonxAiChatCompletionResponse;
import dev.langchain4j.model.watsonx.internal.api.WatsonxAiClient;
import dev.langchain4j.model.watsonx.internal.api.WatsonxChatCompletionRequest;
import dev.langchain4j.model.watsonx.internal.api.requests.WatsonxChatMessage;
import dev.langchain4j.model.watsonx.internal.api.requests.WatsonxChatMessageConverter;
import dev.langchain4j.model.watsonx.internal.api.requests.WatsonxChatParameters;
import dev.langchain4j.model.watsonx.internal.api.requests.WatsonxTextChatParameterTool;
import dev.langchain4j.model.watsonx.internal.api.requests.WatsonxTextChatToolCall;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;


public class WatsonxAiChatModel implements ChatLanguageModel {


    private final IamAuthenticator iamAuthenticator;
    private IamToken iamToken;
    private final WatsonxAiClient client;
    private final String modelId;
    private final WatsonxChatParameters parameters;
    private final String version;
    private final String projectId;
    private final Integer maxRetries;

    private static final Integer DEFAULT_MAX_RETRIES = 3;


    public WatsonxAiChatModel(String baseUrl,
                              String version,
                              String apiKey,
                              Duration timeout,
                              Boolean logRequests,
                              Boolean logResponses,
                              WatsonxChatParameters parameters,
                              String projectId,
                              String modelId,
                              Integer maxRetries) {


        this.iamAuthenticator = IamAuthenticator.fromConfiguration(Map.of("APIKEY", apiKey));
        refreshToken();

        this.client = DefaultWatsonxAiClient.builder()
            .baseUrl(getOrDefault(baseUrl, "https://api.mistral.ai/v1"))
            .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
            .token(ensureNotBlank(iamToken.getAccessToken(), "apiKey"))
            .logRequests(getOrDefault(logRequests, false))
            .logResponses(getOrDefault(logResponses, false))
            .build();

        this.version = ensureNotBlank(version, "version");
        this.modelId = ensureNotBlank(modelId, "modelId");
        this.projectId = ensureNotBlank(projectId, "projectId");
        this.parameters = parameters;
        this.maxRetries = getOrDefault(maxRetries, DEFAULT_MAX_RETRIES);
    }


    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, List.of());
    }


    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {

        ensureNotEmpty(messages, "messages");

        List<WatsonxTextChatParameterTool> tools = convertTools(toolSpecifications);

        List<WatsonxChatMessage> convertedMessages = messages.stream()
            .map(WatsonxChatMessageConverter::convert)
            .toList();

        WatsonxChatCompletionRequest request = new WatsonxChatCompletionRequest(
            modelId,
            projectId,
            convertedMessages,
            tools,
            parameters
        );

        refreshTokenIfNeeded();

        WatsonxAiChatCompletionResponse response = withRetry(
            () -> client.chatCompletion(request, version),
            maxRetries
        );

        return buildResponse(response);
    }

    private void refreshTokenIfNeeded() {
        if (this.iamToken.needsRefresh()) {
            refreshToken();
        }
    }

    private void refreshToken() {
        this.iamToken = this.iamAuthenticator.requestToken();
    }

    private FinishReason toFinishReason(String reason) {
        if (reason == null)
            return FinishReason.OTHER;

        return switch (reason) {
            case "length" -> FinishReason.LENGTH;
            case "stop" -> FinishReason.STOP;
            case "tool_calls" -> FinishReason.TOOL_EXECUTION;
            case "time_limit", "cancelled", "error" -> FinishReason.OTHER;
            default -> throw new IllegalArgumentException("%s not supported".formatted(reason));
        };
    }


    private List<WatsonxTextChatParameterTool> convertTools(List<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null || toolSpecifications.isEmpty()) {
            return null;
        }
        return toolSpecifications.stream()
            .map(WatsonxTextChatParameterTool::of)
            .toList();
    }

    private Response<AiMessage> buildResponse(WatsonxAiChatCompletionResponse response) {

        WatsonxAiChatCompletionResponse.TextChatResultChoice choice = response.choices().get(0);
        WatsonxAiChatCompletionResponse.TextChatResultMessage message = choice.message();
        WatsonxAiChatCompletionResponse.TextChatUsage usage = response.usage();

        AiMessage content = extractContent(message);
        FinishReason finishReason = toFinishReason(choice.finishReason());
        TokenUsage tokenUsage = new TokenUsage(
            usage.promptTokens(),
            usage.completionTokens(),
            usage.totalTokens()
        );

        return Response.from(content, tokenUsage, finishReason);
    }

    private AiMessage extractContent(WatsonxAiChatCompletionResponse.TextChatResultMessage message) {
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            return AiMessage.from(message.toolCalls().stream()
                .map(WatsonxTextChatToolCall::convert)
                .toList());
        }
        return AiMessage.from(message.content().trim());
    }
}
