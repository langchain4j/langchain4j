package dev.langchain4j.model.googleai;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.Tokenizer;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import retrofit2.Call;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromMessageToGContent;
import static java.util.Collections.singletonList;

@Slf4j
public class GoogleAiGeminiTokenizer implements Tokenizer {
    private static final Gson GSON = new Gson();

    private final GeminiService geminiService;
    private final String modelName;
    private final String apiKey;
    private final Integer maxRetries;

    @Builder
    GoogleAiGeminiTokenizer(
        String modelName,
        String apiKey,
        Boolean logRequestsAndResponses,
        Duration timeout,
        Integer maxRetries
    ) {
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.geminiService = GeminiService.getGeminiService(logRequestsAndResponses ? log : null,
            timeout != null ? timeout : Duration.ofSeconds(60));
    }

    @Override
    public int estimateTokenCountInText(String text) {
        return estimateTokenCountInMessages(singletonList(UserMessage.from(text)));
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        return estimateTokenCountInMessages(singletonList(message));
    }

    @Override
    public int estimateTokenCountInToolExecutionRequests(Iterable<ToolExecutionRequest> toolExecutionRequests) {
        List<ToolExecutionRequest> allToolRequests = new LinkedList<>();
        toolExecutionRequests.forEach(allToolRequests::add);

        return estimateTokenCountInMessage(AiMessage.from(allToolRequests));
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        List<ChatMessage> allMessages = new LinkedList<>();
        messages.forEach(allMessages::add);

        List<GeminiContent> geminiContentList = fromMessageToGContent(allMessages, null);

        GeminiCountTokensRequest countTokensRequest = new GeminiCountTokensRequest();
        countTokensRequest.setContents(geminiContentList);

        return estimateTokenCount(countTokensRequest);
    }

    @Override
    public int estimateTokenCountInToolSpecifications(Iterable<ToolSpecification> toolSpecifications) {
        List<ToolSpecification> allTools = new LinkedList<>();
        toolSpecifications.forEach(allTools::add);

        GeminiContent dummyContent = GeminiContent.builder().parts(
            singletonList(GeminiPart.builder()
                .text("Dummy content") // This string contains 2 tokens
                .build())
        ).build();

        GeminiCountTokensRequest countTokensRequestWithDummyContent = new GeminiCountTokensRequest();
        countTokensRequestWithDummyContent.setGenerateContentRequest(GeminiGenerateContentRequest.builder()
            .model("models/" + this.modelName)
            .contents(singletonList(dummyContent))
            .tools(FunctionMapper.fromToolSepcsToGTool(allTools, false))
            .build());

        // The API doesn't allow us to make a request to count the tokens of the tool specifications only.
        // Instead, we take the approach of adding a dummy content in the request, and subtract the tokens for the dummy request.
        // The string "Dummy content" accounts for 2 tokens. So let's subtract 2 from the overall count.
        return estimateTokenCount(countTokensRequestWithDummyContent) - 2;
    }

    private int estimateTokenCount(GeminiCountTokensRequest countTokensRequest) {
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
}
