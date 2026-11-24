package dev.langchain4j.service;

import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.AiServiceParamsUtil.chatRequestParameters;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.internal.CurrentInvocation;
import dev.langchain4j.internal.HandlerWithoutInvocation;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.service.memory.ChatMemoryService;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.service.tool.ToolService.CompensableToolExecution;
import dev.langchain4j.service.tool.ToolServiceContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles response from a language model for AI Service that is streamed token-by-token. Handles both regular (text)
 * responses and responses with the request to execute one or multiple tools.
 */
@Internal
class AiServiceStreamingResponseHandler implements StreamingChatResponseHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AiServiceStreamingResponseHandler.class);

    private final ChatExecutor chatExecutor;
    private final ChatRequest chatRequest;
    private final AiServiceContext context;
    private final InvocationContext invocationContext;
    private final AtomicBoolean terminated = new AtomicBoolean();
    private volatile ObservedStreamingHandle observedStreamingHandle;
    private final GuardrailRequestParams commonGuardrailParams;
    private final Object methodKey;

    private final Consumer<String> partialResponseHandler;
    private final BiConsumer<PartialResponse, PartialResponseContext> partialResponseWithContextHandler;
    private final Consumer<PartialThinking> partialThinkingHandler;
    private final BiConsumer<PartialThinking, PartialThinkingContext> partialThinkingWithContextHandler;
    private final Consumer<PartialToolCall> partialToolCallHandler;
    private final BiConsumer<PartialToolCall, PartialToolCallContext> partialToolCallWithContextHandler;
    private final Consumer<BeforeToolExecution> beforeToolExecutionHandler;
    private final Consumer<Object> rawEventHandler;
    private final Consumer<ToolExecution> toolExecutionHandler;
    private final Consumer<ChatResponse> intermediateResponseHandler;
    private final Consumer<ChatResponse> completeResponseHandler;

    private final Consumer<Throwable> errorHandler;

    private final ChatMemory temporaryMemory;
    private final TokenUsage tokenUsage;

    private final ToolServiceContext toolServiceContext;
    private final Map<String, ToolExecutor> toolExecutors;
    private final ToolArgumentsErrorHandler toolArgumentsErrorHandler;
    private final ToolExecutionErrorHandler toolExecutionErrorHandler;
    private final Executor toolExecutor;
    private final Executor completeResponseProcessingExecutor;
    private final Queue<StartedTool> startedTools = new ConcurrentLinkedQueue<>();
    private final @Nullable List<CompensableToolExecution> compensableExecutions;

    // Only used when compensation on tool errors is enabled, and written under the monitor of compensableExecutions.
    // A cancellation rolls back the tools of this round itself, unless the response of the round was claimed first by
    // its processing, which then runs them to the end, saves their results and rolls them back, or delivers the
    // final answer.
    private volatile boolean cancelled;
    private boolean responseClaimed;

    private final List<String> responseBuffer = new ArrayList<>();
    private final boolean hasOutputGuardrails;

    private int toolCallingRoundTripsLeft;

    private record ToolRequestResult(ToolExecutionResult result, Throwable error) {}

    private record StartedTool(ToolExecutionRequest request, CompletableFuture<ToolRequestResult> execution) {}

    AiServiceStreamingResponseHandler(
            ChatRequest chatRequest,
            ChatExecutor chatExecutor,
            AiServiceContext context,
            InvocationContext invocationContext,
            Consumer<String> partialResponseHandler,
            BiConsumer<PartialResponse, PartialResponseContext> partialResponseWithContextHandler,
            Consumer<PartialThinking> partialThinkingHandler,
            BiConsumer<PartialThinking, PartialThinkingContext> partialThinkingWithContextHandler,
            Consumer<PartialToolCall> partialToolCallHandler,
            BiConsumer<PartialToolCall, PartialToolCallContext> partialToolCallWithContextHandler,
            Consumer<BeforeToolExecution> beforeToolExecutionHandler,
            Consumer<Object> rawEventHandler,
            Consumer<ToolExecution> toolExecutionHandler,
            Consumer<ChatResponse> intermediateResponseHandler,
            Consumer<ChatResponse> completeResponseHandler,
            Consumer<Throwable> errorHandler,
            ChatMemory temporaryMemory,
            TokenUsage tokenUsage,
            ToolServiceContext toolServiceContext,
            int toolCallingRoundTripsLeft,
            ToolArgumentsErrorHandler toolArgumentsErrorHandler,
            ToolExecutionErrorHandler toolExecutionErrorHandler,
            Executor toolExecutor,
            Executor completeResponseProcessingExecutor,
            GuardrailRequestParams commonGuardrailParams,
            Object methodKey,
            @Nullable List<CompensableToolExecution> compensableExecutions) {
        this.chatRequest = ensureNotNull(chatRequest, "chatRequest");
        this.chatExecutor = ensureNotNull(chatExecutor, "chatExecutor");
        this.context = ensureNotNull(context, "context");
        this.invocationContext = ensureNotNull(invocationContext, "invocationContext");
        this.methodKey = methodKey;

        this.partialResponseHandler = partialResponseHandler;
        this.partialResponseWithContextHandler = partialResponseWithContextHandler;
        this.partialThinkingHandler = partialThinkingHandler;
        this.partialThinkingWithContextHandler = partialThinkingWithContextHandler;
        this.partialToolCallHandler = partialToolCallHandler;
        this.partialToolCallWithContextHandler = partialToolCallWithContextHandler;
        this.intermediateResponseHandler = intermediateResponseHandler;
        this.completeResponseHandler = completeResponseHandler;
        this.beforeToolExecutionHandler = beforeToolExecutionHandler;
        this.rawEventHandler = rawEventHandler;
        this.toolExecutionHandler = toolExecutionHandler;
        this.errorHandler = errorHandler;

        this.temporaryMemory = temporaryMemory;
        this.tokenUsage = tokenUsage;
        this.commonGuardrailParams = commonGuardrailParams;

        this.toolServiceContext = toolServiceContext;
        this.toolExecutors = toolServiceContext != null ? toolServiceContext.toolExecutors() : Map.of();
        this.toolArgumentsErrorHandler = ensureNotNull(toolArgumentsErrorHandler, "toolArgumentsErrorHandler");
        this.toolExecutionErrorHandler = ensureNotNull(toolExecutionErrorHandler, "toolExecutionErrorHandler");
        this.toolExecutor = toolExecutor;
        this.completeResponseProcessingExecutor = completeResponseProcessingExecutor;

        this.hasOutputGuardrails = context.guardrailService().hasOutputGuardrails(methodKey);

        this.toolCallingRoundTripsLeft = toolCallingRoundTripsLeft;
        this.compensableExecutions = compensableExecutions;
    }

    @Override
    public void onPartialResponse(String partialResponse) {
        // If we're using output guardrails, then buffer the partial response until the guardrails have completed
        if (hasOutputGuardrails) {
            responseBuffer.add(partialResponse);
        } else if (partialResponseHandler != null) {
            partialResponseHandler.accept(partialResponse);
        } else if (partialResponseWithContextHandler != null) {
            PartialResponseContext context = new PartialResponseContext(new CancellationUnsupportedStreamingHandle());
            partialResponseWithContextHandler.accept(new PartialResponse(partialResponse), context);
        }
    }

    @Override
    public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
        // If we're using output guardrails, then buffer the partial response until the guardrails have completed
        if (hasOutputGuardrails) {
            responseBuffer.add(partialResponse.text());
        } else if (partialResponseHandler != null) {
            partialResponseHandler.accept(partialResponse.text());
        } else if (partialResponseWithContextHandler != null) {
            partialResponseWithContextHandler.accept(
                    partialResponse, new PartialResponseContext(observeCancellation(context.streamingHandle())));
        }
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking) {
        if (partialThinkingHandler != null) {
            partialThinkingHandler.accept(partialThinking);
        } else if (partialThinkingWithContextHandler != null) {
            PartialThinkingContext context = new PartialThinkingContext(new CancellationUnsupportedStreamingHandle());
            partialThinkingWithContextHandler.accept(partialThinking, context);
        }
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
        if (partialThinkingHandler != null) {
            partialThinkingHandler.accept(partialThinking);
        } else if (partialThinkingWithContextHandler != null) {
            partialThinkingWithContextHandler.accept(
                    partialThinking, new PartialThinkingContext(observeCancellation(context.streamingHandle())));
        }
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall) {
        if (partialToolCallHandler != null) {
            partialToolCallHandler.accept(partialToolCall);
        } else if (partialToolCallWithContextHandler != null) {
            PartialToolCallContext context = new PartialToolCallContext(new CancellationUnsupportedStreamingHandle());
            partialToolCallWithContextHandler.accept(partialToolCall, context);
        }
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
        if (partialToolCallHandler != null) {
            partialToolCallHandler.accept(partialToolCall);
        } else if (partialToolCallWithContextHandler != null) {
            partialToolCallWithContextHandler.accept(
                    partialToolCall, new PartialToolCallContext(observeCancellation(context.streamingHandle())));
        }
    }

    @Override
    public void onCompleteToolCall(CompleteToolCall completeToolCall) {
        if (toolExecutor != null) {
            ToolExecutionRequest toolRequest = completeToolCall.toolExecutionRequest();
            if (compensableExecutions == null) {
                CompletableFuture<ToolRequestResult> execution;
                try {
                    execution = CompletableFuture.supplyAsync(() -> executeCatching(toolRequest), toolExecutor);
                } catch (RuntimeException rejected) {
                    execution = CompletableFuture.failedFuture(rejected);
                }
                startedTools.add(new StartedTool(toolRequest, execution));
                return;
            }
            // registered before it starts, so that a cancellation waits for it, also when the executor runs it inline
            CompletableFuture<Void> start = new CompletableFuture<>();
            StartedTool startedTool = new StartedTool(
                    toolRequest, start.thenApplyAsync(ignored -> executeCatching(toolRequest), toolExecutor));
            synchronized (compensableExecutions) {
                if (cancelled) {
                    return;
                }
                startedTools.add(startedTool);
            }
            start.complete(null);
        }
    }

    private ToolRequestResult executeCatching(ToolExecutionRequest toolRequest) {
        try {
            return new ToolRequestResult(execute(toolRequest), null);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            return new ToolRequestResult(null, e);
        }
    }

    @Override
    public void onUnmappedRawEvent(Object rawEvent) {
        if (rawEventHandler != null) {
            rawEventHandler.accept(rawEvent);
        }
    }

    private StreamingHandle observeCancellation(StreamingHandle streamingHandle) {
        ObservedStreamingHandle observed = this.observedStreamingHandle;
        if (observed == null || observed.delegate != streamingHandle) {
            observed = new ObservedStreamingHandle(streamingHandle);
            this.observedStreamingHandle = observed;
        }
        return observed;
    }

    private class ObservedStreamingHandle implements StreamingHandle {

        private final StreamingHandle delegate;

        ObservedStreamingHandle(StreamingHandle delegate) {
            this.delegate = delegate;
        }

        @Override
        public void cancel() {
            delegate.cancel();
            if (terminated.compareAndSet(false, true)) {
                try {
                    TerminalEvents.fireCancelled(context.eventListenerRegistrar, invocationContext);
                } finally {
                    compensateOnCancellation();
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }
    }

    private <T> void fireInvocationComplete(T result) {
        if (terminated.compareAndSet(false, true)) {
            TerminalEvents.fireCompleted(context.eventListenerRegistrar, invocationContext, result);
        }
    }

    private void fireResponseReceivedEvent(ChatResponse chatResponse) {
        context.eventListenerRegistrar.fireEvent(AiServiceResponseReceivedEvent.builder()
                .invocationContext(invocationContext)
                .request(chatRequest)
                .response(chatResponse)
                .build());
    }

    private void fireRequestIssuedEvent(ChatRequest chatRequest) {
        context.eventListenerRegistrar.fireEvent(AiServiceRequestIssuedEvent.builder()
                .invocationContext(invocationContext)
                .request(chatRequest)
                .build());
    }

    private void fireErrorReceived(Throwable error) {
        if (terminated.compareAndSet(false, true)) {
            TerminalEvents.fireError(context.eventListenerRegistrar, invocationContext, error);
        }
    }

    @Override
    public void onCompleteResponse(ChatResponse chatResponse) {
        if (completeResponseProcessingExecutor == null) {
            try {
                processCompleteResponse(chatResponse);
            } catch (Error e) {
                onError(e); // a model implementation might not catch an Error, so the stream would never end
            }
            return;
        }
        try {
            completeResponseProcessingExecutor.execute(() -> {
                try {
                    processCompleteResponse(chatResponse);
                } catch (Throwable t) {
                    onError(t);
                }
            });
        } catch (Exception e) {
            onError(e);
        }
    }

    private void processCompleteResponse(ChatResponse chatResponse) {
        if (compensableExecutions != null) {
            synchronized (compensableExecutions) {
                if (cancelled) {
                    return; // the cancellation rolls back the tools that had already run, nothing is delivered
                }
                responseClaimed = true;
            }
        }

        fireResponseReceivedEvent(chatResponse);
        AiMessage aiMessage = chatResponse.aiMessage();

        if (aiMessage.hasToolExecutionRequests() && toolCallingRoundTripsLeft-- == 0) {
            throw runtime(
                    "Something is wrong, exceeded %s tool calling round trips (maxToolCallingRoundTrips)",
                    context.toolService.maxToolCallingRoundTrips());
        }

        addToMemory(aiMessage);

        if (aiMessage.hasToolExecutionRequests()) {

            if (intermediateResponseHandler != null) {
                intermediateResponseHandler.accept(chatResponse);
            }

            List<ToolExecutionRequest> executedRequests = aiMessage.toolExecutionRequests();
            Map<ToolExecutionRequest, ToolExecutionResult> toolResults = new LinkedHashMap<>();
            if (toolExecutor != null) {
                executedRequests = new ArrayList<>();
                Map<ToolExecutionRequest, Throwable> toolErrors = new LinkedHashMap<>();
                for (StartedTool startedTool : startedTools) {
                    executedRequests.add(startedTool.request());
                    try {
                        ToolRequestResult toolRequestResult = startedTool.execution().get();
                        if (toolRequestResult.error() != null) {
                            toolErrors.put(startedTool.request(), toolRequestResult.error());
                        } else {
                            toolResults.put(startedTool.request(), toolRequestResult.result());
                        }
                    } catch (ExecutionException e) {
                        if (e.getCause() instanceof VirtualMachineError vmError) {
                            throw vmError;
                        }
                        // e.g. the tool executor rejected the tool
                        toolErrors.put(startedTool.request(), e.getCause());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
                if (!toolErrors.isEmpty()) {
                    Throwable error = toolErrors.values().iterator().next();
                    RuntimeException errorToRethrow =
                            error instanceof RuntimeException re ? re : new RuntimeException(error);
                    failRoundBeforeRethrow(executedRequests, toolResults, toolErrors, errorToRethrow);
                    throw errorToRethrow;
                }
            } else {
                for (ToolExecutionRequest toolRequest : executedRequests) {
                    try {
                        toolResults.put(toolRequest, execute(toolRequest));
                    } catch (VirtualMachineError e) {
                        throw e;
                    } catch (RuntimeException | Error e) {
                        failRoundBeforeRethrow(executedRequests, toolResults, Map.of(toolRequest, e), e);
                        throw e;
                    }
                }
            }

            // Use the requests that were executed: the final response can reformat their JSON arguments.
            ToolService.ToolResultsOutcome outcome = context.toolService.processToolResults(
                    context, executedRequests, toolResults, new ArrayList<>(), invocationContext, toolServiceContext);

            if (cancelled) {
                // saved first, so that no tool call in the chat memory is left without a result
                int saved = 0;
                try {
                    ChatMemory memory = getMemory();
                    for (ToolExecutionResultMessage resultMessage : outcome.resultMessages()) {
                        memory.add(resultMessage);
                        saved++;
                    }
                } finally {
                    compensateOnCancellation(toolResults, executedRequests.subList(saved, executedRequests.size()));
                }
                return;
            }

            context.toolService.compensateIfNeeded(
                    executedRequests,
                    toolResults,
                    outcome.resultMessages(),
                    compensableExecutions,
                    outcome.anyToolErrored(),
                    getMemory(),
                    List.of(),
                    invocationContext,
                    context.eventListenerRegistrar);

            List<ChatMessage> nextMessages = context.toolService.persistToolResultsAndResolveMessagesSync(
                    context, getMemory(), null, outcome.resultMessages(), invocationContext);

            if (rolledBackOnCancellation()) {
                return;
            }

            if (ToolService.shouldReturnImmediately(outcome.anyToolErrored(), outcome.returnBehaviors())) {
                ChatResponse finalChatResponse = finalResponse(chatResponse, aiMessage);
                fireInvocationComplete(finalChatResponse);

                if (completeResponseHandler != null) {
                    completeResponseHandler.accept(finalChatResponse);
                }
                return;
            }

            ChatRequestParameters parameters =
                    chatRequestParameters(invocationContext.methodArguments(), toolServiceContext.effectiveTools());
            ToolService.NextChatRequest next = context.toolService.prepareNextChatRequest(
                    context,
                    invocationContext.chatMemoryId(),
                    nextMessages,
                    invocationContext,
                    toolServiceContext,
                    toolResults,
                    parameters);

            if (rolledBackOnCancellation()) {
                return;
            }

            var handler = new AiServiceStreamingResponseHandler(
                    next.chatRequest(),
                    chatExecutor,
                    context,
                    invocationContext,
                    partialResponseHandler,
                    partialResponseWithContextHandler,
                    partialThinkingHandler,
                    partialThinkingWithContextHandler,
                    partialToolCallHandler,
                    partialToolCallWithContextHandler,
                    beforeToolExecutionHandler,
                    rawEventHandler,
                    toolExecutionHandler,
                    intermediateResponseHandler,
                    completeResponseHandler,
                    errorHandler,
                    temporaryMemory,
                    TokenUsage.sum(tokenUsage, chatResponse.metadata().tokenUsage()),
                    next.toolServiceContext(),
                    toolCallingRoundTripsLeft,
                    toolArgumentsErrorHandler,
                    toolExecutionErrorHandler,
                    toolExecutor,
                    completeResponseProcessingExecutor,
                    commonGuardrailParams,
                    methodKey,
                    compensableExecutions);

            CurrentInvocation.runWith(invocationContext, () -> context.streamingChatModel.chat(next.chatRequest(), HandlerWithoutInvocation.wrap(handler)));
        } else {
            ChatResponse finalChatResponse = finalResponse(chatResponse, aiMessage);

            if (completeResponseHandler != null) {
                // Invoke output guardrails
                if (hasOutputGuardrails) {
                    if (commonGuardrailParams != null) {
                        var newCommonParams = commonGuardrailParams.toBuilder()
                                .chatMemory(ChatMemoryService.unwrap(getMemory()))
                                .build();

                        var outputGuardrailParams = OutputGuardrailRequest.builder()
                                .responseFromLLM(finalChatResponse)
                                .chatExecutor(ToolAwareRepromptExecutor.wrap(
                                        chatExecutor,
                                        context,
                                        invocationContext.chatMemoryId(),
                                        chatRequest.parameters(),
                                        invocationContext,
                                        toolServiceContext,
                                        this::executeSynchronously))
                                .requestParams(newCommonParams)
                                .chatRequest(chatRequest)
                                .build();

                        finalChatResponse =
                                context.guardrailService().executeGuardrails(methodKey, outputGuardrailParams);
                    }

                    // If we have output guardrails, we should process all of the partial responses first before
                    // completing
                    if (partialResponseHandler != null) {
                        responseBuffer.forEach(partialResponseHandler::accept);
                    } else if (partialResponseWithContextHandler != null) {
                        PartialResponseContext partialResponseContext =
                                new PartialResponseContext(new CancellationUnsupportedStreamingHandle());
                        responseBuffer.forEach(s -> partialResponseWithContextHandler.accept(
                                new PartialResponse(s), partialResponseContext));
                    }
                    responseBuffer.clear();
                }

                fireInvocationComplete(finalChatResponse);
                completeResponseHandler.accept(finalChatResponse);
            } else {
                fireInvocationComplete(finalChatResponse);
            }
        }
    }

    /**
     * Performs a single, blocking model call against the configured
     * {@link dev.langchain4j.model.chat.StreamingChatModel}, exposing it as a plain synchronous call so that
     * {@code executeInferenceAndToolsLoop} can drive the tool loop on a streaming-only AI Service. This is
     * intentionally a raw model call and does not fire request/response events — those are fired by
     * {@code executeInferenceAndToolsLoop} itself, mirroring the synchronous path.
     */
    private ChatResponse executeSynchronously(ChatRequest request) {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        context.streamingChatModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });
        try {
            return future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private ChatResponse finalResponse(ChatResponse completeResponse, AiMessage aiMessage) {
        TokenUsage totalTokenUsage =
                TokenUsage.sum(tokenUsage, completeResponse.metadata().tokenUsage());
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(completeResponse.metadata().toBuilder()
                        .tokenUsage(totalTokenUsage)
                        .build())
                .build();
    }

    /**
     * Rolls back the compensable tools on cancellation, the way the non-blocking modes do: the tools of this round
     * that had already started are drained first, and then every compensable tool that succeeded, in this round and in
     * the earlier ones, is rolled back. The thread that cancels does not wait for the running tools: the rollback runs
     * on the {@code completeResponseProcessingExecutor} when it is set, otherwise on the thread that cancels (when no
     * tool is running) or on the one that finishes the last running tool. A failure is logged, as the invocation was
     * cancelled and there is nobody to report it to. Nothing to do when the processing of the response claimed it
     * first: it runs the tools of the round to the end, saves their results and then rolls back, or delivers the final
     * answer.
     */
    private void compensateOnCancellation() {
        if (compensableExecutions == null) {
            return;
        }
        List<StartedTool> drained;
        synchronized (compensableExecutions) {
            cancelled = true;
            if (responseClaimed) {
                return;
            }
            drained = new ArrayList<>(startedTools);
        }
        CompletableFuture.allOf(drained.stream().map(StartedTool::execution).toArray(CompletableFuture<?>[]::new))
                .whenComplete((ignored, error) -> {
                    Map<ToolExecutionRequest, ToolExecutionResult> results = new LinkedHashMap<>();
                    for (StartedTool startedTool : drained) {
                        ToolRequestResult toolRequestResult = startedTool.execution().isCompletedExceptionally()
                                ? null
                                : startedTool.execution().getNow(null);
                        if (toolRequestResult != null && toolRequestResult.error() == null) {
                            results.put(startedTool.request(), toolRequestResult.result());
                        }
                    }
                    List<ToolExecutionRequest> unsaved =
                            drained.stream().map(StartedTool::request).toList();
                    runRollback(() -> compensateOnCancellation(results, unsaved));
                });
    }

    /**
     * Rolls back when the invocation was cancelled after this processing claimed the response, once the results of
     * its round are saved. The round is not passed on: {@code compensateIfNeeded} already collected its compensable
     * tools, or already rolled them back when one of its tools failed.
     */
    private boolean rolledBackOnCancellation() {
        if (!cancelled) {
            return false;
        }
        compensateOnCancellation(Map.of(), null);
        return true;
    }

    private void runRollback(Runnable rollback) {
        if (completeResponseProcessingExecutor == null) {
            rollback.run();
            return;
        }
        try {
            completeResponseProcessingExecutor.execute(rollback);
        } catch (RuntimeException rejected) {
            rollback.run();
        }
    }

    private void compensateOnCancellation(
            Map<ToolExecutionRequest, ToolExecutionResult> currentRoundResults,
            @Nullable List<ToolExecutionRequest> unsavedRequests) {
        try {
            context.toolService.compensateOnCancellation(
                    currentRoundResults,
                    unsavedRequests,
                    compensableExecutions,
                    getMemory(),
                    invocationContext,
                    context.eventListenerRegistrar);
        } catch (RuntimeException e) {
            LOG.warn("Failed to roll back the tools after the invocation was cancelled", e);
        }
    }

    private void failRoundBeforeRethrow(
            List<ToolExecutionRequest> executedRequests,
            Map<ToolExecutionRequest, ToolExecutionResult> toolResults,
            Map<ToolExecutionRequest, Throwable> toolErrors,
            Throwable errorToRethrow) {
        context.toolService.failRoundBeforeRethrow(
                executedRequests,
                toolResults,
                toolErrors,
                errorToRethrow,
                compensableExecutions,
                getMemory(),
                null,
                invocationContext,
                context.eventListenerRegistrar);
    }

    private ToolExecutionResult execute(ToolExecutionRequest toolRequest) {
        return context.toolService.executeTool(
                invocationContext,
                toolExecutors,
                toolRequest,
                beforeToolExecutionHandler,
                toolExecutionHandler);
    }

    private ChatMemory getMemory() {
        return getMemory(invocationContext.chatMemoryId());
    }

    private ChatMemory getMemory(Object memId) {
        return context.hasChatMemory()
                ? context.chatMemoryService.getOrCreateInvocationChatMemory(invocationContext)
                : temporaryMemory;
    }

    private void addToMemory(ChatMessage chatMessage) {
        getMemory().add(chatMessage);
    }

    @Override
    public void onError(Throwable error) {
        try {
            fireErrorReceived(error);
        } catch (Exception e) {
            LOG.error("Failed to fire the error event", e);
        }
        if (errorHandler != null) {
            try {
                errorHandler.accept(error);
            } catch (Exception e) {
                LOG.error("While handling the following error...", error);
                LOG.error("...the following error happened", e);
            }
        } else {
            LOG.warn("Ignored error", error);
        }
    }
}
