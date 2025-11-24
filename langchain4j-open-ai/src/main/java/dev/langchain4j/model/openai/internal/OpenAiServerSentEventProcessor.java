package dev.langchain4j.model.openai.internal;

import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.StreamingHttpEvent;
import dev.langchain4j.internal.ToolCallBuilder;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingEvent;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.Delta;
import dev.langchain4j.model.openai.internal.chat.ToolCall;

import java.util.List;
import java.util.concurrent.Flow.Processor;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialToolCall;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

public class OpenAiServerSentEventProcessor implements Processor<StreamingHttpEvent, StreamingEvent> {

    private volatile Subscriber<? super StreamingEvent> downstream;
    private volatile SuccessfulHttpResponse httpResponse;
    private final ToolCallBuilder toolCallBuilder = new ToolCallBuilder();

    @Override
    public void subscribe(Subscriber<? super StreamingEvent> subscriber) {
        this.downstream = subscriber;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        downstream.onSubscribe(subscription);
    }

    @Override
    public void onNext(StreamingHttpEvent item) {
        if (item instanceof SuccessfulHttpResponse successfulHttpResponse) {
            this.httpResponse = successfulHttpResponse;
        }

        if (item instanceof ServerSentEvent sse && !sse.data().equals("[DONE]")) {

            ChatCompletionResponse completionResponse = Json.fromJson(sse.data(), ChatCompletionResponse.class);

            ParsedAndRawResponse<ChatCompletionResponse> parsedAndRawResponse = ParsedAndRawResponse.builder()
                    .parsedResponse(completionResponse)
                    .rawHttpResponse(httpResponse)
                    .rawServerSentEvent(sse)
                    .build();

            handle(parsedAndRawResponse, toolCallBuilder, downstream);
        }
    }

    private static void handle(
            ParsedAndRawResponse<ChatCompletionResponse> parsedAndRawResponse,
            ToolCallBuilder toolCallBuilder,
            Subscriber<? super StreamingEvent> downstream) {

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
            onPartialResponse(downstream, content);
        }

        String reasoningContent = delta.reasoningContent();
        if (!isNullOrEmpty(reasoningContent)) {
            onPartialThinking(downstream, reasoningContent);
        }

        List<ToolCall> toolCalls = delta.toolCalls();
        if (toolCalls != null) {
            for (ToolCall toolCall : toolCalls) {

                int index = toolCall.index();
                if (toolCallBuilder.index() != index) {
                    onCompleteToolCall(downstream, toolCallBuilder.buildAndReset());
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
                    onPartialToolCall(downstream, partialToolRequest);
                }
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        downstream.onError(throwable);
    }

    @Override
    public void onComplete() {
        downstream.onComplete();
    }
}
