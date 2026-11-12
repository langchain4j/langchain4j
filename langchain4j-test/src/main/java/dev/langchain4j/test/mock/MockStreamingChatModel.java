package dev.langchain4j.test.mock;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
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
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * A {@link StreamingChatModel} for tests: it needs no network, streams the answers the test tells it to, and records
 * the requests it receives.
 * <p>
 * Script the answers step by step, one step per request. For example, a model that first calls a tool and then streams
 * text:
 * <pre>{@code
 * MockStreamingChatModel model = MockStreamingChatModel.thatCallsTool("cancelBooking", Map.of("bookingNumber", "MS-777"))
 *         .thenStreams("Booking MS-777 is cancelled.");
 * }</pre>
 * To control exactly which tokens are streamed, use {@link #thatStreamsTokens(String...)}. To test a stream that
 * breaks, let a response fail after its tokens:
 * <pre>{@code
 * MockStreamingChatModel model = MockStreamingChatModel.thatStreamsTokens("Hel", "lo")
 *         .andFails(new RuntimeException("connection reset"));
 * }</pre>
 * Every {@code that...} method creates a mock with its first step (one step per message for the message-list forms),
 * and every {@code then...} method adds a step. Each step answers exactly one request. A request after the last step
 * gets an {@link IllegalStateException} in {@code onError} that shows the request, so a test notices when the code
 * calls the model more often than the script expects, unless the code under test handles the error itself. To answer
 * every further request with the last step, end the script with {@link #thenRepeats()}, for example
 * {@code MockStreamingChatModel.thatStreams("Hello!").thenRepeats()}. A step can compute its answer from the request
 * with {@link #thatStreams(Function)}.
 * <p>
 * An answer is streamed the way a provider streams it: first its thinking, then its text, then its tool calls, each
 * tool call as partial tool calls followed by the complete tool call. Text given as one string, thinking and tool call
 * arguments are streamed one character per token. The complete response reports what a provider reports: the model
 * name of the request, and the finish reason {@link FinishReason#TOOL_EXECUTION} for an answer with tool calls,
 * otherwise {@link FinishReason#STOP}. To report token usage in every response, use {@link #withTokenUsage(TokenUsage)}.
 * To report anything else, for example {@link FinishReason#LENGTH} or a different token usage per step, give the whole
 * response: {@link #thatStreams(ChatResponse)}.
 * <p>
 * The mock supports no capabilities, so an AI Service that returns a structured type, for example a POJO, adds format
 * instructions to the user message instead of asking for a JSON schema response format. To test the response-format
 * path, stub {@code supportedCapabilities()} on a Mockito spy of the mock.
 * <p>
 * Like a provider, the mock streams on its own thread, never on the thread that calls it. {@code chat(request, handler)}
 * returns immediately and calls the handler on another thread, also for a failure, so a test has to wait for
 * {@code onCompleteResponse} or {@code onError}, for example with a {@code CompletableFuture}. The {@code Publisher}
 * returned by {@code chat(request)} emits its events on a new thread for each subscription, as many as the subscriber
 * requested. Each subscription to that {@code Publisher} is one request: the mock records the request and takes the next
 * step when the {@code Publisher} is subscribed to, not when {@code chat(request)} returns, so a retry that subscribes
 * again gets the next step.
 * <p>
 * A mock remembers which steps were taken and which requests it received, so create a new one for each test. A mock
 * shared between tests, for example as a bean, fails with a clear message as soon as its script runs out, unless the
 * script ends with {@link #thenRepeats()}. The steps are taken in the order the requests arrive. A mock keeps every
 * request it receives, so it is not meant for load tests or other long runs.
 */
@Experimental
public class MockStreamingChatModel implements StreamingChatModel {

    private static final String MOCK_NAME = "MockStreamingChatModel";

    private final ScriptedResponses script;
    private final RecordedRequests<ChatRequest> requests = new RecordedRequests<>("chat");
    private List<ChatModelListener> listeners = List.of();

    MockStreamingChatModel(List<Step> steps) {
        this.script = new ScriptedResponses(MOCK_NAME, steps);
    }

    /**
     * Adds a step: the next request is answered by streaming the given text, one character per token.
     *
     * @throws IllegalArgumentException if the text is empty, see {@link #thatStreams(String)}
     * @throws IllegalStateException    if the script already ends with {@link #thenRepeats()}
     */
    public MockStreamingChatModel thenStreams(String text) {
        return thenStreams(AiMessage.from(text));
    }

    /**
     * Adds a step: the next request is answered by streaming exactly the given tokens, which together make up one
     * answer. Without tokens, nothing is streamed and the answer has no text.
     *
     * @throws IllegalArgumentException if a token is empty
     * @throws IllegalStateException    if the script already ends with {@link #thenRepeats()}
     */
    public MockStreamingChatModel thenStreamsTokens(String... tokens) {
        script.add(Step.streaming(tokens));
        return this;
    }

    /**
     * Adds a step: the next request is answered by streaming exactly the given tokens, see
     * {@link #thenStreamsTokens(String...)}.
     *
     * @throws IllegalArgumentException if a token is empty
     * @throws IllegalStateException    if the script already ends with {@link #thenRepeats()}
     */
    public MockStreamingChatModel thenStreamsTokens(List<String> tokens) {
        return thenStreamsTokens(ensureNotNull(tokens, "tokens").toArray(String[]::new));
    }

    /**
     * Adds a step: the next request is answered by streaming the given message, one character per token.
     *
     * @throws IllegalArgumentException if the text of the message is empty, see {@link #thatStreams(String)}
     * @throws IllegalStateException    if the script already ends with {@link #thenRepeats()}
     */
    public MockStreamingChatModel thenStreams(AiMessage aiMessage) {
        script.add(Step.responding(ensureNotEmptyText(aiMessage)));
        return this;
    }

    /**
     * Adds a step: the next request is answered by streaming the message of the given response, one character per
     * token, and completing with exactly that response, including its metadata, for example its token usage. Only tool
     * calls without an id get the next id of the mock, see {@link MockChatModel#toolCall(String, Map)}.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockStreamingChatModel thenStreams(ChatResponse chatResponse) {
        script.add(Step.responding(chatResponse));
        return this;
    }

    /**
     * Adds a step: the next request is answered with a call of the given tool.
     *
     * @param toolName  the name of the tool
     * @param arguments the arguments of the tool, by name
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockStreamingChatModel thenCallsTool(String toolName, Map<String, ?> arguments) {
        return thenCallsTool(toolName, MockChatModel.toJson(arguments));
    }

    /**
     * Adds a step: the next request is answered with a call of the given tool. Use it for arguments that cannot be
     * given as a map, for example invalid JSON.
     *
     * @param toolName      the name of the tool
     * @param jsonArguments the arguments of the tool, as JSON
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockStreamingChatModel thenCallsTool(String toolName, String jsonArguments) {
        script.add(Step.callingTool(toolName, jsonArguments));
        return this;
    }

    /**
     * Adds a step: the next request fails with the given exception before anything is streamed. To let a response fail
     * after its tokens, use {@link #andFails(RuntimeException)}.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockStreamingChatModel thenFails(RuntimeException exception) {
        script.add(Step.failing(exception));
        return this;
    }

    /**
     * Makes the response of the last step fail after its thinking and text were streamed, the way a broken connection
     * does: the handler gets the partial responses and then the error, but no tool calls and no complete response. It
     * does not add a step: to let the next request fail before anything is streamed, use
     * {@link #thenFails(RuntimeException)}.
     *
     * @throws IllegalStateException if the last step already fails, computes its answer or has no text or thinking to
     *                               stream, if the mock already answered the last step, or if the script already ends
     *                               with {@link #thenRepeats()}
     */
    public MockStreamingChatModel andFails(RuntimeException exception) {
        script.failLastAfterTokens(exception);
        return this;
    }

    /**
     * Answers every further request with the last step of the script. Only the last step repeats; the script does not
     * start over. A last step that computes its answer computes it again for each request. Without it, a request after
     * the last step fails. It must be the last call of the script.
     * <p>
     * A step that calls a tool cannot repeat. To test a model that keeps calling a tool, for example the limit set with
     * {@code maxToolCallingRoundTrips}, compute the answer:
     * {@code thatStreams(request -> AiMessage.from(MockChatModel.toolCall("getWeather", Map.of()))).thenRepeats()}.
     *
     * @throws IllegalStateException if the last step calls a tool, because an AI Service would then call the tool again
     *                               and again, or if the script already ends with {@code thenRepeats()}
     */
    public MockStreamingChatModel thenRepeats() {
        script.repeatLastStep();
        return this;
    }

    /**
     * Reports the given token usage in the response to every request, except for a step that gives the whole response:
     * that one reports exactly its own token usage. For different token usages per step, give the whole responses, see
     * {@link #thenStreams(ChatResponse)}.
     *
     * @param tokenUsage the token usage, or {@code null} for no token usage, which is also the default
     */
    public MockStreamingChatModel withTokenUsage(@Nullable TokenUsage tokenUsage) {
        script.defaultTokenUsage(tokenUsage);
        return this;
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public MockStreamingChatModel withListeners(ChatModelListener... listeners) {
        this.listeners = List.of(listeners);
        return this;
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public MockStreamingChatModel withListeners(List<ChatModelListener> listeners) {
        this.listeners = List.copyOf(listeners);
        return this;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    /**
     * Emits {@link PartialThinking} and {@link PartialResponse} events, then {@link PartialToolCall} and
     * {@link CompleteToolCall} events, then the {@link CompleteResponse}, on a new thread for each subscription, as many
     * as the subscriber requested. A failure is reported with {@code onError}, never thrown.
     */
    @Override
    public Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
        return subscriber -> {
            Step step = script.take(requests, chatRequest);
            Emission emission = new Emission(subscriber);
            subscriber.onSubscribe(emission);
            MockThreads.newThreadPerTask(MOCK_NAME).execute(() -> emission.emit(() -> script.answer(step, chatRequest)));
        };
    }

    /**
     * The subscription of one subscriber: it emits the events of one answer on the mock's thread, each one only after
     * the subscriber requested it.
     */
    private static final class Emission implements Subscription {

        private final Subscriber<? super ChatModelStreamingEvent> subscriber;
        private long demand;
        private boolean cancelled;
        private @Nullable IllegalArgumentException invalidDemand;

        private Emission(Subscriber<? super ChatModelStreamingEvent> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public synchronized void request(long n) {
            if (n <= 0) {
                invalidDemand = new IllegalArgumentException("Demand must be positive, got " + n);
            } else {
                demand = demand + n < 0 ? Long.MAX_VALUE : demand + n;
            }
            notifyAll();
        }

        @Override
        public synchronized void cancel() {
            cancelled = true;
            notifyAll();
        }

        private void emit(Supplier<Step> answer) {
            Step step;
            List<ChatModelStreamingEvent> events;
            try {
                step = answer.get();
                events = toEvents(step);
            } catch (RuntimeException | Error e) {
                // for example an AssertionError thrown by a function that computes the answer
                subscriber.onError(e);
                return;
            }
            try {
                for (ChatModelStreamingEvent event : events) {
                    if (!awaitDemand()) {
                        return;
                    }
                    subscriber.onNext(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException | Error e) {
                cancel(); // a subscriber that throws cancels the stream, as with a real model
                throw e;
            }
            if (endsWithoutTerminal()) {
                return;
            }
            if (step.failure() != null) {
                subscriber.onError(step.failure()); // a failure needs no demand, like a provider's error response
            } else {
                subscriber.onComplete();
            }
        }

        /**
         * Waits until the subscriber requests the next event, and returns {@code false} if the stream ends instead.
         */
        private boolean awaitDemand() throws InterruptedException {
            synchronized (this) {
                while (demand == 0 && !cancelled && invalidDemand == null) {
                    wait();
                }
                if (invalidDemand == null && !cancelled) {
                    demand--;
                    return true;
                }
            }
            endsWithoutTerminal();
            return false;
        }

        /**
         * Returns {@code true} if the stream ends without a terminal signal of the answer: the subscriber cancelled it,
         * or it requested a demand that is not positive, which is reported with {@code onError}.
         */
        private boolean endsWithoutTerminal() {
            IllegalArgumentException error;
            synchronized (this) {
                if (cancelled) {
                    return true;
                }
                if (invalidDemand == null) {
                    return false;
                }
                error = invalidDemand;
                cancelled = true;
            }
            subscriber.onError(error);
            return true;
        }
    }

    /**
     * The events of a step, without the failure that follows them, if any.
     */
    private static List<ChatModelStreamingEvent> toEvents(Step step) {
        List<ChatModelStreamingEvent> events = new ArrayList<>();
        step.thinkingTokens().forEach(token -> events.add(new PartialThinking(token)));
        step.tokens().forEach(token -> events.add(new PartialResponse(token)));
        if (step.failure() == null) {
            List<ToolExecutionRequest> toolCalls = step.aiMessage().toolExecutionRequests();
            for (int i = 0; i < toolCalls.size(); i++) {
                events.addAll(partialToolCalls(i, toolCalls.get(i)));
                events.add(new CompleteToolCall(i, toolCalls.get(i)));
            }
            events.add(new CompleteResponse(step.response()));
        }
        return events;
    }

    private static List<PartialToolCall> partialToolCalls(int index, ToolExecutionRequest toolCall) {
        if (isNullOrBlank(toolCall.name())) {
            return List.of(); // a partial tool call needs a name, which a hand-written tool call may not have
        }
        return ScriptedResponses.splitIntoTokens(toolCall.arguments()).stream()
                .map(token -> PartialToolCall.builder()
                        .index(index)
                        .id(toolCall.id())
                        .name(toolCall.name())
                        .partialArguments(token)
                        .build())
                .toList();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        Step step = script.take(requests, chatRequest);
        MockThreads.newThreadPerTask(MOCK_NAME).execute(() -> {
            Throwable failure;
            try {
                failure = stream(script.answer(step, chatRequest), handler);
            } catch (RuntimeException | Error e) {
                // without it, this thread would end without a final call, and a test waiting for one would hang
                failure = e;
            }
            if (failure != null) {
                // outside the try, so that an Error thrown by onError itself does not lead to a second onError
                Throwable error = failure;
                withLoggingExceptions(() -> handler.onError(error));
            }
        });
    }

    /**
     * Calls the handler for each event of the step, and returns the failure to report with {@code onError}: the
     * failure of the step, or {@code null} if it has none or the handler cancelled the stream.
     */
    private static @Nullable RuntimeException stream(Step step, StreamingChatResponseHandler handler) {
        StreamingHandle streamingHandle = new SimpleStreamingHandle();
        for (ChatModelStreamingEvent event : toEvents(step)) {
            if (streamingHandle.isCancelled()) {
                return null;
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
        return streamingHandle.isCancelled() ? null : step.failure();
    }

    /**
     * A copy of the requests received so far, in the order they arrived.
     */
    public List<ChatRequest> requests() {
        return requests.all();
    }

    /**
     * The only request received so far. To check it, for example:
     * {@code LangChain4jAssertions.assertThat(model.request()).hasLastUserMessageContaining("MS-777")}.
     *
     * @throws IllegalStateException if the mock received no request or several requests
     */
    public ChatRequest request() {
        return requests.only();
    }

    /**
     * The last request received so far. For example, the request that follows the tool results.
     *
     * @throws IllegalStateException if the mock received no request
     */
    public ChatRequest lastRequest() {
        return requests.last();
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
     * A model that answers the first request by streaming the given text, one character per token. Add the next steps
     * with {@code then...} methods, or answer every request with this text: {@code thatStreams("Hello!").thenRepeats()}.
     * To control exactly which tokens are streamed, use {@link #thatStreamsTokens(String...)}.
     * <p>
     * The text cannot be empty: a provider that streams no text completes with no text ({@code null}), not with
     * {@code ""}. To stream an answer without text, use {@link #thatStreamsTokens(String...)} without tokens. To complete
     * with exactly {@code ""} anyway, give the whole response: {@link #thatStreams(ChatResponse)}.
     *
     * @throws IllegalArgumentException if the text is empty
     */
    public static MockStreamingChatModel thatStreams(String text) {
        return thatStreams(AiMessage.from(text));
    }

    /**
     * A model that answers the first request by streaming exactly the given tokens, which together make up one answer:
     * {@code thatStreamsTokens("Hel", "lo")} streams {@code "Hel"} and then {@code "lo"}, and completes with
     * {@code "Hello"}. Without tokens, nothing is streamed and the answer has no text. Add the next steps with
     * {@code then...} methods.
     *
     * @throws IllegalArgumentException if a token is empty
     */
    public static MockStreamingChatModel thatStreamsTokens(String... tokens) {
        return new MockStreamingChatModel(List.of(Step.streaming(tokens)));
    }

    /**
     * A model that answers the first request by streaming exactly the given tokens, see
     * {@link #thatStreamsTokens(String...)}.
     *
     * @throws IllegalArgumentException if a token is empty
     */
    public static MockStreamingChatModel thatStreamsTokens(List<String> tokens) {
        return thatStreamsTokens(ensureNotNull(tokens, "tokens").toArray(String[]::new));
    }

    /**
     * A model that streams the given messages in order, one per request, one character per token. Add further steps
     * with {@code then...} methods. Use it for answers that {@link #thatCallsTool(String, Map)} cannot express, for
     * example several tool calls in one answer, built with {@link MockChatModel#toolCall(String, Map)}.
     *
     * @throws IllegalArgumentException if the text of a message is empty, see {@link #thatStreams(String)}
     */
    public static MockStreamingChatModel thatStreams(AiMessage first, AiMessage... next) {
        return thatStreams(Stream.concat(Stream.of(first), Stream.of(next)).toList());
    }

    /**
     * A model that streams the given messages in order, one per request, one character per token. Add further steps
     * with {@code then...} methods.
     *
     * @throws IllegalArgumentException if the text of a message is empty, see {@link #thatStreams(String)}
     */
    public static MockStreamingChatModel thatStreams(List<AiMessage> aiMessages) {
        ensureNotEmpty(aiMessages, "aiMessages");
        return new MockStreamingChatModel(aiMessages.stream()
                .map(aiMessage -> Step.responding(ensureNotEmptyText(aiMessage)))
                .toList());
    }

    /**
     * A model that answers the first request by streaming the message of the given response, one character per token,
     * and completing with exactly that response, including its metadata, for example its token usage, model name or
     * finish reason. Only tool calls without an id get the next id of the mock, see
     * {@link MockChatModel#toolCall(String, Map)}. Add the next steps with {@code then...} methods.
     */
    public static MockStreamingChatModel thatStreams(ChatResponse chatResponse) {
        return new MockStreamingChatModel(List.of(Step.responding(chatResponse)));
    }

    /**
     * A model that computes the answer to the first request from the request and streams it, one character per token.
     * Add the next steps with {@code then...} methods, or compute the answer to every request:
     * {@code thatStreams(request -> ...).thenRepeats()}. If the function throws, the request fails with that exception,
     * unchanged. An answer with the text {@code ""} fails the request with an {@link IllegalArgumentException}, see
     * {@link #thatStreams(String)}. The function runs on the thread that streams the answer.
     */
    public static MockStreamingChatModel thatStreams(Function<ChatRequest, AiMessage> answers) {
        ensureNotNull(answers, "answers");
        return new MockStreamingChatModel(
                List.of(Step.computing(request -> ensureNotEmptyText(answers.apply(request)))));
    }

    /**
     * A model that answers the first request with a call of the given tool. Add the next steps with {@code then...}
     * methods. The call gets the next id of the mock, see {@link MockChatModel#toolCall(String, Map)}.
     *
     * @param toolName  the name of the tool
     * @param arguments the arguments of the tool, by name
     */
    public static MockStreamingChatModel thatCallsTool(String toolName, Map<String, ?> arguments) {
        return thatCallsTool(toolName, MockChatModel.toJson(arguments));
    }

    /**
     * A model that answers the first request with a call of the given tool. Add the next steps with {@code then...}
     * methods. Use it for arguments that cannot be given as a map, for example invalid JSON.
     *
     * @param toolName      the name of the tool
     * @param jsonArguments the arguments of the tool, as JSON
     */
    public static MockStreamingChatModel thatCallsTool(String toolName, String jsonArguments) {
        return new MockStreamingChatModel(List.of(Step.callingTool(toolName, jsonArguments)));
    }

    private static AiMessage ensureNotEmptyText(AiMessage aiMessage) {
        if (aiMessage != null && "".equals(aiMessage.text())) {
            throw new IllegalArgumentException("The mock cannot stream the text \"\": a provider that streams no text"
                    + " completes with no text (null), not with \"\". To stream an answer without text, use"
                    + " thatStreamsTokens() or an AiMessage without text. To complete with exactly \"\" anyway, give"
                    + " the whole ChatResponse, for example with thatStreams(ChatResponse).");
        }
        return aiMessage;
    }

    /**
     * A model whose first request fails with the given exception, before anything is streamed: the handler gets it in
     * {@code onError}. To let every request fail, add {@link #thenRepeats()}. To let later requests succeed, add steps
     * with {@code then...} methods.
     */
    public static MockStreamingChatModel thatFails(RuntimeException exception) {
        return new MockStreamingChatModel(List.of(Step.failing(exception)));
    }
}
