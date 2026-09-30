package dev.langchain4j.model.chat.mock;

import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Arrays.asList;
import static java.util.Collections.synchronizedList;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscription;

/**
 * An implementation of a {@link StreamingChatModel} useful for unit testing.
 * This implementation is experimental and subject to change in the future. It may utilize Mockito internally.
 */
@Experimental
public class StreamingChatModelMock implements StreamingChatModel {

    private final Queue<AiMessage> aiMessages;
    private final RuntimeException exception;
    private final List<ChatRequest> requests = synchronizedList(new ArrayList<>());
    private Set<Capability> supportedCapabilities = Set.of();

    public StreamingChatModelMock(List<String> tokens) {
        this(List.of(toAiMessage(tokens)));
    }

    public StreamingChatModelMock(Collection<AiMessage> aiMessages) {
        this.aiMessages = new ConcurrentLinkedQueue<>(ensureNotEmpty(aiMessages, "aiMessages"));
        this.exception = null;
    }

    public StreamingChatModelMock(RuntimeException exception) {
        this.aiMessages = null;
        this.exception = ensureNotNull(exception, "exception");
    }

    public StreamingChatModelMock withSupportedCapabilities(Capability... supportedCapabilities) {
        this.supportedCapabilities = Set.of(supportedCapabilities);
        return this;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    /**
     * Emits one {@link PartialResponse} per token, then the {@link CompleteResponse}, on the thread that requests
     * them, as many as the subscriber requested.
     */
    @Override
    public Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
        return subscriber -> {
            requests.add(chatRequest);
            subscriber.onSubscribe(new Subscription() {

                private List<ChatModelStreamingEvent> events;
                private int next;
                private long demand;
                private boolean emitting;
                private boolean done;

                @Override
                public synchronized void request(long n) {
                    if (done) {
                        return;
                    }
                    if (n <= 0) {
                        done = true;
                        subscriber.onError(new IllegalArgumentException("Demand must be positive, got " + n));
                        return;
                    }
                    demand = demand + n < 0 ? Long.MAX_VALUE : demand + n;
                    if (emitting) {
                        return; // called from onNext: the loop below emits the additional demand
                    }
                    emitting = true;
                    try {
                        emit();
                    } finally {
                        emitting = false;
                    }
                }

                private void emit() {
                    if (exception != null) {
                        done = true;
                        subscriber.onError(exception);
                        return;
                    }
                    if (events == null) {
                        events = toEvents(ensureNotNull(aiMessages.poll(), "aiMessage"));
                    }
                    while (!done && demand > 0 && next < events.size()) {
                        demand--;
                        subscriber.onNext(events.get(next++));
                    }
                    if (!done && next == events.size()) {
                        done = true;
                        subscriber.onComplete();
                    }
                }

                @Override
                public synchronized void cancel() {
                    done = true;
                }
            });
        };
    }

    private static List<ChatModelStreamingEvent> toEvents(AiMessage aiMessage) {
        List<ChatModelStreamingEvent> events = new ArrayList<>();
        toTokens(aiMessage).forEach(token -> events.add(new PartialResponse(token)));
        events.add(new CompleteResponse(ChatResponse.builder().aiMessage(aiMessage).build()));
        return events;
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        requests.add(chatRequest);

        if (exception != null) {
            handler.onError(exception);
        } else {
            AiMessage aiMessage = ensureNotNull(aiMessages.poll(), "aiMessage");

            var executor = Executors.newSingleThreadExecutor();

            try {
                executor.execute(() -> {

                    StreamingHandle streamingHandle = new SimpleStreamingHandle();

                    for (String token : toTokens(aiMessage)) {
                        if (streamingHandle.isCancelled()) {
                            return;
                        }

                        onPartialResponse(handler, token, streamingHandle);
                    }

                    for (int i = 0; i < aiMessage.toolExecutionRequests().size(); i++) {
                        ToolExecutionRequest toolExecutionRequest =
                                aiMessage.toolExecutionRequests().get(i);
                        CompleteToolCall completeToolCall = new CompleteToolCall(i, toolExecutionRequest);
                        onCompleteToolCall(handler, completeToolCall);
                    }

                    ChatResponse chatResponse =
                            ChatResponse.builder().aiMessage(aiMessage).build();

                    onCompleteResponse(handler, chatResponse);
                });
            } finally {
                executor.shutdown();
            }
        }
    }

    public List<ChatRequest> requests() {
        return requests;
    }

    public ChatRequest request() {
        if (requests.size() == 1) {
            return requests.get(0);
        } else {
            throw runtime("Expected exactly 1 chat request, got: " + requests.size());
        }
    }

    private static AiMessage toAiMessage(List<String> tokens) {
        String text = String.join("", tokens);
        return AiMessage.from(text);
    }

    static List<String> toTokens(AiMessage aiMessage) {
        if (isNullOrEmpty(aiMessage.text())) {
            return List.of();
        }

        // approximating: each char will become a token
        return aiMessage.text().chars().mapToObj(c -> String.valueOf((char) c)).toList();
    }

    private static class SimpleStreamingHandle implements StreamingHandle {

        private boolean isCancelled;

        @Override
        public void cancel() {
            isCancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return isCancelled;
        }
    }

    public static StreamingChatModelMock thatAlwaysStreams(String... tokens) {
        return new StreamingChatModelMock(asList(tokens));
    }

    public static StreamingChatModelMock thatAlwaysStreams(List<String> tokens) {
        return new StreamingChatModelMock(tokens);
    }

    public static StreamingChatModelMock thatAlwaysStreams(AiMessage aiMessage) {
        return new StreamingChatModelMock(List.of(aiMessage));
    }

    public static StreamingChatModelMock thatAlwaysStreams(AiMessage... aiMessages) {
        return new StreamingChatModelMock(asList(aiMessages));
    }

    public static StreamingChatModelMock thatAlwaysStreams(Collection<AiMessage> aiMessages) {
        return new StreamingChatModelMock(aiMessages);
    }

    public static StreamingChatModelMock thatAlwaysThrowsException() {
        return thatAlwaysThrowsExceptionWithMessage("Something went wrong, but this is an expected exception");
    }

    public static StreamingChatModelMock thatAlwaysThrowsExceptionWithMessage(String message) {
        return new StreamingChatModelMock(new RuntimeException(message));
    }
}
