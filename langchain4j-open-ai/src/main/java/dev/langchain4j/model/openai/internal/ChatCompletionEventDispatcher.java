package dev.langchain4j.model.openai.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.ToolCallBuilder;
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

        ChatCompletionResponse partialResponse = parsedAndRawResponse.parsedResponse();
        if (partialResponse == null) {
            return;
        }

        List<ChatCompletionChoice> choices = partialResponse.choices();
        if (isNullOrEmpty(choices)) {
            return;
        }

        ChatCompletionChoice chatCompletionChoice = choices.get(0);
        if (chatCompletionChoice == null) {
            return;
        }

        Delta delta = chatCompletionChoice.delta();
        if (delta == null) {
            return;
        }

        String content = delta.content();
        if (!isNullOrEmpty(content)) {
            onPartialResponse(handler, content, parsedAndRawResponse.streamingHandle());
        }

        String reasoningContent = delta.reasoningContent();
        if (returnThinking && !isNullOrEmpty(reasoningContent)) {
            onPartialThinking(handler, reasoningContent, parsedAndRawResponse.streamingHandle());
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
                }
            }
        }
    }
}
