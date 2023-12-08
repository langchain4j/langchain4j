package dev.langchain4j.model.azure;

import com.azure.ai.openai.models.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.langchain4j.model.openai.InternalOpenAiHelper.finishReasonFrom;

/**
 * This class needs to be thread safe because it is called when a streaming result comes back
 * and there is no guarantee that this thread will be the same as the one that initiated the request,
 * in fact it almost certainly won't be.
 */
class AzureOpenAiStreamingResponseBuilder {

    private final StringBuffer contentBuilder = new StringBuffer();
    private final StringBuffer toolNameBuilder = new StringBuffer();
    private final StringBuffer toolArgumentsBuilder = new StringBuffer();
    private final Integer inputTokenCount;
    private final AtomicInteger outputTokenCount = new AtomicInteger();
    private volatile String finishReason;

    public AzureOpenAiStreamingResponseBuilder(Integer inputTokenCount) {
        this.inputTokenCount = inputTokenCount;
    }

    public void append(ChatCompletions completions) {
        if (completions == null) {
            return;
        }

        List<ChatChoice> choices = completions.getChoices();
        if (choices == null || choices.isEmpty()) {
            return;
        }

        ChatChoice chatCompletionChoice = choices.get(0);
        if (chatCompletionChoice == null) {
            return;
        }

        CompletionsFinishReason finishReason = chatCompletionChoice.getFinishReason();
        if (finishReason != null) {
            this.finishReason = finishReason.toString();
        }

        com.azure.ai.openai.models.ChatMessage delta = chatCompletionChoice.getDelta();
        if (delta == null) {
            return;
        }

        String content = delta.getContent();
        if (content != null) {
            contentBuilder.append(content);
            outputTokenCount.incrementAndGet();
            return;
        }

        FunctionCall functionCall = delta.getFunctionCall();
        if (functionCall != null) {
            if (functionCall.getName() != null) {
                toolNameBuilder.append(functionCall.getName());
                outputTokenCount.incrementAndGet();
            }

            if (functionCall.getArguments() != null) {
                toolArgumentsBuilder.append(functionCall.getArguments());
                outputTokenCount.incrementAndGet();
            }
        }
    }

    public void append(Completions completions) {
        if (completions == null) {
            return;
        }

        List<Choice> choices = completions.getChoices();
        if (choices == null || choices.isEmpty()) {
            return;
        }

        Choice completionChoice = choices.get(0);
        if (completionChoice == null) {
            return;
        }

        CompletionsFinishReason completionsFinishReason = completionChoice.getFinishReason();
        if (completionsFinishReason != null) {
            this.finishReason = completionsFinishReason.toString();
        }

        String token = completionChoice.getText();
        if (token != null) {
            contentBuilder.append(token);
            outputTokenCount.incrementAndGet();
        }
    }

    public Response<AiMessage> build() {

        String content = contentBuilder.toString();
        if (!content.isEmpty()) {
            return Response.from(
                    AiMessage.from(content),
                    new TokenUsage(inputTokenCount, outputTokenCount.get()),
                    finishReasonFrom(finishReason)
            );
        }

        String toolName = toolNameBuilder.toString();
        if (!toolName.isEmpty()) {
            return Response.from(
                    AiMessage.from(ToolExecutionRequest.builder()
                            .name(toolName)
                            .arguments(toolArgumentsBuilder.toString())
                            .build()),
                    new TokenUsage(inputTokenCount, outputTokenCount.get()),
                    finishReasonFrom(finishReason)
            );
        }

        return null;
    }
}
