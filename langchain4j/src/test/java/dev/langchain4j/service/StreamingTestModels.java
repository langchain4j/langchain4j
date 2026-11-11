package dev.langchain4j.service;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialToolCall;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import java.util.concurrent.Flow;

/**
 * Streaming models for the tests of this package that check which thread a model streams on.
 */
final class StreamingTestModels {

    private StreamingTestModels() {}

    /**
     * The given mock, streaming on the thread that calls the model, like a model that streams before
     * {@code chat(...)} returns. That thread is what the tests using it check, so it is written here instead of being
     * an option of the mock.
     */
    static StreamingChatModel streamingOnCallingThread(MockStreamingChatModel mock) {
        return new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                StreamingHandle streamingHandle = new StreamingHandle() {

                    private volatile boolean cancelled;

                    @Override
                    public void cancel() {
                        cancelled = true;
                    }

                    @Override
                    public boolean isCancelled() {
                        return cancelled;
                    }
                };
                mock.doChat(chatRequest).subscribe(new Flow.Subscriber<>() {

                    private Flow.Subscription subscription;

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        this.subscription = subscription;
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent event) {
                        if (event instanceof PartialThinking partialThinking) {
                            onPartialThinking(handler, partialThinking.text(), streamingHandle);
                        } else if (event instanceof PartialResponse partialResponse) {
                            onPartialResponse(handler, partialResponse.text(), streamingHandle);
                        } else if (event instanceof PartialToolCall partialToolCall) {
                            onPartialToolCall(handler, partialToolCall, streamingHandle);
                        } else if (event instanceof CompleteToolCall completeToolCall) {
                            onCompleteToolCall(handler, completeToolCall);
                        } else if (event instanceof CompleteResponse completeResponse) {
                            onCompleteResponse(handler, completeResponse.chatResponse());
                        }
                        if (streamingHandle.isCancelled()) {
                            subscription.cancel();
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        handler.onError(error);
                    }

                    @Override
                    public void onComplete() {}
                });
            }
        };
    }
}
