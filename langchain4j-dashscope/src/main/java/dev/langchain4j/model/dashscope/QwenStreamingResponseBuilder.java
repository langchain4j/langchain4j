package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.GenerationUsage;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationUsage;
import com.alibaba.dashscope.tools.ToolCallBase;
import com.alibaba.dashscope.tools.ToolCallFunction;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.model.dashscope.QwenHelper.*;
import static java.util.stream.Collectors.toList;

public class QwenStreamingResponseBuilder {
    private final StringBuilder generatedContent = new StringBuilder();
    private final Map<Integer, ToolExecutionRequestBuilder> indexToToolExecutionRequestBuilder = new ConcurrentHashMap<>();
    private Integer inputTokenCount;
    private Integer outputTokenCount;
    private FinishReason finishReason;

    public QwenStreamingResponseBuilder() {}

    public String append(GenerationResult partialResponse) {
        if (partialResponse == null) {
            return null;
        }

        GenerationUsage usage = partialResponse.getUsage();
        if (usage != null) {
            inputTokenCount = usage.getInputTokens();
            outputTokenCount = usage.getOutputTokens();
        }

        FinishReason finishReason = finishReasonFrom(partialResponse);
        if (finishReason != null) {
            this.finishReason = finishReason;
        }

        if (hasAnswer(partialResponse)) {
            String partialContent = answerFrom(partialResponse);
            generatedContent.append(partialContent);
            return partialContent;
        } else if (isFunctionToolCalls(partialResponse)) {
            List<ToolCallBase> toolCalls = toolCallsFrom(partialResponse);
            for (int index = 0; index < toolCalls.size(); index++) {
                // It looks like the index of the list matches the 'index' property in the response,
                // which can't be directly accessed by java sdk.
                if (toolCalls.get(index) instanceof ToolCallFunction toolCall) {
                    ToolExecutionRequestBuilder toolExecutionRequestBuilder
                            = indexToToolExecutionRequestBuilder.computeIfAbsent(index, idx -> new ToolExecutionRequestBuilder());
                    if (toolCall.getId() != null) {
                        toolExecutionRequestBuilder.idBuilder.append(toolCall.getId());
                    }

                    ToolCallFunction.CallFunction functionCall = toolCall.getFunction();

                    if (functionCall.getName() != null) {
                        toolExecutionRequestBuilder.nameBuilder.append(functionCall.getName());
                    }

                    if (functionCall.getArguments() != null) {
                        toolExecutionRequestBuilder.argumentsBuilder.append(functionCall.getArguments());
                    }
                }
            }
        }

        return null;
    }

    public String append(MultiModalConversationResult partialResponse) {
        if (partialResponse == null) {
            return null;
        }

        MultiModalConversationUsage usage = partialResponse.getUsage();
        if (usage != null) {
            inputTokenCount = usage.getInputTokens();
            outputTokenCount = usage.getOutputTokens();
        }

        FinishReason finishReason = finishReasonFrom(partialResponse);
        if (finishReason != null) {
            this.finishReason = finishReason;
        }

        if (hasAnswer(partialResponse)) {
            String partialContent = answerFrom(partialResponse);
            generatedContent.append(partialContent);
            return partialContent;
        }

        return null;
    }

    public Response<AiMessage> build() {
        String text = generatedContent.toString();

        if (!indexToToolExecutionRequestBuilder.isEmpty()) {
            List<ToolExecutionRequest> toolExecutionRequests = indexToToolExecutionRequestBuilder.values().stream()
                    .map(it -> ToolExecutionRequest.builder()
                            .id(it.idBuilder.toString())
                            .name(it.nameBuilder.toString())
                            .arguments(it.argumentsBuilder.toString())
                            .build())
                    .collect(toList());

            AiMessage aiMessage = isNullOrBlank(text) ?
                    AiMessage.from(toolExecutionRequests) :
                    AiMessage.from(text, toolExecutionRequests);

            return Response.from(
                    aiMessage,
                    new TokenUsage(inputTokenCount, outputTokenCount),
                    finishReason
            );
        }

        if (!isNullOrBlank(text)) {
            return Response.from(
                    AiMessage.from(text),
                    new TokenUsage(inputTokenCount, outputTokenCount),
                    finishReason
            );
        }

        return null;
    }

    private static class ToolExecutionRequestBuilder {

        private final StringBuffer idBuilder = new StringBuffer();
        private final StringBuffer nameBuilder = new StringBuffer();
        private final StringBuffer argumentsBuilder = new StringBuffer();
    }
}
