package dev.langchain4j.test.mock;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.test.mock.RecordedRequests.forRequest;
import static java.util.Arrays.asList;

import dev.langchain4j.Experimental;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
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
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.test.mock.ScriptedResponses.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A {@link StreamingChatModel} for tests: it needs no network, streams the answers the test tells it to, and records
 * the requests it receives.
 * <p>
 * Script the answers step by step, one step per request. For example, a model that first calls a tool and then streams
 * text:
 * <pre>{@code
 * StreamingChatModelMock model = StreamingChatModelMock.thatCallsTool("cancelBooking", Map.of("bookingNumber", "MS-777"))
 *         .thenStreams("Booking ", "cancelled.");
 * }</pre>
 * To test a stream that breaks, let a response fail after its tokens:
 * <pre>{@code
 * StreamingChatModelMock model = StreamingChatModelMock.thatStreams("Hel", "lo")
 *         .thenFailsWith(new RuntimeException("connection reset"));
 * }</pre>
 * Steps can be added to a mock created with {@link #thatStreams(String...)},
 * {@link #thatStreams(AiMessage, AiMessage...)}, {@link #thatStreams(List)}, {@link #thatCallsTool(String, Map)} or
 * {@link #thatThrows(RuntimeException)}. Once all steps were taken, the last one is repeated for every further request
 * (like Mockito's {@code thenReturn(a, b)}), unless it is a response with tool calls: then the handler gets an
 * {@link IllegalStateException}, because repeating the response would make an AI Service call the tools again and again.
 * <p>
 * An answer is streamed the way a provider streams it: first its thinking, then its text, then its tool calls, each
 * tool call as partial tool calls followed by the complete tool call. Thinking and tool call arguments are streamed one
 * character per token. {@code chat(request, handler)} returns immediately and calls the handler on another thread, so a test has to wait for
 * {@code onCompleteResponse} or {@code onError}, for example with a {@code CompletableFuture}.
 * <p>
 * A mock remembers which steps were taken and which requests it received, so create a new one for each test.
 */
@Experimental
public class StreamingChatModelMock implements StreamingChatModel {

    private static final String MOCK_NAME = "StreamingChatModelMock";

    private final Function<ChatRequest, AiMessage> answers;
    private final ScriptedResponses steps;
    private final boolean scripted;
    private final RecordedRequests<ChatRequest> requests = new RecordedRequests<>("chat");

    private ModelProvider provider = ModelProvider.OTHER;
    private Set<Capability> supportedCapabilities = Set.of();
    private List<String> modelNames = List.of();
    private List<TokenUsage> tokenUsages = List.of();
    private List<FinishReason> finishReasons = List.of();
    private boolean throwFromChat;
    private boolean streamingOnCallingThread;
    private List<ChatModelListener> listeners = List.of();

    private StreamingChatModelMock(List<Step> steps, boolean scripted) {
        this.answers = null;
        this.steps = new ScriptedResponses(MOCK_NAME, steps);
        this.scripted = scripted;
    }

    private StreamingChatModelMock(Function<ChatRequest, AiMessage> answers) {
        this.answers = ensureNotNull(answers, "answers");
        this.steps = null;
        this.scripted = false;
    }

    /**
     * Adds a step: the next request is answered by streaming the given tokens, which together make up one answer.
     *
     * @throws IllegalStateException if steps cannot be added to this mock, see {@link StreamingChatModelMock}
     */
    public StreamingChatModelMock thenStreams(String... tokens) {
        script().add(Step.streaming(asList(tokens)));
        return this;
    }

    /**
     * Adds a step: the next request is answered by streaming the given message, one character per token.
     *
     * @throws IllegalStateException if steps cannot be added to this mock, see {@link StreamingChatModelMock}
     */
    public StreamingChatModelMock thenStreams(AiMessage aiMessage) {
        script().add(Step.responding(aiMessage));
        return this;
    }

    /**
     * Adds a step: the next request is answered with a call of the given tool.
     *
     * @param toolName  the name of the tool
     * @param arguments the arguments of the tool, by name
     * @throws IllegalStateException if steps cannot be added to this mock, see {@link StreamingChatModelMock}
     */
    public StreamingChatModelMock thenCallsTool(String toolName, Map<String, ?> arguments) {
        return thenCallsTool(toolName, ChatModelMock.toJson(arguments));
    }

    /**
     * Adds a step: the next request is answered with a call of the given tool. Use it for arguments that cannot be
     * given as a map, for example invalid JSON.
     *
     * @param toolName      the name of the tool
     * @param jsonArguments the arguments of the tool, as JSON
     * @throws IllegalStateException if steps cannot be added to this mock, see {@link StreamingChatModelMock}
     */
    public StreamingChatModelMock thenCallsTool(String toolName, String jsonArguments) {
        script().addToolCall(toolName, jsonArguments);
        return this;
    }

    /**
     * Adds a step: the next request fails with the given exception before anything is streamed. To let a response fail
     * after its tokens, use {@link #thenFailsWith(RuntimeException)}.
     *
     * @throws IllegalStateException if steps cannot be added to this mock, see {@link StreamingChatModelMock}
     */
    public StreamingChatModelMock thenThrows(RuntimeException exception) {
        script().add(Step.failing(exception));
        return this;
    }

    /**
     * Makes the response of the last step fail after its tokens were streamed, the way a broken connection does: the
     * handler gets the partial responses and then the error, but no complete response. It does not add a step: to let
     * the next request fail before anything is streamed, use {@link #thenThrows(RuntimeException)}.
     *
     * @throws IllegalStateException if the last step does not stream text
     */
    public StreamingChatModelMock thenFailsWith(RuntimeException exception) {
        if (steps == null) {
            throw new IllegalStateException("thenFailsWith(...) cannot be used on a mock that computes its answers");
        }
        steps.failLastAfterTokens(exception);
        return this;
    }

    private ScriptedResponses script() {
        if (!scripted) {
            throw new IllegalStateException("Steps can only be added to a mock created with thatStreams(String...),"
                    + " thatStreams(AiMessage, AiMessage...), thatStreams(List), thatCallsTool(...) or thatThrows(...)");
        }
        return steps;
    }

    /**
     * Reports the given provider from {@link #provider()}.
     */
    public StreamingChatModelMock withProvider(ModelProvider provider) {
        this.provider = ensureNotNull(provider, "provider");
        return this;
    }

    @Override
    public ModelProvider provider() {
        return provider;
    }

    /**
     * Reports the given capabilities from {@link #supportedCapabilities()}.
     */
    public StreamingChatModelMock withSupportedCapabilities(Capability... supportedCapabilities) {
        this.supportedCapabilities = Set.of(supportedCapabilities);
        return this;
    }

    /**
     * Reports the given model name in every complete response.
     */
    public StreamingChatModelMock withModelName(String modelName) {
        return withModelNames(modelName);
    }

    /**
     * Reports the given model names for successive requests, repeating the last one.
     */
    public StreamingChatModelMock withModelNames(String... modelNames) {
        this.modelNames = List.of(modelNames);
        return this;
    }

    /**
     * Reports the given token usage in every complete response.
     */
    public StreamingChatModelMock withTokenUsage(TokenUsage tokenUsage) {
        return withTokenUsages(tokenUsage);
    }

    /**
     * Reports the given token usages for successive requests, repeating the last one.
     */
    public StreamingChatModelMock withTokenUsages(TokenUsage... tokenUsages) {
        this.tokenUsages = List.of(tokenUsages);
        return this;
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public StreamingChatModelMock withListeners(ChatModelListener... listeners) {
        this.listeners = List.of(listeners);
        return this;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    /**
     * Only for LangChain4j's own tests.
     */
    @Internal
    public StreamingChatModelMock streamingOnCallingThread() {
        this.streamingOnCallingThread = true;
        return this;
    }

    /**
     * Reports the given finish reason in every complete response.
     */
    public StreamingChatModelMock withFinishReason(FinishReason finishReason) {
        return withFinishReasons(finishReason);
    }

    /**
     * Reports the given finish reasons for successive requests, repeating the last one.
     */
    public StreamingChatModelMock withFinishReasons(FinishReason... finishReasons) {
        this.finishReasons = List.of(finishReasons);
        return this;
    }

    /**
     * Only for LangChain4j's own tests.
     */
    @Internal
    public StreamingChatModelMock throwingFromChat() {
        this.throwFromChat = true;
        return this;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    private Step nextStep(ChatRequest chatRequest) {
        try {
            return answers != null ? Step.responding(answers.apply(chatRequest)) : steps.next();
        } catch (RuntimeException e) {
            return Step.failing(e);
        }
    }

    /**
     * Emits {@link PartialThinking} and {@link PartialResponse} events, then {@link PartialToolCall} and
     * {@link CompleteToolCall} events, then the {@link CompleteResponse}, on the thread that requests them, as many as
     * the subscriber requested.
     */
    @Override
    public Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
        if (throwFromChat) {
            throw new IllegalStateException(
                    "throwingFromChat() only applies to chat(ChatRequest, StreamingChatResponseHandler)");
        }
        return subscriber -> {
            int requestNumber = requests.add(chatRequest);
            Step step = nextStep(chatRequest);
            List<ChatModelStreamingEvent> events = toEvents(step, requestNumber);
            subscriber.onSubscribe(new Subscription() {

                private int next;
                private long demand;
                private boolean emitting;
                private volatile boolean done;

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
                    while (!done && demand > 0 && next < events.size()) {
                        demand--;
                        subscriber.onNext(events.get(next++));
                    }
                    if (!done && next == events.size()) {
                        done = true;
                        if (step.failure() != null) {
                            subscriber.onError(step.failure());
                        } else {
                            subscriber.onComplete();
                        }
                    }
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        };
    }

    private List<ChatModelStreamingEvent> toEvents(Step step, int requestNumber) {
        List<ChatModelStreamingEvent> events = new ArrayList<>();
        step.thinkingTokens().forEach(token -> events.add(new PartialThinking(token)));
        step.tokens().forEach(token -> events.add(new PartialResponse(token)));
        if (step.failure() == null) {
            List<ToolExecutionRequest> toolCalls = step.aiMessage().toolExecutionRequests();
            for (int i = 0; i < toolCalls.size(); i++) {
                events.addAll(partialToolCalls(i, toolCalls.get(i)));
                events.add(new CompleteToolCall(i, toolCalls.get(i)));
            }
            events.add(new CompleteResponse(chatResponse(step.aiMessage(), requestNumber)));
        }
        return events;
    }

    private static List<PartialToolCall> partialToolCalls(int index, ToolExecutionRequest toolCall) {
        if (isNullOrEmpty(toolCall.name())) {
            return List.of(); // a partial tool call needs a name, which a hand-written tool call may not have
        }
        return splitIntoTokens(toolCall.arguments()).stream()
                .map(token -> PartialToolCall.builder()
                        .index(index)
                        .id(toolCall.id())
                        .name(toolCall.name())
                        .partialArguments(token)
                        .build())
                .toList();
    }

    private ChatResponse chatResponse(AiMessage aiMessage, int requestNumber) {
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .modelName(forRequest(modelNames, requestNumber))
                .tokenUsage(forRequest(tokenUsages, requestNumber))
                .finishReason(forRequest(finishReasons, requestNumber))
                .build();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        int requestNumber = requests.add(chatRequest);
        Step step = nextStep(chatRequest);

        if (step.aiMessage() == null) {
            if (throwFromChat) {
                throw step.failure();
            }
            handler.onError(step.failure());
            return;
        }

        if (streamingOnCallingThread) {
            stream(step, requestNumber, handler);
            return;
        }

        Thread thread = new Thread(
                () -> {
                    try {
                        stream(step, requestNumber, handler);
                    } catch (Error e) {
                        // an AssertionError of the test's handler would otherwise end this thread without a final call
                        withLoggingExceptions(() -> handler.onError(e));
                    }
                },
                "StreamingChatModelMock");
        thread.setDaemon(true);
        thread.start();
    }

    private void stream(Step step, int requestNumber, StreamingChatResponseHandler handler) {
        StreamingHandle streamingHandle = new SimpleStreamingHandle();

        for (String token : step.thinkingTokens()) {
            if (streamingHandle.isCancelled()) {
                return;
            }

            onPartialThinking(handler, token, streamingHandle);
        }

        for (String token : step.tokens()) {
            if (streamingHandle.isCancelled()) {
                return;
            }

            onPartialResponse(handler, token, streamingHandle);
        }

        if (streamingHandle.isCancelled()) {
            return;
        }

        if (step.failure() != null) {
            withLoggingExceptions(() -> handler.onError(step.failure()));
            return;
        }

        AiMessage aiMessage = step.aiMessage();
        for (int i = 0; i < aiMessage.toolExecutionRequests().size(); i++) {
            ToolExecutionRequest toolExecutionRequest =
                    aiMessage.toolExecutionRequests().get(i);
            for (PartialToolCall partialToolCall : partialToolCalls(i, toolExecutionRequest)) {
                if (streamingHandle.isCancelled()) {
                    return;
                }

                onPartialToolCall(handler, partialToolCall, streamingHandle);
            }
            if (streamingHandle.isCancelled()) {
                return;
            }
            CompleteToolCall completeToolCall = new CompleteToolCall(i, toolExecutionRequest);
            onCompleteToolCall(handler, completeToolCall);
        }

        if (streamingHandle.isCancelled()) {
            return;
        }

        onCompleteResponse(handler, chatResponse(aiMessage, requestNumber));
    }

    /**
     * A copy of the requests received so far, in the order they arrived.
     */
    public List<ChatRequest> requests() {
        return requests.all();
    }

    /**
     * The only request received so far.
     *
     * @throws IllegalStateException if the mock received no request or several requests
     */
    public ChatRequest request() {
        return requests.only();
    }

    static List<String> toTokens(AiMessage aiMessage) {
        return splitIntoTokens(aiMessage.text());
    }

    static List<String> splitIntoTokens(String text) {
        if (isNullOrEmpty(text)) {
            return List.of();
        }

        // approximating: each character will become a token
        return text.codePoints().mapToObj(Character::toString).toList();
    }

    private static class SimpleStreamingHandle implements StreamingHandle {

        private volatile boolean isCancelled;

        @Override
        public void cancel() {
            isCancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return isCancelled;
        }
    }

    /**
     * A model that answers every request by streaming the given tokens, which together make up one answer.
     */
    public static StreamingChatModelMock thatAlwaysStreams(String... tokens) {
        return new StreamingChatModelMock(List.of(Step.streaming(asList(tokens))), false);
    }

    /**
     * A model that answers every request by streaming the given message, one character per token. It fails on the
     * second request if the message contains tool calls, because repeating it would make an AI Service call the tools
     * again and again.
     */
    public static StreamingChatModelMock thatAlwaysStreams(AiMessage aiMessage) {
        return new StreamingChatModelMock(List.of(Step.responding(aiMessage)), false);
    }

    /**
     * A model that answers the first request by streaming the given tokens, which together make up one answer. Add the
     * next steps with {@code then...} methods.
     */
    public static StreamingChatModelMock thatStreams(String... tokens) {
        return new StreamingChatModelMock(List.of(Step.streaming(asList(tokens))), true);
    }

    /**
     * A model that streams the given messages in order, one per request, one character per token. Add further steps
     * with {@code then...} methods. Use it for answers that {@link #thatCallsTool(String, Map)} cannot express, for
     * example several tool calls in one answer, built with {@link ChatModelMock#toolCall(String, Map)}.
     */
    public static StreamingChatModelMock thatStreams(AiMessage first, AiMessage... next) {
        return thatStreams(Stream.concat(Stream.of(first), Stream.of(next)).toList());
    }

    /**
     * A model that streams the given messages in order, one per request, one character per token. Add further steps
     * with {@code then...} methods.
     */
    public static StreamingChatModelMock thatStreams(List<AiMessage> aiMessages) {
        ensureNotEmpty(aiMessages, "aiMessages");
        return new StreamingChatModelMock(
                aiMessages.stream().map(Step::responding).toList(), true);
    }

    /**
     * A model that computes the answer from each request and streams it, one character per token.
     */
    public static StreamingChatModelMock thatStreams(Function<ChatRequest, AiMessage> answers) {
        return new StreamingChatModelMock(answers);
    }

    /**
     * A model that answers the first request with a call of the given tool. Add the next steps with {@code then...}
     * methods. The id of the call contains the number of its step, so that calls of the same tool can be told apart.
     *
     * @param toolName  the name of the tool
     * @param arguments the arguments of the tool, by name
     */
    public static StreamingChatModelMock thatCallsTool(String toolName, Map<String, ?> arguments) {
        return thatCallsTool(toolName, ChatModelMock.toJson(arguments));
    }

    /**
     * A model that answers the first request with a call of the given tool. Add the next steps with {@code then...}
     * methods. Use it for arguments that cannot be given as a map, for example invalid JSON.
     *
     * @param toolName      the name of the tool
     * @param jsonArguments the arguments of the tool, as JSON
     */
    public static StreamingChatModelMock thatCallsTool(String toolName, String jsonArguments) {
        return new StreamingChatModelMock(List.of(Step.callingTool(toolName, jsonArguments, 1)), true);
    }

    /**
     * A model whose first request fails with the given exception before anything is streamed. Add the next steps with
     * {@code then...} methods.
     */
    public static StreamingChatModelMock thatThrows(RuntimeException exception) {
        return new StreamingChatModelMock(List.of(Step.failing(exception)), true);
    }

    /**
     * A model whose requests all fail with the given exception, before anything is streamed.
     */
    public static StreamingChatModelMock thatAlwaysThrows(RuntimeException exception) {
        return new StreamingChatModelMock(List.of(Step.failing(exception)), false);
    }

}
