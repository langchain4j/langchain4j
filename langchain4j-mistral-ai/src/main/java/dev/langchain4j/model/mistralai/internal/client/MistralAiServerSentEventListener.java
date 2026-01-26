package dev.langchain4j.model.mistralai.internal.client;

import static dev.langchain4j.http.client.sse.ServerSentEventParsingHandleUtils.toStreamingHandle;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.mistralai.internal.client.MistralAiJsonUtils.fromJson;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.*;
import static java.util.Collections.emptyList;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.sse.CancellationUnsupportedHandle;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionChoice;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiMessageContent;
import dev.langchain4j.model.mistralai.internal.api.MistralAiTextContent;
import dev.langchain4j.model.mistralai.internal.api.MistralAiThinkingContent;
import dev.langchain4j.model.mistralai.internal.api.MistralAiToolCall;
import dev.langchain4j.model.mistralai.internal.api.MistralAiUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;

@Internal
class MistralAiServerSentEventListener implements ServerSentEventListener {

    private final StringBuffer contentBuilder;

    private final StringBuffer thinkingBuilder;
    private final boolean returnThinking;

    private final StreamingChatResponseHandler handler;

    private List<ToolExecutionRequest> toolExecutionRequests;
    private TokenUsage tokenUsage;
    private FinishReason finishReason;

    private String modelName;
    private String id;
    private volatile StreamingHandle streamingHandle;

    public MistralAiServerSentEventListener(StreamingChatResponseHandler handler, boolean returnThinking) {
        this.contentBuilder = new StringBuffer();
        this.thinkingBuilder = returnThinking ? new StringBuffer() : null;
        this.returnThinking = returnThinking;
        this.handler = handler;
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

        String data = event.data();
        if ("[DONE]".equals(data)) {
            AiMessage.Builder responseBuilder = AiMessage.builder();
            if (isNotNullOrEmpty(toolExecutionRequests)) {
                responseBuilder.toolExecutionRequests(toolExecutionRequests);
            } else {
                responseBuilder.text(contentBuilder.toString());
            }
            if (returnThinking && !thinkingBuilder.isEmpty()) {
                responseBuilder.thinking(thinkingBuilder.toString());
            }

            ChatResponse response = ChatResponse.builder()
                    .aiMessage(responseBuilder.build())
                    .metadata(ChatResponseMetadata.builder()
                            .tokenUsage(tokenUsage)
                            .finishReason(finishReason)
                            .modelName(modelName)
                            .id(id)
                            .build())
                    .build();
            onCompleteResponse(handler, response);
        } else {
            MistralAiChatCompletionResponse chatCompletionResponse =
                    fromJson(data, MistralAiChatCompletionResponse.class);
            MistralAiChatCompletionChoice choice =
                    chatCompletionResponse.getChoices().get(0);

            this.modelName = chatCompletionResponse.getModel();
            this.id = chatCompletionResponse.getId();

            List<MistralAiMessageContent> chunks = choice.getDelta().getContent();
            if (isNotNullOrEmpty(chunks)) {
                for (var chunk : chunks) {
                    if (returnThinking && chunk instanceof MistralAiThinkingContent thinkingContent) {
                        List<String> thinkingChunks = getThinkingChunks(thinkingContent);
                        for (String thinkingChunk : thinkingChunks) {
                            thinkingBuilder.append(thinkingChunk);
                            onPartialThinking(handler, thinkingChunk, streamingHandle);
                        }
                    }
                    if (chunk instanceof MistralAiTextContent textContent) {
                        String text = textContent.getText();
                        if (isNotNullOrEmpty(text)) {
                            contentBuilder.append(text);
                            onPartialResponse(handler, text, streamingHandle);
                        }
                    }
                }
            }

            List<MistralAiToolCall> toolCalls = choice.getDelta().getToolCalls();
            if (isNotNullOrEmpty(toolCalls)) {
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

    private static List<String> getThinkingChunks(MistralAiThinkingContent thinkingContent) {
        if (thinkingContent == null) {
            return emptyList();
        }
        if (isNullOrEmpty(thinkingContent.getThinking())) {
            return emptyList();
        }
        List<String> thinkingChunks = new ArrayList<>(1);
        for (MistralAiTextContent thinkingTextContent : thinkingContent.getThinking()) {
            String thinkingText = thinkingTextContent.getText();
            if (isNotNullOrEmpty(thinkingText)) {
                thinkingChunks.add(thinkingText);
            }
        }
        return thinkingChunks;
    }

    @Override
    public void onError(Throwable error) {
        RuntimeException mappedError = ExceptionMapper.DEFAULT.mapException(error);
        withLoggingExceptions(() -> handler.onError(mappedError));
    }
}
