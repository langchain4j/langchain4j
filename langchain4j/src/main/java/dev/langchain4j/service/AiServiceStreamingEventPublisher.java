package dev.langchain4j.service;

import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.AiServiceParamsUtil.chatRequestParameters;
import static dev.langchain4j.service.tool.ToolService.shouldReturnImmediately;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.RawStreamingEvent;
import dev.langchain4j.model.chat.response.StreamingEvent;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServiceStreamingEvent.AfterToolExecutionEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.BeforeToolExecutionEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.CompleteToolCallEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.FinalResponseEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.IntermediateResponseEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.PartialResponseEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.PartialThinkingEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.PartialToolCallEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.RawEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.RetrievedContentsEvent;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.service.tool.ToolServiceContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import mutiny.zero.BackpressureStrategy;
import mutiny.zero.Tube;
import mutiny.zero.TubeConfiguration;
import mutiny.zero.ZeroPublisher;

/**
 * A cold, truly non-blocking reactive stream for an AI Service method that returns a
 * {@link Flow.Publisher} of {@link AiServiceStreamingEvent}s.
 * <p>
 * It consumes the model's reactive {@link dev.langchain4j.model.chat.StreamingChatModel#chat(ChatRequest)}
 * publisher round by round, mapping each low-level {@link StreamingEvent} to the corresponding high-level
 * {@link AiServiceStreamingEvent} as it arrives ({@link PartialThinkingEvent}, {@link PartialResponseEvent},
 * {@link PartialToolCallEvent}, {@link CompleteToolCallEvent}). When a round requests tools, the round's
 * {@link ChatResponse} is emitted as an {@link IntermediateResponseEvent}, tools are executed without blocking
 * via {@link dev.langchain4j.service.tool.ToolExecutor#executeAsync(ToolExecutionRequest, InvocationContext)}
 * (emitting {@link BeforeToolExecutionEvent} and {@link AfterToolExecutionEvent} as they happen), and the next
 * round's publisher is subscribed to. Exactly one {@link FinalResponseEvent} carrying the final answer is
 * emitted last, followed by {@code onComplete}. If RAG content was retrieved, a single
 * {@link RetrievedContentsEvent} is emitted first.
 * <p>
 * The semantics mirror the handler-based {@link AiServiceTokenStream}/{@link TokenStream}: events from every
 * round are surfaced in order; no thread is ever blocked or pinned while a model response or a tool result is
 * in flight.
 * <p>
 * <b>Cancellation.</b> Cancelling the {@link Flow.Subscription} stops the interaction: the in-flight model
 * call is cancelled (for providers whose reactive stream supports it, this aborts the underlying HTTP
 * request), no further round is started, and no more events — including the terminal
 * {@link AiServiceStreamingEvent.FinalResponseEvent} and {@code onComplete}/{@code onError} — are emitted. A
 * tool execution that has <b>already started</b> is <b>not</b> interrupted: it runs to completion and its
 * result is discarded (Java cannot safely interrupt arbitrary tool code; this is a deliberate best-effort
 * contract, consistent with the {@code CompletableFuture} path).
 *
 * @since 1.17.0
 */
@Internal
public class AiServiceStreamingEventPublisher implements Flow.Publisher<AiServiceStreamingEvent> {

    // TODO output guardrails for the reactive streaming path (the handler-based path buffers and validates)

    private static final int BUFFER_SIZE = 256;

    private final List<ChatMessage> messages;
    private final ToolServiceContext toolServiceContext;
    private final List<Content> retrievedContents;
    private final AiServiceContext context;
    private final InvocationContext invocationContext;

    private final Flow.Publisher<AiServiceStreamingEvent> delegate;

    public AiServiceStreamingEventPublisher(AiServiceTokenStreamParameters parameters) {
        ensureNotNull(parameters, "parameters");
        this.messages = copy(ensureNotEmpty(parameters.messages(), "messages"));
        this.toolServiceContext = parameters.toolServiceContext();
        this.retrievedContents = copy(parameters.retrievedContents());
        this.context = ensureNotNull(parameters.context(), "context");
        ensureNotNull(this.context.streamingChatModel, "streamingChatModel");
        this.invocationContext = parameters.invocationContext();

        TubeConfiguration config = new TubeConfiguration()
                .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                .withBufferSize(BUFFER_SIZE);
        this.delegate = ZeroPublisher.create(config, tube -> new Loop(tube).start());
    }

    @Override
    public void subscribe(Flow.Subscriber<? super AiServiceStreamingEvent> subscriber) {
        delegate.subscribe(subscriber);
    }

    /**
     * Maps a rich {@link AiServiceStreamingEvent} stream to a text-only {@link String} stream: it emits the
     * text of each {@link PartialResponseEvent} and drops every other event. Used to satisfy AI Service methods
     * that return a {@code Publisher<String>} (or a {@code Flux<String>}/{@code Multi<String>} via a
     * {@link dev.langchain4j.spi.services.PublisherAdapter}).
     */
    public static Flow.Publisher<String> toTextPublisher(Flow.Publisher<AiServiceStreamingEvent> events) {
        TubeConfiguration config = new TubeConfiguration()
                .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                .withBufferSize(BUFFER_SIZE);
        return ZeroPublisher.create(config, tube -> events.subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                if (tube.cancelled()) {
                    subscription.cancel();
                    return;
                }
                tube.whenCancelled(subscription::cancel);
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AiServiceStreamingEvent event) {
                if (tube.cancelled()) {
                    return;
                }
                if (event instanceof PartialResponseEvent partialResponseEvent) {
                    tube.send(partialResponseEvent.partialResponse().text());
                }
            }

            @Override
            public void onError(Throwable error) {
                if (!tube.cancelled()) {
                    tube.fail(error);
                }
            }

            @Override
            public void onComplete() {
                if (!tube.cancelled()) {
                    tube.complete();
                }
            }
        }));
    }

    /**
     * Holds the per-subscription state of one AI Service invocation. A fresh instance is created on each
     * {@code subscribe()} (the publisher is cold).
     */
    private final class Loop {

        private final Tube<AiServiceStreamingEvent> tube;
        private final ChatMemory temporaryMemory;
        private final AtomicReference<Flow.Subscription> modelSubscription = new AtomicReference<>();
        private final AtomicReference<CompletableFuture<?>> toolsFuture = new AtomicReference<>();

        // The reactive Publisher path runs tools concurrently by default (off the model delivery thread),
        // unless the user explicitly disabled it via executeToolsConcurrently(false). When concurrent, each
        // tool is started as soon as its CompleteToolCall arrives (rather than waiting for the whole model
        // response), so concurrent tools overlap each other and the tail of the model stream — mirroring the
        // handler-based path's onCompleteToolCall scheduling. When sequential, tools run inline (toolExecutor
        // is null).
        private final Executor toolExecutor = context.toolService.effectiveToolExecutor(true);
        private final boolean startToolsEagerly = toolExecutor != null;

        private TokenUsage tokenUsage = new TokenUsage();
        private int roundTripsLeft = context.toolService.maxToolCallingRoundTrips();

        private Loop(Tube<AiServiceStreamingEvent> tube) {
            this.tube = tube;
            this.temporaryMemory = initTemporaryMemory();
        }

        private void start() {
            tube.whenCancelled(() -> {
                Flow.Subscription subscription = modelSubscription.get();
                if (subscription != null) {
                    subscription.cancel();
                }
                CompletableFuture<?> tools = toolsFuture.get();
                if (tools != null) {
                    tools.cancel(true);
                }
            });

            // Mirrors TokenStream.onRetrieved: surface retrieved RAG content once, before the first response.
            if (retrievedContents != null && !retrievedContents.isEmpty()) {
                tube.send(new RetrievedContentsEvent(retrievedContents, invocationContext));
            }

            List<ToolSpecification> effectiveTools =
                    toolServiceContext != null ? toolServiceContext.effectiveTools() : null;
            ChatRequestParameters parameters =
                    chatRequestParameters(invocationContext.methodArguments(), effectiveTools);
            ChatRequest chatRequest = context.chatRequestTransformer.apply(
                    ChatRequest.builder().messages(messages).parameters(parameters).build(),
                    invocationContext.chatMemoryId());

            // The first round's request-issued event is fired here; subsequent rounds' events are fired by
            // ToolService.prepareNextChatRequest (shared with the other AI Service modes).
            fireRequestIssuedEvent(chatRequest);

            startRound(chatRequest, toolServiceContext, parameters);
        }

        private void startRound(
                ChatRequest chatRequest, ToolServiceContext currentToolContext, ChatRequestParameters parameters) {

            if (tube.cancelled()) {
                return;
            }

            // Tools started eagerly (on CompleteToolCall) for this round, keyed by request in arrival order.
            // Empty unless tools execute concurrently. Accessed only from the (serial) model delivery thread.
            Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> startedTools = new LinkedHashMap<>();

            context.streamingChatModel.chat(chatRequest).subscribe(new Flow.Subscriber<StreamingEvent>() {

                private ChatResponse roundResponse;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    modelSubscription.set(subscription);
                    if (tube.cancelled()) {
                        subscription.cancel();
                        return;
                    }
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(StreamingEvent event) {
                    if (tube.cancelled()) {
                        return;
                    }
                    // Map each low-level model event to its high-level AI Service counterpart. The terminal
                    // ChatResponse is held back: the AI Service stream decides whether the round is an
                    // intermediate (tool-calling) one or the final answer. Raw provider events are relayed
                    // as-is via RawEvent.
                    if (event instanceof ChatResponse chatResponse) {
                        this.roundResponse = chatResponse;
                    } else if (event instanceof PartialResponse partialResponse) {
                        tube.send(new PartialResponseEvent(partialResponse, invocationContext));
                    } else if (event instanceof PartialThinking partialThinking) {
                        tube.send(new PartialThinkingEvent(partialThinking, invocationContext));
                    } else if (event instanceof PartialToolCall partialToolCall) {
                        tube.send(new PartialToolCallEvent(partialToolCall, invocationContext));
                    } else if (event instanceof CompleteToolCall completeToolCall) {
                        tube.send(new CompleteToolCallEvent(completeToolCall, invocationContext));
                        if (startToolsEagerly) {
                            ToolExecutionRequest toolRequest = completeToolCall.toolExecutionRequest();
                            startedTools.put(toolRequest, context.toolService.startTool(
                                    toolRequest,
                                    currentToolContext.toolExecutors(),
                                    invocationContext,
                                    Loop.this::emitBeforeToolExecution,
                                    Loop.this::emitAfterToolExecution,
                                    toolExecutor));
                        }
                    } else if (event instanceof RawStreamingEvent rawStreamingEvent) {
                        tube.send(new RawEvent(rawStreamingEvent, invocationContext));
                    }
                }

                @Override
                public void onError(Throwable error) {
                    fail(error);
                }

                @Override
                public void onComplete() {
                    if (tube.cancelled()) {
                        return;
                    }
                    try {
                        processRound(chatRequest, currentToolContext, parameters, roundResponse, startedTools);
                    } catch (Throwable error) {
                        fail(error);
                    }
                }
            });
        }

        private void processRound(
                ChatRequest chatRequest,
                ToolServiceContext currentToolContext,
                ChatRequestParameters parameters,
                ChatResponse chatResponse,
                Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> startedTools) {

            fireResponseReceivedEvent(chatRequest, chatResponse);

            AiMessage aiMessage = chatResponse.aiMessage();
            addToMemory(aiMessage);

            if (!aiMessage.hasToolExecutionRequests()) {
                emitFinalResponse(chatResponse, aiMessage);
                return;
            }

            if (roundTripsLeft-- == 0) {
                throw runtime(
                        "Something is wrong, exceeded %s tool calling round trips (maxToolCallingRoundTrips)",
                        context.toolService.maxToolCallingRoundTrips());
            }

            tube.send(new IntermediateResponseEvent(chatResponse, invocationContext));

            List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();

            CompletableFuture<Map<ToolExecutionRequest, ToolExecutionResult>> toolResultsFuture =
                    startToolsEagerly
                            ? combineEagerlyStartedTools(toolRequests, startedTools, currentToolContext)
                            : context.toolService.executeToolsAsync(
                                    toolRequests,
                                    currentToolContext.toolExecutors(),
                                    invocationContext,
                                    this::emitBeforeToolExecution,
                                    this::emitAfterToolExecution,
                                    toolExecutor);
            toolsFuture.set(toolResultsFuture);

            toolResultsFuture.whenComplete((toolResults, error) -> {
                if (error != null) {
                    fail(error);
                    return;
                }
                if (tube.cancelled()) {
                    return;
                }
                try {
                    afterToolsExecuted(chatResponse, aiMessage, currentToolContext, parameters, toolRequests, toolResults);
                } catch (Throwable t) {
                    fail(t);
                }
            });
        }

        /**
         * Combines the tools that were already started eagerly (on their CompleteToolCall) into a single
         * future of results, in request order. Any requested tool that was not started eagerly (e.g. a
         * provider emitted no matching CompleteToolCall) is started now, so the result set is always complete.
         */
        private CompletableFuture<Map<ToolExecutionRequest, ToolExecutionResult>> combineEagerlyStartedTools(
                List<ToolExecutionRequest> toolRequests,
                Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> startedTools,
                ToolServiceContext currentToolContext) {

            Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> orderedFutures = new LinkedHashMap<>();
            for (ToolExecutionRequest toolRequest : toolRequests) {
                CompletableFuture<ToolExecutionResult> future = startedTools.get(toolRequest);
                if (future == null) {
                    future = context.toolService.startTool(
                            toolRequest,
                            currentToolContext.toolExecutors(),
                            invocationContext,
                            this::emitBeforeToolExecution,
                            this::emitAfterToolExecution,
                            toolExecutor);
                }
                orderedFutures.put(toolRequest, future);
            }
            return ToolService.combineToolResults(orderedFutures);
        }

        private void afterToolsExecuted(
                ChatResponse chatResponse,
                AiMessage aiMessage,
                ToolServiceContext currentToolContext,
                ChatRequestParameters parameters,
                List<ToolExecutionRequest> toolRequests,
                Map<ToolExecutionRequest, ToolExecutionResult> toolResults) {

            // Shared per-round bookkeeping (memory writes, ToolExecutedEvent, returnBehavior accumulation).
            // The per-tool BeforeToolExecution/ToolExecution stream events were already emitted by the
            // tube::send consumers passed to executeToolsAsync, as the tools ran.
            ToolService.ToolResultsOutcome outcome = context.toolService.processToolResults(
                    context,
                    toolRequests,
                    toolResults,
                    new ArrayList<>(),
                    getMemory(),
                    null,
                    invocationContext,
                    currentToolContext);

            if (shouldReturnImmediately(outcome.anyToolErrored(), outcome.returnBehaviors())) {
                emitFinalResponse(chatResponse, aiMessage);
                return;
            }

            tokenUsage = TokenUsage.sum(tokenUsage, chatResponse.metadata().tokenUsage());

            // Shared next-request construction (messages-to-send, dynamic providers, tool search, transformer,
            // request-issued event), identical to the sync/async/TokenStream modes.
            ToolService.NextChatRequest next = context.toolService.prepareNextChatRequest(
                    context,
                    invocationContext.chatMemoryId(),
                    getMemory(),
                    null,
                    invocationContext,
                    currentToolContext,
                    toolResults,
                    parameters);

            startRound(next.chatRequest(), next.toolServiceContext(), next.parameters());
        }

        private void emitFinalResponse(ChatResponse chatResponse, AiMessage aiMessage) {
            ChatResponse finalChatResponse = ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(chatResponse.metadata().toBuilder()
                            .tokenUsage(tokenUsage.add(chatResponse.metadata().tokenUsage()))
                            .build())
                    .build();
            fireInvocationComplete(finalChatResponse);
            tube.send(new FinalResponseEvent(finalChatResponse, invocationContext));
            tube.complete();
        }

        private void fail(Throwable error) {
            if (tube.cancelled()) {
                return;
            }
            // Unwrap the CompletionException that future composition wraps around the real cause, so consumers
            // see the actual error (consistent with the CompletableFuture single-response path).
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause()
                    : error;
            fireErrorReceived(cause);
            tube.fail(cause);
        }

        private ChatMemory initTemporaryMemory() {
            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(Integer.MAX_VALUE);
            if (!context.hasChatMemory()) {
                chatMemory.add(messages);
            }
            return chatMemory;
        }

        private ChatMemory getMemory() {
            return context.hasChatMemory()
                    ? context.chatMemoryService.getOrCreateChatMemory(invocationContext.chatMemoryId())
                    : temporaryMemory;
        }

        private void addToMemory(ChatMessage chatMessage) {
            getMemory().add(chatMessage);
        }

        private void emitBeforeToolExecution(BeforeToolExecution beforeToolExecution) {
            tube.send(new BeforeToolExecutionEvent(beforeToolExecution, invocationContext));
        }

        private void emitAfterToolExecution(ToolExecution toolExecution) {
            tube.send(new AfterToolExecutionEvent(toolExecution, invocationContext));
        }

        private void fireRequestIssuedEvent(ChatRequest chatRequest) {
            context.eventListenerRegistrar.fireEvent(AiServiceRequestIssuedEvent.builder()
                    .invocationContext(invocationContext)
                    .request(chatRequest)
                    .build());
        }

        private void fireResponseReceivedEvent(ChatRequest chatRequest, ChatResponse chatResponse) {
            context.eventListenerRegistrar.fireEvent(AiServiceResponseReceivedEvent.builder()
                    .invocationContext(invocationContext)
                    .request(chatRequest)
                    .response(chatResponse)
                    .build());
        }

        private void fireInvocationComplete(ChatResponse finalChatResponse) {
            context.eventListenerRegistrar.fireEvent(AiServiceCompletedEvent.builder()
                    .invocationContext(invocationContext)
                    .result(finalChatResponse)
                    .build());
        }

        private void fireErrorReceived(Throwable error) {
            context.eventListenerRegistrar.fireEvent(AiServiceErrorEvent.builder()
                    .invocationContext(invocationContext)
                    .error(error)
                    .build());
        }
    }
}
