package dev.langchain4j.model.azure;

import com.azure.ai.openai.models.*;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ToolCallBuilder;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.finishReasonFrom;

/**
 * This class needs to be thread safe because it is called when a streaming result comes back
 * and there is no guarantee that this thread will be the same as the one that initiated the request,
 * in fact it almost certainly won't be.
 */
@Internal
class InternalAzureOpenAiStreamingResponseBuilder {

    private final Integer inputTokenCount;
    private final StringBuffer contentBuilder = new StringBuffer();
    private final ToolCallBuilder toolCallBuilder;
    private volatile CompletionsFinishReason finishReason;

    InternalAzureOpenAiStreamingResponseBuilder(Integer inputTokenCount, ToolCallBuilder toolCallBuilder) {
        this.inputTokenCount = inputTokenCount;
        this.toolCallBuilder = toolCallBuilder;
    }

    void append(ChatCompletions completions) {
        if (completions == null) {
            return;
        }

        List<ChatChoice> choices = completions.getChoices();
        if (isNullOrEmpty(choices)) {
            return;
        }

        ChatChoice chatCompletionChoice = choices.get(0);
        if (chatCompletionChoice == null) {
            return;
        }

        CompletionsFinishReason finishReason = chatCompletionChoice.getFinishReason();
        if (finishReason != null) {
            this.finishReason = finishReason;
        }

        com.azure.ai.openai.models.ChatResponseMessage delta = chatCompletionChoice.getDelta();
        if (delta == null) {
            return;
        }

        String content = delta.getContent();
        if (content != null) {
            contentBuilder.append(content);
        }
    }

    void append(Completions completions) {
        if (completions == null) {
            return;
        }

        List<Choice> choices = completions.getChoices();
        if (isNullOrEmpty(choices)) {
            return;
        }

        Choice completionChoice = choices.get(0);
        if (completionChoice == null) {
            return;
        }

        CompletionsFinishReason completionsFinishReason = completionChoice.getFinishReason();
        if (completionsFinishReason != null) {
            this.finishReason = completionsFinishReason;
        }

        String token = completionChoice.getText();
        if (token != null) {
            contentBuilder.append(token);
        }
    }

    Response<AiMessage> build(TokenCountEstimator tokenCountEstimator) {

        String content = contentBuilder.toString();

        List<ToolExecutionRequest> toolExecutionRequests = List.of();
        if (toolCallBuilder != null) {
            toolExecutionRequests = toolCallBuilder.allRequests();
        }

        AiMessage aiMessage = AiMessage.builder()
                .text(content.isEmpty() ? null : content)
                .toolExecutionRequests(toolExecutionRequests)
                .build();

        TokenUsage tokenUsage = null;
        if (tokenCountEstimator != null) {
            int outputTokenCount = tokenCountEstimator.estimateTokenCountInMessage(aiMessage);
            tokenUsage = new TokenUsage(inputTokenCount, outputTokenCount);
        }

        return Response.from(aiMessage, tokenUsage, finishReasonFrom(finishReason));
    }
}
