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

import static dev.langchain4j.model.openai.InternalOpenAiHelper.finishReasonFrom;

public class OpenAiStreamingResponseBuilder {

    private final StringBuilder contentBuilder = new StringBuilder();
    private final StringBuilder toolNameBuilder = new StringBuilder();
    private final StringBuilder toolArgumentsBuilder = new StringBuilder();

    private final Integer inputTokenCount;
    private int outputTokenCount;

    private String finishReason;

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
            outputTokenCount++;
            return;
        }

        FunctionCall functionCall = delta.functionCall();
        if (functionCall != null) {
            if (functionCall.name() != null) {
                toolNameBuilder.append(functionCall.name());
                outputTokenCount++;
            }

            if (functionCall.arguments() != null) {
                toolArgumentsBuilder.append(functionCall.arguments());
                outputTokenCount++;
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
            outputTokenCount++;
        }
    }

    public Response<AiMessage> build() {

        String content = contentBuilder.toString();
        if (!content.isEmpty()) {
            return Response.from(
                    AiMessage.from(content),
                    new TokenUsage(inputTokenCount, outputTokenCount),
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
                    new TokenUsage(inputTokenCount, outputTokenCount),
                    finishReasonFrom(finishReason)
            );
        }

        return null;
    }
}