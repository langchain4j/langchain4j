package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.chat.ChatCompletionChoice;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.chat.Delta;
import dev.ai4j.openai4j.chat.FunctionCall;
import dev.ai4j.openai4j.completion.CompletionChoice;
import dev.ai4j.openai4j.completion.CompletionResponse;
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
public class OpenAiStreamingResponseBuilder {

    private final StringBuffer contentBuilder = new StringBuffer();
    private final StringBuffer toolNameBuilder = new StringBuffer();
    private final StringBuffer toolArgumentsBuilder = new StringBuffer();

    private final Integer inputTokenCount;
    private final AtomicInteger outputTokenCount = new AtomicInteger();

    private volatile String finishReason;

    public OpenAiStreamingResponseBuilder(Integer inputTokenCount) {
        this.inputTokenCount = inputTokenCount;
    }

    public void append(ChatCompletionResponse partialResponse) {
        if (partialResponse == null) {
            return;
        }

        List<ChatCompletionChoice> choices = partialResponse.choices();
        if (choices == null || choices.isEmpty()) {
            return;
        }

        ChatCompletionChoice chatCompletionChoice = choices.get(0);
        if (chatCompletionChoice == null) {
            return;
        }

        String finishReason = chatCompletionChoice.finishReason();
        if (finishReason != null) {
            this.finishReason = finishReason;
        }

        Delta delta = chatCompletionChoice.delta();
        if (delta == null) {
            return;
        }

        String content = delta.content();
        if (content != null) {
            contentBuilder.append(content);
            outputTokenCount.incrementAndGet();
            return;
        }

        FunctionCall functionCall = delta.functionCall();
        if (functionCall != null) {
            if (functionCall.name() != null) {
                toolNameBuilder.append(functionCall.name());
                outputTokenCount.incrementAndGet();
            }

            if (functionCall.arguments() != null) {
                toolArgumentsBuilder.append(functionCall.arguments());
                outputTokenCount.incrementAndGet();
            }
        }
    }

    public void append(CompletionResponse partialResponse) {
        if (partialResponse == null) {
            return;
        }

        List<CompletionChoice> choices = partialResponse.choices();
        if (choices == null || choices.isEmpty()) {
            return;
        }

        CompletionChoice completionChoice = choices.get(0);
        if (completionChoice == null) {
            return;
        }

        String finishReason = completionChoice.finishReason();
        if (finishReason != null) {
            this.finishReason = finishReason;
        }

        String token = completionChoice.text();
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
