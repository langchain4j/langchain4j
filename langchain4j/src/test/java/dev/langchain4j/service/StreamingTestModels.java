package dev.langchain4j.service;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialToolCall;
import static java.util.concurrent.TimeUnit.SECONDS;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;

/**
 * Streaming models for the tests of this package that need a model to do what the mock does not: stream on the thread
 * that calls the model, go on streaming after a cancellation, or answer with tool calls without an id.
 */
final class StreamingTestModels {

    private StreamingTestModels() {}

    /**
     * The given mock, streaming on the thread that calls the model, like a model that streams before
     * {@code chat(...)} returns. That thread is what the tests using it check, so it is written here instead of being
     * an option of the mock: it collects the events the mock streams on its own thread, and then replays them to the
     * handler on the calling thread.
     */
    static StreamingChatModel streamingOnCallingThread(MockStreamingChatModel mock) {
        return replayingOnCallingThread(mock, true, event -> event);
    }

    /**
     * Like {@link #streamingOnCallingThread(MockStreamingChatModel)}, but the rest of the answer is still streamed
     * after the handler cancelled the stream, like a provider that still delivers the events it had received before
     * the cancellation. The cancellation is recorded: {@link StreamingHandle#isCancelled()} returns {@code true}.
     */
    static StreamingChatModel streamingOnCallingThreadEvenAfterCancellation(MockStreamingChatModel mock) {
        return replayingOnCallingThread(mock, false, event -> event);
    }

    /**
     * The given mock, answering with tool calls without an id, like a provider that gives tool calls no id. The mock
     * gives every tool call without an id an id of its own, so the ids of all tool calls are removed. It streams on
     * the thread that calls the model, see {@link #streamingOnCallingThread(MockStreamingChatModel)}.
     */
    static StreamingChatModel withoutToolCallIds(MockStreamingChatModel mock) {
        return replayingOnCallingThread(mock, true, StreamingTestModels::withoutToolCallId);
    }

    private static StreamingChatModel replayingOnCallingThread(
            MockStreamingChatModel mock,
            boolean stopOnCancellation,
            UnaryOperator<ChatModelStreamingEvent> eventMapper) {
        return new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                List<ChatModelStreamingEvent> events = new CopyOnWriteArrayList<>();
                CompletableFuture<Void> streamed = new CompletableFuture<>();
                mock.doChat(chatRequest).subscribe(new Flow.Subscriber<>() {

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent event) {
                        events.add(eventMapper.apply(event));
                    }

                    @Override
                    public void onError(Throwable error) {
                        streamed.completeExceptionally(error);
                    }

                    @Override
                    public void onComplete() {
                        streamed.complete(null);
                    }
                });
                Throwable failure = null;
                try {
                    streamed.get(10, SECONDS);
                } catch (ExecutionException e) {
                    failure = e.getCause();
                } catch (InterruptedException | TimeoutException e) {
                    failure = e;
                }
                replay(events, failure, handler, stopOnCancellation);
            }
        };
    }

    private static void replay(
            List<ChatModelStreamingEvent> events,
            Throwable failure,
            StreamingChatResponseHandler handler,
            boolean stopOnCancellation) {
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
        for (ChatModelStreamingEvent event : events) {
            if (stopOnCancellation && streamingHandle.isCancelled()) {
                return;
            }
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
        }
        if (failure != null && !streamingHandle.isCancelled()) {
            handler.onError(failure);
        }
    }

    private static ChatModelStreamingEvent withoutToolCallId(ChatModelStreamingEvent event) {
        if (event instanceof PartialToolCall partialToolCall) {
            return PartialToolCall.builder()
                    .index(partialToolCall.index())
                    .name(partialToolCall.name())
                    .partialArguments(partialToolCall.partialArguments())
                    .build();
        }
        if (event instanceof CompleteToolCall completeToolCall) {
            return new CompleteToolCall(
                    completeToolCall.index(), withoutId(completeToolCall.toolExecutionRequest()));
        }
        if (event instanceof CompleteResponse completeResponse) {
            ChatResponse response = completeResponse.chatResponse();
            AiMessage aiMessage = response.aiMessage();
            if (!aiMessage.hasToolExecutionRequests()) {
                return event;
            }
            List<ToolExecutionRequest> toolCalls = aiMessage.toolExecutionRequests().stream()
                    .map(StreamingTestModels::withoutId)
                    .toList();
            return new CompleteResponse(response.toBuilder()
                    .aiMessage(aiMessage.toBuilder().toolExecutionRequests(toolCalls).build())
                    .build());
        }
        return event;
    }

    private static ToolExecutionRequest withoutId(ToolExecutionRequest toolCall) {
        return toolCall.toBuilder().id(null).build();
    }
}
