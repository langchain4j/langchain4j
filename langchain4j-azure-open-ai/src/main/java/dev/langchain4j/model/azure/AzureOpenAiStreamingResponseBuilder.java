package dev.langchain4j.model.azure;

import com.azure.ai.openai.models.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;

import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.finishReasonFrom;
import static java.util.Collections.singletonList;

/**
 * This class needs to be thread safe because it is called when a streaming result comes back
 * and there is no guarantee that this thread will be the same as the one that initiated the request,
 * in fact it almost certainly won't be.
 */
class AzureOpenAiStreamingResponseBuilder {

    private final StringBuffer contentBuilder = new StringBuffer();
    private final StringBuffer toolNameBuilder = new StringBuffer();
    private final StringBuffer toolArgumentsBuilder = new StringBuffer();
    private volatile CompletionsFinishReason finishReason;

    private final Integer inputTokenCount;

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
            this.finishReason = finishReason;
        }

        com.azure.ai.openai.models.ChatResponseMessage delta = chatCompletionChoice.getDelta();
        if (delta == null) {
            return;
        }

        String content = delta.getContent();
        if (content != null) {
            contentBuilder.append(content);
            return;
        }

        FunctionCall functionCall = delta.getFunctionCall();
        if (functionCall != null) {
            if (functionCall.getName() != null) {
                toolNameBuilder.append(functionCall.getName());
            }

            if (functionCall.getArguments() != null) {
                toolArgumentsBuilder.append(functionCall.getArguments());
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
            this.finishReason = completionsFinishReason;
        }

        String token = completionChoice.getText();
        if (token != null) {
            contentBuilder.append(token);
        }
    }

    public Response<AiMessage> build(Tokenizer tokenizer, boolean forcefulToolExecution) {

        String content = contentBuilder.toString();
        if (!content.isEmpty()) {
            return Response.from(
                    AiMessage.from(content),
                    tokenUsage(content, tokenizer),
                    finishReasonFrom(finishReason)
            );
        }

        String toolName = toolNameBuilder.toString();
        if (!toolName.isEmpty()) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(toolName)
                    .arguments(toolArgumentsBuilder.toString())
                    .build();
            return Response.from(
                    AiMessage.from(toolExecutionRequest),
                    tokenUsage(toolExecutionRequest, tokenizer, forcefulToolExecution),
                    finishReasonFrom(finishReason)
            );
        }

        return null;
    }

    private TokenUsage tokenUsage(String content, Tokenizer tokenizer) {
        if (tokenizer == null) {
            return null;
        }
        int outputTokenCount = tokenizer.estimateTokenCountInText(content);
        return new TokenUsage(inputTokenCount, outputTokenCount);
    }

    private TokenUsage tokenUsage(ToolExecutionRequest toolExecutionRequest, Tokenizer tokenizer, boolean forcefulToolExecution) {
        if (tokenizer == null) {
            return null;
        }

        int outputTokenCount = 0;
        if (forcefulToolExecution) {
            // OpenAI calculates output tokens differently when tool is executed forcefully
            outputTokenCount += tokenizer.estimateTokenCountInForcefulToolExecutionRequest(toolExecutionRequest);
        } else {
            outputTokenCount = tokenizer.estimateTokenCountInToolExecutionRequests(singletonList(toolExecutionRequest));
        }

        return new TokenUsage(inputTokenCount, outputTokenCount);
    }
}
