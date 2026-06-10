package dev.langchain4j.model.openai.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.internal.ToolCallBuilder;
import dev.langchain4j.model.chat.response.DefaultRawStreamingEvent;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.Delta;
import dev.langchain4j.model.openai.internal.chat.ToolCall;

import java.util.List;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialToolCall;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

@Internal
public final class ChatCompletionEventDispatcher {

    private ChatCompletionEventDispatcher() {}

    public static void handle(
            ParsedAndRawResponse<ChatCompletionResponse> parsedAndRawResponse,
            ToolCallBuilder toolCallBuilder,
            StreamingChatResponseHandler handler,
            boolean returnThinking) {

        boolean dispatched = dispatchTyped(parsedAndRawResponse, toolCallBuilder, handler, returnThinking);

        if (!dispatched) {
            ServerSentEvent rawServerSentEvent = parsedAndRawResponse.rawServerSentEvent();
            if (rawServerSentEvent != null) {
                // TODO type
                handler.onRawEvent(new DefaultRawStreamingEvent(null, rawServerSentEvent.data()));
            }
        }
    }

    /** Dispatches the typed events in the chunk; returns whether at least one typed event was emitted. */
    private static boolean dispatchTyped(
            ParsedAndRawResponse<ChatCompletionResponse> parsedAndRawResponse,
            ToolCallBuilder toolCallBuilder,
            StreamingChatResponseHandler handler,
            boolean returnThinking) {

        ChatCompletionResponse partialResponse = parsedAndRawResponse.parsedResponse();
        if (partialResponse == null) {
            return false;
        }

        List<ChatCompletionChoice> choices = partialResponse.choices();
        if (isNullOrEmpty(choices)) {
            return false;
        }

        ChatCompletionChoice chatCompletionChoice = choices.get(0);
        if (chatCompletionChoice == null) {
            return false;
        }

        Delta delta = chatCompletionChoice.delta();
        if (delta == null) {
            return false;
        }

        boolean dispatched = false;

        String content = delta.content();
        if (!isNullOrEmpty(content)) {
            onPartialResponse(handler, content, parsedAndRawResponse.streamingHandle());
            dispatched = true;
        }

        String reasoningContent = delta.reasoningContent();
        if (returnThinking && !isNullOrEmpty(reasoningContent)) {
            onPartialThinking(handler, reasoningContent, parsedAndRawResponse.streamingHandle());
            dispatched = true;
        }

        List<ToolCall> toolCalls = delta.toolCalls();
        if (toolCalls != null) {
            for (ToolCall toolCall : toolCalls) {

                int index;
                if (toolCall.index() != null) {
                    index = toolCall.index();
                } else {
                    index = toolCallBuilder.index();
                    // When index is null and a different tool call id appears, increment the index
                    if (toolCall.id() != null
                            && toolCallBuilder.id() != null
                            && !toolCallBuilder.id().equals(toolCall.id())) {
                        index = toolCallBuilder.index() + 1;
                    }
                }
                if (toolCallBuilder.index() != index) {
                    onCompleteToolCall(handler, toolCallBuilder.buildAndReset());
                    dispatched = true;
                    toolCallBuilder.updateIndex(index);
                }

                String id = toolCallBuilder.updateId(toolCall.id());
                String name = toolCallBuilder.updateName(toolCall.function().name());

                String partialArguments = toolCall.function().arguments();
                if (isNotNullOrEmpty(partialArguments)) {
                    toolCallBuilder.appendArguments(partialArguments);

                    PartialToolCall partialToolRequest = PartialToolCall.builder()
                            .index(index)
                            .id(id)
                            .name(name)
                            .partialArguments(partialArguments)
                            .build();
                    onPartialToolCall(handler, partialToolRequest, parsedAndRawResponse.streamingHandle());
                    dispatched = true;
                }
            }
        }

        return dispatched;
    }
}
