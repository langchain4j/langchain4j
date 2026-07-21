package dev.langchain4j.service;

import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
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
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModelHelper;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.RawStreamingEvent;
import dev.langchain4j.model.chat.response.StreamingEvent;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.CompensationReason;
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
import dev.langchain4j.service.AiServiceStreamingEvent.ToolCompensatedEvent;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import mutiny.zero.BackpressureStrategy;
import mutiny.zero.Tube;
import mutiny.zero.TubeConfiguration;
import mutiny.zero.ZeroPublisher;

/**
 * A cold, non-blocking reactive stream for an AI Service method that returns a
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
 * <b>Back-pressure.</b> The model's streaming response is consumed with unbounded demand and its events are
 * relayed to the subscriber through a <b>bounded</b> buffer ({@value #DEFAULT_BUFFER_SIZE} entries by default,
 * overridable per AI Service via {@code AiServices.streamingBufferSize(int)}). The model stream <i>could</i> be
 * throttled — its subscription demand maps to TCP / HTTP-2 flow control (see {@code onSubscribe}) — but we
 * deliberately request unbounded: throttling the socket cannot slow token <i>generation</i> (already produced and
 * billed server-side) and only risks an idle-timeout reset of the in-flight response, so the sole thing it would
 * protect is heap — which the bounded buffer already guards. Consequently a subscriber that consumes slower than
 * the model produces and overflows the buffer terminates with an {@link IllegalStateException} rather than
 * dropping events (which would corrupt the assembled response) or buffering unbounded (which risks
 * {@link OutOfMemoryError}).
 * Consumers must therefore not block in {@code onNext}; offload heavy per-event work. If a slow-but-correct
 * consumer trips the buffer on a long response, raise it via {@code AiServices.streamingBufferSize(int)}
 * (or set it to {@link Integer#MAX_VALUE} for an effectively unbounded buffer, accepting the OOM risk).
 * <p>
 * <b>Cancellation.</b> Cancelling the {@link Flow.Subscription} stops the interaction: the in-flight model
 * call is cancelled (for providers whose reactive stream supports it, this aborts the underlying HTTP
 * request), no further round is started, and no more events — including the terminal
 * {@link AiServiceStreamingEvent.FinalResponseEvent} and {@code onComplete}/{@code onError} — are emitted. A
 * tool execution that has <b>already started</b> is <b>not</b> interrupted: it runs to completion and its
 * result is discarded (Java cannot safely interrupt arbitrary tool code; this is a deliberate best-effort
 * contract, consistent with the {@code CompletableFuture} path).
 *
 * @since 1.19.0
 */
@Internal
public class AiServiceStreamingEventPublisher implements Flow.Publisher<AiServiceStreamingEvent> {

    /**
     * Default size of the bounded back-pressure buffer (see the class-level "Back-pressure" note), used when an AI
     * Service does not override it via {@code AiServices.streamingBufferSize(int)}.
     */
    public static final int DEFAULT_BUFFER_SIZE = 16384;

    private final List<ChatMessage> messages;
    private final ToolServiceContext toolServiceContext;
    private final List<Content> retrievedContents;
    private final AiServiceContext context;
    private final InvocationContext invocationContext;
    private final GuardrailRequestParams commonGuardrailParams;
    private final Object methodKey;

    private final Flow.Publisher<AiServiceStreamingEvent> delegate;

    public AiServiceStreamingEventPublisher(AiServiceTokenStreamParameters parameters, int bufferSize) {
        ensureNotNull(parameters, "parameters");
        this.messages = copy(ensureNotEmpty(parameters.messages(), "messages"));
        this.toolServiceContext = parameters.toolServiceContext();
        this.retrievedContents = copy(parameters.retrievedContents());
        this.context = ensureNotNull(parameters.context(), "context");
        ensureNotNull(this.context.streamingChatModel, "streamingChatModel");
        this.invocationContext = parameters.invocationContext();
        this.commonGuardrailParams = parameters.commonGuardrailParams();
        this.methodKey = parameters.methodKey();

        TubeConfiguration config = new TubeConfiguration()
                .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                .withBufferSize(bufferSize);
        this.delegate = ZeroPublisher.create(config, tube -> new Loop(tube).start());
    }

    @Override
    public void subscribe(Flow.Subscriber<? super AiServiceStreamingEvent> subscriber) {
        delegate.subscribe(subscriber);
    }

    /**
     * Maps a rich {@link AiServiceStreamingEvent} stream to a text-only {@link String} stream. Used to satisfy AI
     * Service methods that return a {@code Publisher<String>} (or a {@code Flux<String>}/{@code Multi<String>} via a
     * {@link dev.langchain4j.spi.services.PublisherAdapter}). The concatenation of all emitted strings equals the
     * final answer.
     * <p>
     * When there are <b>no output guardrails</b>, the text of each {@link PartialResponseEvent} is emitted as it
     * streams (true token-by-token streaming) and every other event is dropped.
     * <p>
     * When there <b>are output guardrails</b>, an output guardrail may rewrite the final answer, so the individual
     * partial chunks are no longer authoritative. In that case the partials are dropped and the (possibly rewritten)
     * text of the single {@link FinalResponseEvent} is emitted instead. This loses no streaming granularity in
     * practice: with output guardrails the partials are buffered upstream and only released once the assembled
     * response has passed validation, so they would arrive as a single burst at the very end anyway.
     *
     * @param events             the rich event stream to adapt
     * @param hasOutputGuardrails whether the AI Service method has output guardrails configured
     * @param bufferSize         the back-pressure buffer size
     */
    public static Flow.Publisher<String> toTextPublisher(
            Flow.Publisher<AiServiceStreamingEvent> events, boolean hasOutputGuardrails, int bufferSize) {
        TubeConfiguration config = new TubeConfiguration()
                .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                .withBufferSize(bufferSize);
        return ZeroPublisher.create(config, tube -> events.subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                if (tube.cancelled()) {
                    subscription.cancel();
                    return;
                }
                tube.whenTerminates(subscription::cancel);
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AiServiceStreamingEvent event) {
                if (tube.cancelled()) {
                    return;
                }
                if (hasOutputGuardrails) {
                    // A guardrail may rewrite the answer, so the buffered partials are stale — emit the
                    // authoritative final text instead.
                    if (event instanceof FinalResponseEvent finalResponseEvent) {
                        String text = finalResponseEvent.chatResponse().aiMessage().text();
                        if (text != null) {
                            tube.send(text);
                        }
                    }
                } else if (event instanceof PartialResponseEvent partialResponseEvent) {
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
        private final AtomicReference<InflightRound> inflightRound = new AtomicReference<>();
        private final AtomicReference<CompletableFuture<?>> guardrailsFuture = new AtomicReference<>();
        private final List<ToolService.CompensableToolExecution> compensableExecutions =
                context.toolService.newCompensableExecutionsAccumulator();
        private final boolean hasOutputGuardrails = context.guardrailService().hasOutputGuardrails(methodKey);
        private final List<PartialResponse> bufferedPartialResponses = new ArrayList<>();
        private ChatExecutor chatExecutor;
        private final Executor toolExecutor = context.toolService.effectiveToolExecutor();
        private TokenUsage tokenUsage = new TokenUsage();
        private int roundTripsLeft = context.toolService.maxToolCallingRoundTrips();

        private Loop(Tube<AiServiceStreamingEvent> tube) {
            this.tube = tube;
            this.temporaryMemory = initTemporaryMemory();
        }

        private void start() {
            tube.whenTerminates(() -> {
                Flow.Subscription subscription = modelSubscription.get();
                if (subscription != null) {
                    subscription.cancel();
                }
                CompletableFuture<?> guardrails = guardrailsFuture.get();
                if (guardrails != null) {
                    guardrails.cancel(true);
                }
            });
            tube.whenCancelled(() -> {
                InflightRound round = underAccumulatorLock(() -> inflightRound.getAndSet(null));
                if (round != null) {
                    round.toolsFuture().whenComplete((combined, error) -> doCancellationCompensation(
                            round.toolRequests(), combined != null ? combined.results() : null));
                } else {
                    doCancellationCompensation(null, null);
                }
            });

            try {
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

                chatExecutor = ChatExecutor.builder(context.streamingChatModel)
                        .chatRequest(chatRequest)
                        .invocationContext(invocationContext)
                        .eventListenerRegistrar(context.eventListenerRegistrar)
                        .build();

                fireRequestIssuedEvent(chatRequest);

                startRound(chatRequest, toolServiceContext, parameters);
            } catch (Throwable error) {
                fail(error);
            }
        }

        private void startRound(
                ChatRequest chatRequest, ToolServiceContext currentToolContext, ChatRequestParameters parameters) {

            if (tube.cancelled()) {
                return;
            }

            Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> startedTools = new LinkedHashMap<>();

            context.streamingChatModel.chat(chatRequest).subscribe(new Flow.Subscriber<>() {

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
                    if (event instanceof CompleteResponse completeResponse) {
                        this.roundResponse = completeResponse.chatResponse();
                    } else if (event instanceof PartialResponse partialResponse) {
                        if (hasOutputGuardrails) {
                            bufferedPartialResponses.add(partialResponse);
                        } else {
                            tube.send(new PartialResponseEvent(partialResponse, invocationContext));
                        }
                    } else if (event instanceof PartialThinking partialThinking) {
                        tube.send(new PartialThinkingEvent(partialThinking, invocationContext));
                    } else if (event instanceof PartialToolCall partialToolCall) {
                        tube.send(new PartialToolCallEvent(partialToolCall, invocationContext));
                    } else if (event instanceof CompleteToolCall completeToolCall) {
                        tube.send(new CompleteToolCallEvent(completeToolCall, invocationContext));
                        ToolExecutionRequest toolRequest = completeToolCall.toolExecutionRequest();
                        startedTools.put(toolRequest, context.toolService.startTool(
                                toolRequest,
                                currentToolContext.toolExecutors(),
                                invocationContext,
                                Loop.this::emitBeforeToolExecution,
                                Loop.this::emitAfterToolExecution,
                                toolExecutor));
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

            getMemory().addAsync(List.of(aiMessage)).whenComplete((ignored, error) -> {
                if (error != null) {
                    fail(error);
                    return;
                }
                if (tube.cancelled()) {
                    return;
                }
                try {
                    afterAiMessageAdded(currentToolContext, parameters, chatResponse, aiMessage, startedTools);
                } catch (Throwable t) {
                    fail(t);
                }
            });
        }

        private void afterAiMessageAdded(
                ToolServiceContext currentToolContext,
                ChatRequestParameters parameters,
                ChatResponse chatResponse,
                AiMessage aiMessage,
                Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> startedTools) {

            if (!aiMessage.hasToolExecutionRequests()) {
                emitFinalResponse(chatResponse, aiMessage, currentToolContext, parameters);
                return;
            }

            if (roundTripsLeft-- == 0) {
                throw runtime(
                        "Something is wrong, exceeded %s tool calling round trips (maxToolCallingRoundTrips)",
                        context.toolService.maxToolCallingRoundTrips());
            }

            tube.send(new IntermediateResponseEvent(chatResponse, invocationContext));

            bufferedPartialResponses.clear();

            List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();

            CompletableFuture<ToolService.CombinedToolResults> toolResultsFuture =
                    combineEagerlyStartedTools(toolRequests, startedTools, currentToolContext);
            InflightRound round = new InflightRound(toolRequests, toolResultsFuture);
            inflightRound.set(round);

            toolResultsFuture.whenComplete((combined, error) -> {
                if (error != null) {
                    fail(error);
                    return;
                }
                String[] failedToolNameHolder = new String[1];
                boolean won = underAccumulatorLock(() -> {
                    if (!inflightRound.compareAndSet(round, null)) {
                        return false;
                    }
                    if (compensableExecutions != null) {
                        failedToolNameHolder[0] = context.toolService.collectCompensableRound(
                                toolRequests, combined.results(), compensableExecutions, invocationContext);
                    }
                    return true;
                });
                if (!won) {
                    return; // the cancel path claimed the round; it compensates
                }
                if (tube.cancelled()) {
                    doCancellationCompensation(null, null); // this round already collected above
                    return;
                }
                try {
                    afterToolsExecuted(
                            chatResponse,
                            aiMessage,
                            currentToolContext,
                            parameters,
                            toolRequests,
                            combined.results(),
                            combined.firstError(),
                            failedToolNameHolder[0]);
                } catch (Throwable t) {
                    fail(t);
                }
            });
        }

        /**
         * Runs {@code action} while holding the compensable-executions accumulator monitor, so the inflight-round
         * handoff and the round's collection are ordered with the cancel path (which reads the handoff under the same
         * monitor). When compensation is disabled the accumulator is {@code null}; there is then nothing to order, so
         * the action runs without a lock.
         */
        private <T> T underAccumulatorLock(java.util.function.Supplier<T> action) {
            if (compensableExecutions == null) {
                return action.get();
            }
            synchronized (compensableExecutions) {
                return action.get();
            }
        }

        /**
         * Rolls back the compensable tools on cancellation. Safe to call more than once and from either the cancel
         * signal or a racing round completion: {@code compensateOnCancellationAsync} snapshots-and-clears the shared
         * accumulator under its monitor, so each compensable tool is rolled back exactly once (the first caller to
         * snapshot it wins; later calls see an empty accumulator). Only round-data-bearing calls collect a round, and
         * those are mutually exclusive via {@code inflightRound}, so no round is collected twice. Stream events are not
         * delivered after a cancel, so only the observability {@code ToolCompensatedEvent} fires here.
         */
        private void doCancellationCompensation(
                List<ToolExecutionRequest> currentRoundRequests,
                Map<ToolExecutionRequest, ToolExecutionResult> currentRoundResults) {
            context.toolService.compensateOnCancellationAsync(
                    currentRoundRequests,
                    currentRoundResults,
                    compensableExecutions,
                    getMemory(),
                    null,
                    invocationContext,
                    context.eventListenerRegistrar,
                    null);
        }

        private record InflightRound(
                List<ToolExecutionRequest> toolRequests,
                CompletableFuture<ToolService.CombinedToolResults> toolsFuture) {}

        /**
         * Combines the tools that were already started eagerly (on their CompleteToolCall) into a single
         * future of results, in request order. Any requested tool that was not started eagerly (e.g. a
         * provider emitted no matching CompleteToolCall) is started now, so the result set is always complete.
         */
        private CompletableFuture<ToolService.CombinedToolResults> combineEagerlyStartedTools(
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
            return ToolService.combineToolResultsCollectingErrors(orderedFutures);
        }

        private void afterToolsExecuted(
                ChatResponse chatResponse,
                AiMessage aiMessage,
                ToolServiceContext currentToolContext,
                ChatRequestParameters parameters,
                List<ToolExecutionRequest> toolRequests,
                Map<ToolExecutionRequest, ToolExecutionResult> toolResults,
                Throwable firstError,
                String preCollectedFailedToolName) {

            ToolService.ToolResultsOutcome outcome = context.toolService.processToolResults(
                    context,
                    toolRequests,
                    toolResults,
                    new ArrayList<>(),
                    invocationContext,
                    currentToolContext);

            CompletableFuture<Void> compensated = context.toolService.compensateIfNeededAsync(
                    toolRequests,
                    toolResults,
                    outcome.resultMessages(),
                    compensableExecutions,
                    outcome.anyToolErrored(),
                    getMemory(),
                    messages,
                    invocationContext,
                    CompensationReason.TOOL_EXECUTION_FAILED,
                    context.eventListenerRegistrar,
                    (toolExecution, reason) ->
                            tube.send(new ToolCompensatedEvent(toolExecution, reason, invocationContext)),
                    true,
                    preCollectedFailedToolName);

            compensated.whenComplete((ignoredCompensation, compensationError) -> {
                if (tube.cancelled()) {
                    doCancellationCompensation(null, null);
                    return;
                }
                // The default asynchronous ToolExecutionErrorHandler rethrows a tool execution error. The tools that
                // succeeded have now been compensated; fail the stream with the tool error, attaching any
                // compensation-infra failure (e.g. a failing memory rewrite) as suppressed so the root cause is not
                // masked. When there was no tool error, surface the compensation failure directly.
                if (firstError != null) {
                    if (compensationError != null) {
                        firstError.addSuppressed(unwrapCompletionException(compensationError));
                    }
                    fail(firstError);
                    return;
                }
                if (compensationError != null) {
                    fail(compensationError);
                    return;
                }
                if (shouldReturnImmediately(outcome.anyToolErrored(), outcome.returnBehaviors())) {
                    emitFinalResponse(chatResponse, aiMessage, currentToolContext, parameters);
                    return;
                }

                tokenUsage = TokenUsage.sum(tokenUsage, chatResponse.metadata().tokenUsage());

                context.toolService
                        .persistToolResultsAndResolveMessages(
                                context, getMemory(), null, outcome.resultMessages(), invocationContext)
                        .thenAccept(nextMessages -> {
                            if (tube.cancelled()) {
                                return;
                            }
                            ToolService.NextChatRequest next = context.toolService.prepareNextChatRequest(
                                    context,
                                    invocationContext.chatMemoryId(),
                                    nextMessages,
                                    invocationContext,
                                    currentToolContext,
                                    toolResults,
                                    parameters);

                            startRound(next.chatRequest(), next.toolServiceContext(), next.parameters());
                        })
                        .exceptionally(error -> {
                            fail(error);
                            return null;
                        });
            });
        }

        private void emitFinalResponse(
                ChatResponse chatResponse,
                AiMessage aiMessage,
                ToolServiceContext currentToolContext,
                ChatRequestParameters parameters) {
            ChatResponse finalChatResponse = ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(chatResponse.metadata().toBuilder()
                            .tokenUsage(tokenUsage.add(chatResponse.metadata().tokenUsage()))
                            .build())
                    .build();

            if (!hasOutputGuardrails || commonGuardrailParams == null) {
                flushBufferedPartialResponses();
                completeWithFinalResponse(finalChatResponse);
                return;
            }

            GuardrailRequestParams guardrailParams =
                    commonGuardrailParams.toBuilder().chatMemory(getMemory()).build();

            ChatExecutor toolAwareRepromptExecutor = ToolAwareRepromptExecutor.wrapAsync(
                    chatExecutor,
                    context,
                    invocationContext.chatMemoryId(),
                    parameters,
                    invocationContext,
                    currentToolContext,
                    request -> StreamingChatModelHelper.chatAsync(context.streamingChatModel, request));
            OutputGuardrailRequest request = OutputGuardrailRequest.builder()
                    .responseFromLLM(finalChatResponse)
                    .chatExecutor(toolAwareRepromptExecutor)
                    .requestParams(guardrailParams)
                    .build();

            // Output guardrails (and any reprompt round-trips to the model) run on the virtual-thread executor,
            // never on the model-delivery thread: a blocking guardrail or a reprompt blocks a virtual thread
            // (non-pinning), not the delivery thread. TODO
            CompletableFuture<ChatResponse> guarded =
                    context.guardrailService().executeGuardrailsAsync(methodKey, request);
            guardrailsFuture.set(guarded);
            guarded.whenComplete((guardedResponse, error) -> {
                if (error != null) {
                    fail(error);
                    return;
                }
                if (tube.cancelled()) {
                    return;
                }
                try {
                    flushBufferedPartialResponses();
                    completeWithFinalResponse(guardedResponse);
                } catch (Throwable t) {
                    fail(t);
                }
            });
        }

        private void flushBufferedPartialResponses() {
            for (PartialResponse partialResponse : bufferedPartialResponses) {
                tube.send(new PartialResponseEvent(partialResponse, invocationContext));
            }
            bufferedPartialResponses.clear();
        }

        private void completeWithFinalResponse(ChatResponse finalChatResponse) {
            fireInvocationComplete(finalChatResponse);
            tube.send(new FinalResponseEvent(finalChatResponse, invocationContext));
            tube.complete();
        }

        private void fail(Throwable error) {
            if (tube.cancelled()) {
                return;
            }
            Throwable cause = unwrapCompletionException(error);
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
