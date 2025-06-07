package dev.langchain4j.model.azure;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.finishReasonFrom;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsFunctionToolCall;
import com.azure.ai.openai.models.ChatCompletionsToolCall;
import com.azure.ai.openai.models.Choice;
import com.azure.ai.openai.models.Completions;
import com.azure.ai.openai.models.CompletionsFinishReason;
import com.azure.ai.openai.models.FunctionCall;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class needs to be thread safe because it is called when a streaming result comes back
 * and there is no guarantee that this thread will be the same as the one that initiated the request,
 * in fact it almost certainly won't be.
 */
class AzureOpenAiStreamingResponseBuilder {

    private final StringBuffer contentBuilder = new StringBuffer();
    private final StringBuffer toolNameBuilder = new StringBuffer();
    private final StringBuffer toolArgumentsBuilder = new StringBuffer();
    private final AtomicReference<String> responseId = new AtomicReference<>();
    private String toolExecutionsIndex = "call_undefined";
    private final Map<String, ToolExecutionRequestBuilder> toolExecutionRequestBuilderHashMap = new HashMap<>();
    private volatile CompletionsFinishReason finishReason;

    private final String modelName;
    private final Integer inputTokenCount;

    public AzureOpenAiStreamingResponseBuilder(final String modelName, Integer inputTokenCount) {
        this.modelName = modelName;
        this.inputTokenCount = inputTokenCount;
    }

    public void append(ChatCompletions completions) {
        if (completions == null) {
            return;
        }

        if (isNotNullOrBlank(completions.getId())) {
            responseId.set(completions.getId());
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

        if (delta.getFunctionCall() != null) {
            FunctionCall functionCall = delta.getFunctionCall();

            if (functionCall.getName() != null) {
                this.toolNameBuilder.append(functionCall.getName());
            }

            if (functionCall.getArguments() != null) {
                this.toolArgumentsBuilder.append(functionCall.getArguments());
            }
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
                if (toolCall instanceof ChatCompletionsFunctionToolCall) {
                    ChatCompletionsFunctionToolCall functionCall = (ChatCompletionsFunctionToolCall) toolCall;
                    if (functionCall.getFunction().getName() != null) {
                        toolExecutionRequestBuilder.nameBuilder.append(
                                functionCall.getFunction().getName());
                    }
                    if (functionCall.getFunction().getArguments() != null) {
                        toolExecutionRequestBuilder.argumentsBuilder.append(
                                functionCall.getFunction().getArguments());
                    }
                }
            }
        }
    }

    public void append(Completions completions) {
        if (completions == null) {
            return;
        }

        if (isNotNullOrBlank(completions.getId())) {
            responseId.set(completions.getId());
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

    public ChatResponse build(TokenCountEstimator tokenCountEstimator) {

        String content = contentBuilder.toString();
        TokenUsage tokenUsage =
                content.isEmpty() ? new TokenUsage(inputTokenCount, 0) : tokenUsage(content, tokenCountEstimator);

        ChatResponseMetadata chatResponseMetadata = ChatResponseMetadata.builder()
                .id(responseId.get())
                .modelName(modelName)
                .tokenUsage(tokenUsage)
                .finishReason(finishReasonFrom(finishReason))
                .build();

        String toolName = toolNameBuilder.toString();
        if (!toolName.isEmpty()) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(toolName)
                    .arguments(toolArgumentsBuilder.toString())
                    .build();
            AiMessage aiMessage = isNullOrBlank(content)
                    ? AiMessage.from(toolExecutionRequest)
                    : AiMessage.from(content, singletonList(toolExecutionRequest));
            return ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(chatResponseMetadata)
                    .build();
        }

        if (!toolExecutionRequestBuilderHashMap.isEmpty()) {
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
            return ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(chatResponseMetadata)
                    .build();
        }

        if (!content.isEmpty()) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(content))
                    .metadata(chatResponseMetadata)
                    .build();
        }

        return null;
    }

    private TokenUsage tokenUsage(String content, TokenCountEstimator tokenCountEstimator) {
        if (tokenCountEstimator == null) {
            return null;
        }
        int outputTokenCount = tokenCountEstimator.estimateTokenCountInText(content);
        return new TokenUsage(inputTokenCount, outputTokenCount);
    }

    private static class ToolExecutionRequestBuilder {

        private final StringBuffer idBuilder = new StringBuffer();
        private final StringBuffer nameBuilder = new StringBuffer();
        private final StringBuffer argumentsBuilder = new StringBuffer();
    }
}
