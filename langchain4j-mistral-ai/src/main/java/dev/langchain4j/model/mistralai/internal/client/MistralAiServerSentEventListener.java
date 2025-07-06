package dev.langchain4j.model.mistralai.internal.client;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.mistralai.internal.client.MistralAiJsonUtils.fromJson;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.*;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionChoice;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiToolCall;
import dev.langchain4j.model.mistralai.internal.api.MistralAiUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.function.BiFunction;

@Internal
class MistralAiServerSentEventListener implements ServerSentEventListener {

    private final StringBuffer contentBuilder;
    private final StreamingChatResponseHandler handler;
    private final BiFunction<String, List<ToolExecutionRequest>, AiMessage> toResponse;

    private List<ToolExecutionRequest> toolExecutionRequests;
    private TokenUsage tokenUsage;
    private FinishReason finishReason;

    private String modelName;
    private String id;

    public MistralAiServerSentEventListener(
            StreamingChatResponseHandler handler, BiFunction<String, List<ToolExecutionRequest>, AiMessage> toResponse) {
        this.contentBuilder = new StringBuffer();
        this.handler = handler;
        this.toResponse = toResponse;
    }

    @Override
    public void onEvent(ServerSentEvent event) {
        String data = event.data();
        if ("[DONE]".equals(data)) {
            AiMessage responseContent = toResponse.apply(contentBuilder.toString(), toolExecutionRequests);
            ChatResponse response = ChatResponse.builder()
                    .aiMessage(responseContent)
                    .metadata(ChatResponseMetadata.builder()
                            .tokenUsage(tokenUsage)
                            .finishReason(finishReason)
                            .modelName(modelName)
                            .id(id)
                            .build())
                    .build();
            try {
                handler.onCompleteResponse(response);
            } catch (Exception e) {
                withLoggingExceptions(() -> handler.onError(e));
            }
        } else {
            MistralAiChatCompletionResponse chatCompletionResponse =
                    fromJson(data, MistralAiChatCompletionResponse.class);
            MistralAiChatCompletionChoice choice =
                    chatCompletionResponse.getChoices().get(0);

            this.modelName = chatCompletionResponse.getModel();
            this.id = chatCompletionResponse.getId();

            String chunk = choice.getDelta().getContent();
            if (isNotNullOrEmpty(chunk)) {
                contentBuilder.append(chunk);
                try {
                    handler.onPartialResponse(chunk);
                } catch (Exception e) {
                    withLoggingExceptions(() -> handler.onError(e));
                }
            }

            List<MistralAiToolCall> toolCalls = choice.getDelta().getToolCalls();
            if (!isNullOrEmpty(toolCalls)) {
                toolExecutionRequests = toToolExecutionRequests(toolCalls);
            }

            MistralAiUsage usageInfo = chatCompletionResponse.getUsage();
            if (usageInfo != null) {
                this.tokenUsage = tokenUsageFrom(usageInfo);
            }

            String finishReasonString = choice.getFinishReason();
            if (finishReasonString != null) {
                this.finishReason = finishReasonFrom(finishReasonString);
            }
        }
    }

    @Override
    public void onError(Throwable error) {
        RuntimeException mappedError = ExceptionMapper.DEFAULT.mapException(error);
        withLoggingExceptions(() -> handler.onError(mappedError));
    }
}
