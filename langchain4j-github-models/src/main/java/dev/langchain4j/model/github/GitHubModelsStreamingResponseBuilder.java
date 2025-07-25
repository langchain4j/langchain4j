package dev.langchain4j.model.github;

import com.azure.ai.inference.models.CompletionsFinishReason;
import com.azure.ai.inference.models.StreamingChatChoiceUpdate;
import com.azure.ai.inference.models.StreamingChatCompletionsUpdate;
import com.azure.ai.inference.models.StreamingChatResponseMessageUpdate;
import com.azure.ai.inference.models.StreamingChatResponseToolCallUpdate;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.github.InternalGitHubModelHelper.finishReasonFrom;
import static java.util.stream.Collectors.toList;

/**
 * This class needs to be thread safe because it is called when a streaming result comes back
 * and there is no guarantee that this thread will be the same as the one that initiated the request,
 * in fact it almost certainly won't be.
 */
class GitHubModelsStreamingResponseBuilder {

    private final StringBuffer contentBuilder = new StringBuffer();
    private int inputTokenCount = 0;
    private int outputTokenCount = 0;
    private String toolExecutionsIndex = "call_undefined";
    private final Map<String, ToolExecutionRequestBuilder> toolExecutionRequestBuilderHashMap = new HashMap<>();
    private volatile CompletionsFinishReason azureFinishReason;

    public GitHubModelsStreamingResponseBuilder() {
    }

    public void append(StreamingChatCompletionsUpdate streamingChatCompletionsUpdate) {
        if (streamingChatCompletionsUpdate == null) {
            return;
        }
        if (streamingChatCompletionsUpdate.getUsage() != null) {
            inputTokenCount = streamingChatCompletionsUpdate.getUsage().getPromptTokens();
            outputTokenCount = streamingChatCompletionsUpdate.getUsage().getCompletionTokens();
        }

        List<StreamingChatChoiceUpdate> choices = streamingChatCompletionsUpdate.getChoices();
        if (isNullOrEmpty(choices)) {
            return;
        }

        StreamingChatChoiceUpdate chatCompletionChoice = choices.get(0);
        if (chatCompletionChoice == null) {
            return;
        }

        CompletionsFinishReason finishReason = chatCompletionChoice.getFinishReason();
        if (finishReason != null) {
            this.azureFinishReason = finishReason;
        }

        StreamingChatResponseMessageUpdate delta = chatCompletionChoice.getDelta();
        if (delta == null) {
            return;
        }

        String content = delta.getContent();
        if (content != null) {
            contentBuilder.append(content);
            return;
        }

        if (delta.getToolCalls() != null && !delta.getToolCalls().isEmpty()) {
            for (StreamingChatResponseToolCallUpdate toolCall : delta.getToolCalls()) {
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
                if (toolCall.getFunction().getName() != null) {
                    toolExecutionRequestBuilder.nameBuilder.append(toolCall.getFunction().getName());
                }
                if (toolCall.getFunction().getArguments() != null) {
                    toolExecutionRequestBuilder.argumentsBuilder.append(toolCall.getFunction().getArguments());
                }
            }
        }
    }

    public Response<AiMessage> build() {
        String content = contentBuilder.toString();
        TokenUsage tokenUsage = new TokenUsage(inputTokenCount, outputTokenCount);
        FinishReason finishReason = finishReasonFrom(azureFinishReason);

        if (toolExecutionRequestBuilderHashMap.isEmpty()) {

            if (isNullOrBlank(content)) {
                return null;
            } else {
                return Response.from(
                        AiMessage.from(content),
                        tokenUsage,
                        finishReason
                );
            }
        } else {
            List<ToolExecutionRequest> toolExecutionRequests = toolExecutionRequestBuilderHashMap.values().stream()
                    .map(it -> ToolExecutionRequest.builder()
                            .id(it.idBuilder.toString())
                            .name(it.nameBuilder.toString())
                            .arguments(it.argumentsBuilder.toString())
                            .build())
                    .collect(toList());

            AiMessage aiMessage = isNullOrBlank(content)
                    ? AiMessage.from(toolExecutionRequests)
                    : AiMessage.from(content, toolExecutionRequests);

            return Response.from(
                    aiMessage,
                    tokenUsage,
                    finishReason
            );
        }
    }

    private static class ToolExecutionRequestBuilder {

        private final StringBuffer idBuilder = new StringBuffer();
        private final StringBuffer nameBuilder = new StringBuffer();
        private final StringBuffer argumentsBuilder = new StringBuffer();
    }
}
