package dev.langchain4j.model.openai;

import dev.langchain4j.Internal;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.Delta;
import dev.langchain4j.model.openai.internal.chat.FunctionCall;
import dev.langchain4j.model.openai.internal.chat.ToolCall;
import dev.langchain4j.model.openai.internal.completion.CompletionChoice;
import dev.langchain4j.model.openai.internal.completion.CompletionResponse;
import dev.langchain4j.model.openai.internal.shared.Usage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.finishReasonFrom;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.tokenUsageFrom;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * This class needs to be thread safe because it is called when a streaming result comes back
 * and there is no guarantee that this thread will be the same as the one that initiated the request,
 * in fact it almost certainly won't be.
 */
@Internal
public class OpenAiStreamingResponseBuilder {

    private final StringBuffer contentBuilder = new StringBuffer();

    private final StringBuffer toolNameBuilder = new StringBuffer();
    private final StringBuffer toolArgumentsBuilder = new StringBuffer();

    private final Map<Integer, ToolExecutionRequestBuilder> indexToToolExecutionRequestBuilder = new ConcurrentHashMap<>();

    private final AtomicReference<String> id = new AtomicReference<>();
    private final AtomicReference<Long> created = new AtomicReference<>();
    private final AtomicReference<String> model = new AtomicReference<>();
    private final AtomicReference<String> serviceTier = new AtomicReference<>();
    private final AtomicReference<String> systemFingerprint = new AtomicReference<>();
    private final AtomicReference<TokenUsage> tokenUsage = new AtomicReference<>();
    private final AtomicReference<FinishReason> finishReason = new AtomicReference<>();

    public void append(ChatCompletionResponse partialResponse) {
        if (partialResponse == null) {
            return;
        }

        if (!isNullOrBlank(partialResponse.id())) {
            this.id.set(partialResponse.id());
        }
        if (partialResponse.created() != null) {
            this.created.set(partialResponse.created());
        }
        if (!isNullOrBlank(partialResponse.model())) {
            this.model.set(partialResponse.model());
        }
        if (!isNullOrBlank(partialResponse.serviceTier())) {
            this.serviceTier.set(partialResponse.serviceTier());
        }
        if (!isNullOrBlank(partialResponse.systemFingerprint())) {
            this.systemFingerprint.set(partialResponse.systemFingerprint());
        }

        Usage usage = partialResponse.usage();
        if (usage != null) {
            this.tokenUsage.set(tokenUsageFrom(usage));
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
            this.finishReason.set(finishReasonFrom(finishReason));
        }

        Delta delta = chatCompletionChoice.delta();
        if (delta == null) {
            return;
        }

        String content = delta.content();
        if (!isNullOrEmpty(content)) {
            this.contentBuilder.append(content);
        }

        if (delta.functionCall() != null) {
            FunctionCall functionCall = delta.functionCall();

            if (functionCall.name() != null) {
                this.toolNameBuilder.append(functionCall.name());
            }

            if (functionCall.arguments() != null) {
                this.toolArgumentsBuilder.append(functionCall.arguments());
            }
        }

        if (delta.toolCalls() != null && !delta.toolCalls().isEmpty()) {
            ToolCall toolCall = delta.toolCalls().get(0);

            ToolExecutionRequestBuilder builder = this.indexToToolExecutionRequestBuilder.computeIfAbsent(
                    toolCall.index(),
                    idx -> new ToolExecutionRequestBuilder()
            );

            if (toolCall.id() != null) {
                builder.idBuilder.append(toolCall.id());
            }

            FunctionCall functionCall = toolCall.function();
            if (functionCall.name() != null) {
                builder.nameBuilder.append(functionCall.name());
            }
            if (functionCall.arguments() != null) {
                builder.argumentsBuilder.append(functionCall.arguments());
            }
        }
    }

    public void append(CompletionResponse partialResponse) {
        if (partialResponse == null) {
            return;
        }

        Usage usage = partialResponse.usage();
        if (usage != null) {
            this.tokenUsage.set(tokenUsageFrom(usage));
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
            this.finishReason.set(finishReasonFrom(finishReason));
        }

        String token = completionChoice.text();
        if (token != null) {
            this.contentBuilder.append(token);
        }
    }

    public ChatResponse build() {

        OpenAiChatResponseMetadata chatResponseMetadata = OpenAiChatResponseMetadata.builder()
                .id(id.get())
                .modelName(model.get())
                .tokenUsage(tokenUsage.get())
                .finishReason(finishReason.get())
                .created(created.get())
                .serviceTier(serviceTier.get())
                .systemFingerprint(systemFingerprint.get())
                .build();

        String text = contentBuilder.toString();

        String toolName = toolNameBuilder.toString();
        if (!toolName.isEmpty()) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(toolName)
                    .arguments(toolArgumentsBuilder.toString())
                    .build();

            AiMessage aiMessage = isNullOrBlank(text) ?
                    AiMessage.from(toolExecutionRequest) :
                    AiMessage.from(text, singletonList(toolExecutionRequest));

            return ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(chatResponseMetadata)
                    .build();
        }

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

            return ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(chatResponseMetadata)
                    .build();
        }

        if (!isNullOrBlank(text)) {
            AiMessage aiMessage = AiMessage.from(text);
            return ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(chatResponseMetadata)
                    .build();
        }

        return null;
    }

    private static class ToolExecutionRequestBuilder {

        private final StringBuffer idBuilder = new StringBuffer();
        private final StringBuffer nameBuilder = new StringBuffer();
        private final StringBuffer argumentsBuilder = new StringBuffer();
    }
}
