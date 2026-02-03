package dev.langchain4j.model.mistralai.internal.client;

import static dev.langchain4j.http.client.sse.ServerSentEventParsingHandleUtils.toStreamingHandle;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.mistralai.internal.client.MistralAiJsonUtils.fromJson;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.*;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.CancellationUnsupportedHandle;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.mistralai.MistralAiChatResponseMetadata;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionChoice;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiToolCall;
import dev.langchain4j.model.mistralai.internal.api.MistralAiUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
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
    private volatile StreamingHandle streamingHandle;

    final AtomicReference<SuccessfulHttpResponse> rawHttpResponse = new AtomicReference<>();
    final Queue<ServerSentEvent> rawServerSentEvents = new ConcurrentLinkedQueue<>();

    public MistralAiServerSentEventListener(
            StreamingChatResponseHandler handler,
            BiFunction<String, List<ToolExecutionRequest>, AiMessage> toResponse) {
        this.contentBuilder = new StringBuffer();
        this.handler = handler;
        this.toResponse = toResponse;
    }

    @Override
    public void onOpen(final SuccessfulHttpResponse response) {
        rawHttpResponse.set(response);
    }

    @Override
    public void onEvent(ServerSentEvent event) {
        onEvent(event, new ServerSentEventContext(new CancellationUnsupportedHandle()));
    }

    @Override
    public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
        if (streamingHandle == null) {
            streamingHandle = toStreamingHandle(context.parsingHandle());
        }

        rawServerSentEvents.add(event);

        String data = event.data();
        if ("[DONE]".equals(data)) {
            AiMessage responseContent = toResponse.apply(contentBuilder.toString(), toolExecutionRequests);
            ChatResponse response = ChatResponse.builder()
                    .aiMessage(responseContent)
                    .metadata(createMetadata())
                    .build();
            onCompleteResponse(handler, response);
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
                onPartialResponse(handler, chunk, streamingHandle);
            }

            List<MistralAiToolCall> toolCalls = choice.getDelta().getToolCalls();
            if (!isNullOrEmpty(toolCalls)) {
                toolExecutionRequests = toToolExecutionRequests(toolCalls);

                for (int i = 0; i < toolExecutionRequests.size(); i++) {
                    CompleteToolCall completeToolCall = new CompleteToolCall(i, toolExecutionRequests.get(i));
                    onCompleteToolCall(handler, completeToolCall);
                }
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

    private MistralAiChatResponseMetadata createMetadata() {
        var metadataBuilder = MistralAiChatResponseMetadata.builder();

        metadataBuilder
                .tokenUsage(tokenUsage)
                .finishReason(finishReason)
                .modelName(modelName)
                .id(id);

        if (rawHttpResponse.get() != null) {
            metadataBuilder.rawHttpResponse(rawHttpResponse.get());
        }
        if (!rawServerSentEvents.isEmpty()) {
            metadataBuilder.rawServerSentEvents(new ArrayList<>(rawServerSentEvents));
        }

        return metadataBuilder.build();
    }

    @Override
    public void onError(Throwable error) {
        RuntimeException mappedError = ExceptionMapper.DEFAULT.mapException(error);
        withLoggingExceptions(() -> handler.onError(mappedError));
    }
}
