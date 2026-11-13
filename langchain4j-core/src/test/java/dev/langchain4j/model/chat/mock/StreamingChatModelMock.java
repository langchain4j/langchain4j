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
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscription;

/**
 * An implementation of a {@link StreamingChatModel} useful for unit testing.
 * This implementation is experimental and subject to change in the future. It may utilize Mockito internally.
 * <p>
 * The responses are streamed in the given order, one per request. Once all of them were streamed, the last one is
 * streamed again for every further request (like Mockito's {@code thenReturn(a, b)}), unless it contains tool execution
 * requests: then the mock fails, because repeating it would make an AI Service call the tools again and again.
 *
 * @deprecated use {@link MockStreamingChatModel} (package {@code dev.langchain4j.test.mock}) from the
 * {@code langchain4j-test} module. Unlike this class, it answers one request per scripted step and fails when the
 * script runs out; end the script with {@link MockStreamingChatModel#thenRepeats()} to answer every further request
 * with its last step. {@code withTokenUsage(...)}, {@code requests()} and {@code request()} have the same names. A
 * response reports the model name of the request and {@code STOP} or {@code TOOL_EXECUTION} as its finish reason;
 * instead of {@code withTokenUsages(...)} and {@code withFinishReason(...)}, give a step the whole response:
 * {@link MockStreamingChatModel#thatResponds(ChatResponse)}. {@code throwingFromChat()} and
 * {@code streamingOnCallingThread()} have no replacement, and {@code withListeners(...)} and
 * {@code withSupportedCapabilities(...)} have the same names. Other differences: the new mock also streams partial tool
 * calls and thinking, streams text in word-like tokens, reports every failure on its streaming thread, and
 * {@code requests()} returns a copy. Its {@code Publisher} produces the events on its own thread, so a test that
 * subscribes has to wait for the end of the stream before it checks the events.
 * {@link MockStreamingChatModel#thatStreams(String...)} without tokens completes with no text ({@code null}), where
 * this class completed with {@code ""}. This class will be removed in 1.26.0 at the earliest.
 */
@Experimental
@Deprecated(since = "1.24.0", forRemoval = true)
public class StreamingChatModelMock implements StreamingChatModel {

    private final ScriptedResponses aiMessages;
    private final List<String> tokens;
    private final RuntimeException exception;
    private final List<ChatRequest> requests = synchronizedList(new ArrayList<>());
    private Set<Capability> supportedCapabilities = Set.of();
    private List<TokenUsage> tokenUsages = List.of();
    private FinishReason finishReason;
    private boolean throwFromChat;
    private boolean streamingOnCallingThread;
    private List<ChatModelListener> listeners = List.of();

    /**
     * @deprecated use {@link MockStreamingChatModel#thatStreams(List)} followed by
     * {@link MockStreamingChatModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public StreamingChatModelMock(List<String> tokens) {
        this(List.of(toAiMessage(tokens)), List.copyOf(tokens));
    }

    /**
     * @deprecated use {@link MockStreamingChatModel#thatResponds(List)}, followed by
     * {@link MockStreamingChatModel#thenRepeats()} if the model gets more requests than there are messages. Removed
     * together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public StreamingChatModelMock(Collection<AiMessage> aiMessages) {
        this(aiMessages, null);
    }

    private StreamingChatModelMock(Collection<AiMessage> aiMessages, List<String> tokens) {
        this.aiMessages = new ScriptedResponses("StreamingChatModelMock", ensureNotEmpty(aiMessages, "aiMessages"));
        this.tokens = tokens;
        this.exception = null;
    }

    /**
     * @deprecated use {@link MockStreamingChatModel#thatFails(RuntimeException)} followed by
     * {@link MockStreamingChatModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public StreamingChatModelMock(RuntimeException exception) {
        this.aiMessages = null;
        this.tokens = null;
        this.exception = ensureNotNull(exception, "exception");
    }

    /**
     * @deprecated use {@link MockStreamingChatModel#withSupportedCapabilities(Capability...)}. Removed together with
     * this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public StreamingChatModelMock withSupportedCapabilities(Capability... supportedCapabilities) {
        this.supportedCapabilities = Set.of(supportedCapabilities);
        return this;
    }

    /**
     * Reports the given token usage in every complete response.
     *
     * @deprecated use {@link MockStreamingChatModel#withTokenUsage(TokenUsage)}. Removed together with this class, in
     * 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public StreamingChatModelMock withTokenUsage(TokenUsage tokenUsage) {
        return withTokenUsages(tokenUsage);
    }

    /**
     * Reports the given token usages in successive complete responses, repeating the last one.
     *
     * @deprecated for a different token usage per step, give a step the whole response:
     * {@link MockStreamingChatModel#thatResponds(ChatResponse)}. Removed together with this class, in 1.26.0 at the
     * earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public StreamingChatModelMock withTokenUsages(TokenUsage... tokenUsages) {
        this.tokenUsages = List.of(tokenUsages);
        return this;
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     *
     * @deprecated use {@link MockStreamingChatModel#withListeners(ChatModelListener...)}. Removed together with this
     * class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public StreamingChatModelMock withListeners(ChatModelListener... listeners) {
        this.listeners = List.of(listeners);
        return this;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    /**
     * Streams on the thread that calls {@code chat(ChatRequest, StreamingChatResponseHandler)}, before that call
     * returns, as some in-process models do, instead of on a separate thread.
     *
     * @deprecated has no replacement: {@link MockStreamingChatModel} always streams on its own thread, like a provider.
     * Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public StreamingChatModelMock streamingOnCallingThread() {
        this.streamingOnCallingThread = true;
        return this;
    }

    private TokenUsage recordRequest(ChatRequest chatRequest) {
        synchronized (requests) {
            requests.add(chatRequest);
            return tokenUsages.isEmpty() ? null : tokenUsages.get(Math.min(requests.size(), tokenUsages.size()) - 1);
        }
    }

    /**
     * Reports the given finish reason in every complete response.
     *
     * @deprecated a response of {@link MockStreamingChatModel} reports {@code STOP} or {@code TOOL_EXECUTION}; for
     * another finish reason, give a step the whole response: {@link MockStreamingChatModel#thatResponds(ChatResponse)}.
     * Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public StreamingChatModelMock withFinishReason(FinishReason finishReason) {
        this.finishReason = finishReason;
        return this;
    }

    /**
     * Throws the exception of this mock from {@code doChat(ChatRequest, StreamingChatResponseHandler)}, as some
     * providers do when a request cannot be sent, instead of passing it to the handler.
     * {@code chat(ChatRequest, StreamingChatResponseHandler)} then reports it to the handler's {@code onError}. The {@code Publisher}-based
     * {@code chat(ChatRequest)} then fails with an {@link IllegalStateException}, as this option does not apply to it.
     *
     * @deprecated has no replacement: {@link MockStreamingChatModel} reports every failure with {@code onError}, like
     * the streaming models of LangChain4j. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public StreamingChatModelMock throwingFromChat() {
        this.throwFromChat = true;
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
        if (throwFromChat) {
            throw new IllegalStateException(
                    "throwingFromChat() only applies to chat(ChatRequest, StreamingChatResponseHandler)");
        }
        return subscriber -> {
            TokenUsage tokenUsage = recordRequest(chatRequest);
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
                        events = toEvents(nextAiMessage(), tokenUsage);
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

    private List<ChatModelStreamingEvent> toEvents(AiMessage aiMessage, TokenUsage tokenUsage) {
        List<ChatModelStreamingEvent> events = new ArrayList<>();
        tokens(aiMessage).forEach(token -> events.add(new PartialResponse(token)));
        events.add(new CompleteResponse(chatResponse(aiMessage, tokenUsage)));
        return events;
    }

    private ChatResponse chatResponse(AiMessage aiMessage, TokenUsage tokenUsage) {
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .tokenUsage(tokenUsage)
                .finishReason(finishReason)
                .build();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        TokenUsage tokenUsage = recordRequest(chatRequest);

        if (exception != null && throwFromChat) {
            throw exception;
        } else if (exception != null) {
            handler.onError(exception);
        } else {
            AiMessage aiMessage = nextAiMessage();

            var executor = streamingOnCallingThread ? null : Executors.newSingleThreadExecutor();
            Runnable streaming = () -> {

                    StreamingHandle streamingHandle = new SimpleStreamingHandle();

                    for (String token : tokens(aiMessage)) {
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

                    ChatResponse chatResponse = chatResponse(aiMessage, tokenUsage);

                    onCompleteResponse(handler, chatResponse);
            };
            if (executor == null) {
                streaming.run();
                return;
            }
            try {
                executor.execute(streaming);
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

    private List<String> tokens(AiMessage aiMessage) {
        return tokens != null ? tokens : toTokens(aiMessage);
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

    private AiMessage nextAiMessage() {
        return aiMessages.next();
    }

    /**
     * @deprecated use {@link MockStreamingChatModel#thatStreams(String...)} followed by
     * {@link MockStreamingChatModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static StreamingChatModelMock thatAlwaysStreams(String... tokens) {
        return new StreamingChatModelMock(asList(tokens));
    }

    /**
     * @deprecated use {@link MockStreamingChatModel#thatStreams(List)} followed by
     * {@link MockStreamingChatModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static StreamingChatModelMock thatAlwaysStreams(List<String> tokens) {
        return new StreamingChatModelMock(tokens);
    }

    /**
     * @deprecated use {@link MockStreamingChatModel#thatResponds(AiMessage...)} followed by
     * {@link MockStreamingChatModel#thenRepeats()}; for a message with tool calls, script the answer that follows the
     * tool results with {@link MockStreamingChatModel#thenResponds(String)} instead. Removed together with this class,
     * in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static StreamingChatModelMock thatAlwaysStreams(AiMessage aiMessage) {
        return new StreamingChatModelMock(List.of(aiMessage));
    }

    /**
     * @deprecated use {@link MockStreamingChatModel#thatResponds(AiMessage...)}, followed by
     * {@link MockStreamingChatModel#thenRepeats()} if the model gets more requests than there are messages. Removed
     * together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static StreamingChatModelMock thatAlwaysStreams(AiMessage... aiMessages) {
        return new StreamingChatModelMock(asList(aiMessages));
    }

    /**
     * @deprecated use {@link MockStreamingChatModel#thatResponds(List)}, followed by
     * {@link MockStreamingChatModel#thenRepeats()} if the model gets more requests than there are messages. Removed
     * together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static StreamingChatModelMock thatAlwaysStreams(Collection<AiMessage> aiMessages) {
        return new StreamingChatModelMock(aiMessages);
    }

    /**
     * @deprecated use {@link MockStreamingChatModel#thatFails(RuntimeException)} followed by
     * {@link MockStreamingChatModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static StreamingChatModelMock thatAlwaysThrowsException() {
        return thatAlwaysThrowsExceptionWithMessage("Something went wrong, but this is an expected exception");
    }

    /**
     * @deprecated use {@link MockStreamingChatModel#thatFails(RuntimeException)} followed by
     * {@link MockStreamingChatModel#thenRepeats()}. Removed together with this class, in 1.26.0 at the earliest.
     */
    @Deprecated(since = "1.24.0", forRemoval = true)
    public static StreamingChatModelMock thatAlwaysThrowsExceptionWithMessage(String message) {
        return new StreamingChatModelMock(new RuntimeException(message));
    }
}
