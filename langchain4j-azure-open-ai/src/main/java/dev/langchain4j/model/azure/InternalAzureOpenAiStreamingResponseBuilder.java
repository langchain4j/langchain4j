package dev.langchain4j.model.azure;

import com.azure.ai.openai.models.*;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.finishReasonFrom;
import static java.util.stream.Collectors.toList;

/**
 * This class needs to be thread safe because it is called when a streaming result comes back
 * and there is no guarantee that this thread will be the same as the one that initiated the request,
 * in fact it almost certainly won't be.
 */
@Internal
class InternalAzureOpenAiStreamingResponseBuilder {

    private final Integer inputTokenCount;

    private final StringBuffer contentBuilder = new StringBuffer();

    private String toolExecutionsIndex = "call_undefined";
    private final Map<String, ToolExecutionRequestBuilder> toolExecutionRequestBuilderHashMap = new HashMap<>();

    private volatile CompletionsFinishReason finishReason;

    InternalAzureOpenAiStreamingResponseBuilder(Integer inputTokenCount) {
        this.inputTokenCount = inputTokenCount;
    }

    void append(ChatCompletions completions) {
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

        if (delta.getToolCalls() != null && !delta.getToolCalls().isEmpty()) {
            for (ChatCompletionsToolCall toolCall : delta.getToolCalls()) {
                ToolExecutionRequestBuilder toolExecutionRequestBuilder;
                if (toolCall.getId() != null) {
                    toolExecutionsIndex = toolCall.getId();
                    toolExecutionRequestBuilder = new ToolExecutionRequestBuilder();
                    toolExecutionRequestBuilder.idBuilder.append(toolExecutionsIndex);
                    toolExecutionRequestBuilderHashMap.put(toolExecutionsIndex, toolExecutionRequestBuilder);
                } else {
                    toolExecutionRequestBuilder = toolExecutionRequestBuilderHashMap.get(toolExecutionsIndex);
                    if (toolExecutionRequestBuilder == null) {
                        throw new IllegalStateException("Function without an id defined in the tool call");
                    }
                }
                if (toolCall instanceof ChatCompletionsFunctionToolCall functionCall) {
                    if (functionCall.getFunction().getName() != null) {
                        toolExecutionRequestBuilder.nameBuilder.append(functionCall.getFunction().getName());
                    }
                    if (functionCall.getFunction().getArguments() != null) {
                        toolExecutionRequestBuilder.argumentsBuilder.append(functionCall.getFunction().getArguments());
                    }
                }
            }
        }
    }

    void append(Completions completions) {
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

    Response<AiMessage> build(TokenCountEstimator tokenCountEstimator) {

        String content = contentBuilder.toString();

        List<ToolExecutionRequest> toolExecutionRequests = null;
        if (!toolExecutionRequestBuilderHashMap.isEmpty()) {
            toolExecutionRequests = toolExecutionRequestBuilderHashMap.values().stream()
                    .map(it -> ToolExecutionRequest.builder()
                            .id(it.idBuilder.toString())
                            .name(it.nameBuilder.toString())
                            .arguments(it.argumentsBuilder.toString())
                            .build())
                    .collect(toList());

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

    private static class ToolExecutionRequestBuilder {

        private final StringBuffer idBuilder = new StringBuffer();
        private final StringBuffer nameBuilder = new StringBuffer();
        private final StringBuffer argumentsBuilder = new StringBuffer();
    }
}
