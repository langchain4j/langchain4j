package dev.langchain4j.model.azure;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.finishReasonFrom;

import com.azure.ai.openai.models.*;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ToolCallBuilder;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;

/**
 * This class needs to be thread safe because it is called when a streaming result comes back
 * and there is no guarantee that this thread will be the same as the one that initiated the request,
 * in fact it almost certainly won't be.
 */
@Internal
class AzureOpenAiStreamingResponseBuilder {

    private final StringBuffer contentBuilder = new StringBuffer();
    private final ToolCallBuilder toolCallBuilder;
    private volatile TokenUsage tokenUsage;
    private volatile CompletionsFinishReason finishReason;

    AzureOpenAiStreamingResponseBuilder() {
        this(null);
    }

    AzureOpenAiStreamingResponseBuilder(ToolCallBuilder toolCallBuilder) {
        this.toolCallBuilder = toolCallBuilder;
    }

    void append(ChatCompletions completions) {
        if (completions == null) {
            return;
        }

        CompletionsUsage usage = completions.getUsage();
        if (usage != null) {
            tokenUsage = new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens());
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

        CompletionsUsage usage = completions.getUsage();
        if (usage != null) {
            tokenUsage = new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens());
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

    Response<AiMessage> build() {

        String content = contentBuilder.toString();

        List<ToolExecutionRequest> toolExecutionRequests = List.of();
        if (toolCallBuilder != null) {
            toolExecutionRequests = toolCallBuilder.allRequests();
        }

        AiMessage aiMessage = AiMessage.builder()
                .text(content.isEmpty() ? null : content)
                .toolExecutionRequests(toolExecutionRequests)
                .build();

        return Response.from(aiMessage, tokenUsage, finishReasonFrom(finishReason));
    }
}
