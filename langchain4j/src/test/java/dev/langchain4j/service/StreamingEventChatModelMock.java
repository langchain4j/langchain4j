package dev.langchain4j.service;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Arrays.asList;
import static java.util.Collections.synchronizedList;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.RawStreamingEvent;
import dev.langchain4j.model.chat.response.StreamingEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.atomic.AtomicBoolean;
import mutiny.zero.BackpressureStrategy;
import mutiny.zero.TubeConfiguration;
import mutiny.zero.ZeroPublisher;

/**
 * A test-only {@link StreamingChatModel} that implements the reactive, publisher-based
 * {@link #doChat(ChatRequest)} contract. Each subscription replays one queued {@link AiMessage}: it emits the
 * message text as per-character {@link PartialResponse}s, then a {@link CompleteToolCall} per tool request, then
 * the terminal {@link ChatResponse}, then {@code onComplete}. Emission happens on a dedicated thread so the
 * stream is genuinely asynchronous (never delivered inline on the subscriber's thread).
 */
class StreamingEventChatModelMock implements StreamingChatModel {

    private final Queue<AiMessage> aiMessages;
    private final List<ChatRequest> requests = synchronizedList(new ArrayList<>());
    private final long tokenDelayMillis;
    private final Executor deliveryExecutor;
    private final AtomicBoolean cancellationObserved = new AtomicBoolean(false);
    private RawStreamingEvent rawEvent;
    private CountDownLatch toolRoundCompletionGate;

    StreamingEventChatModelMock(Collection<AiMessage> aiMessages) {
        this(aiMessages, 0, null);
    }

    StreamingEventChatModelMock(Collection<AiMessage> aiMessages, long tokenDelayMillis, Executor deliveryExecutor) {
        this.aiMessages = new ConcurrentLinkedQueue<>(ensureNotEmpty(aiMessages, "aiMessages"));
        this.tokenDelayMillis = tokenDelayMillis;
        this.deliveryExecutor = deliveryExecutor;
    }

    static StreamingEventChatModelMock thatStreams(AiMessage... aiMessages) {
        return new StreamingEventChatModelMock(asList(aiMessages));
    }

    static StreamingEventChatModelMock thatStreamsSlowly(long tokenDelayMillis, AiMessage... aiMessages) {
        return new StreamingEventChatModelMock(asList(aiMessages), tokenDelayMillis, null);
    }

    /**
     * Emits all events on the given executor instead of a fresh daemon thread. Used by BlockHound tests so
     * that the AI Service's reactive tool loop runs on a policed (non-blocking) thread.
     */
    static StreamingEventChatModelMock thatStreamsOn(Executor deliveryExecutor, AiMessage... aiMessages) {
        return new StreamingEventChatModelMock(asList(aiMessages), 0, deliveryExecutor);
    }

    /**
     * Emits the given raw provider event at the start of the stream (before the text chunks). Used to verify
     * raw-event propagation through the AI Service stream.
     */
    StreamingEventChatModelMock withRawEvent(RawStreamingEvent rawEvent) {
        this.rawEvent = rawEvent;
        return this;
    }

    /**
     * Makes the model wait on the given latch after emitting a round's tool calls and before completing that
     * round (i.e. before the terminal {@link ChatResponse}). Used to prove that tools are started eagerly: if
     * the AI Service only started tools after the model response completed, this would deadlock.
     */
    StreamingEventChatModelMock withToolRoundCompletionGate(CountDownLatch gate) {
        this.toolRoundCompletionGate = gate;
        return this;
    }

    boolean cancellationObserved() {
        return cancellationObserved.get();
    }

    @Override
    public Publisher<StreamingEvent> doChat(ChatRequest chatRequest) {
        requests.add(chatRequest);
        AiMessage aiMessage = ensureNotNull(aiMessages.poll(), "aiMessage");

        TubeConfiguration config = new TubeConfiguration()
                .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                .withBufferSize(256);

        return ZeroPublisher.create(config, tube -> {
            Runnable emission = () -> {
                try {
                    if (rawEvent != null && !tube.cancelled()) {
                        tube.send(rawEvent);
                    }
                    String text = aiMessage.text();
                    if (text != null) {
                        for (int i = 0; i < text.length(); i++) {
                            if (tube.cancelled()) {
                                cancellationObserved.set(true);
                                return;
                            }
                            tube.send(new PartialResponse(String.valueOf(text.charAt(i))));
                            if (tokenDelayMillis > 0) {
                                Thread.sleep(tokenDelayMillis);
                            }
                        }
                    }
                    List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
                    for (int i = 0; i < toolRequests.size(); i++) {
                        if (tube.cancelled()) {
                            return;
                        }
                        tube.send(new CompleteToolCall(i, toolRequests.get(i)));
                    }
                    if (toolRoundCompletionGate != null && !toolRequests.isEmpty()) {
                        // wait until the AI Service has started the tool(s) before completing this round
                        toolRoundCompletionGate.await();
                    }
                    if (tube.cancelled()) {
                        return;
                    }
                    tube.send(new CompleteResponse(ChatResponse.builder().aiMessage(aiMessage).build()));
                    tube.complete();
                } catch (Throwable error) {
                    tube.fail(error);
                }
            };

            if (deliveryExecutor != null) {
                deliveryExecutor.execute(emission);
            } else {
                Thread thread = new Thread(emission);
                thread.setDaemon(true);
                thread.start();
            }
        });
    }

    List<ChatRequest> requests() {
        return requests;
    }
}
