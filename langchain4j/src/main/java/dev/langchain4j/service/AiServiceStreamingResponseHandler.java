package dev.langchain4j.service;

import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.AiServiceParamsUtil.chatRequestParameters;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.StreamingToolDispatchHook;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolBatchDispatcher;
import dev.langchain4j.service.tool.ToolCallsLimitExceededException;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.service.tool.ToolServiceContext;
import dev.langchain4j.service.tool.search.ToolSearchService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
    private final GuardrailRequestParams commonGuardrailParams;
    private final Object methodKey;

    private final Consumer<String> partialResponseHandler;
    private final BiConsumer<PartialResponse, PartialResponseContext> partialResponseWithContextHandler;
    private final Consumer<PartialThinking> partialThinkingHandler;
    private final BiConsumer<PartialThinking, PartialThinkingContext> partialThinkingWithContextHandler;
    private final Consumer<PartialToolCall> partialToolCallHandler;
    private final BiConsumer<PartialToolCall, PartialToolCallContext> partialToolCallWithContextHandler;
    private final Consumer<BeforeToolExecution> beforeToolExecutionHandler;
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
    private final StreamingChatModel streamingChatModel;
    private final StreamingToolDispatchHook streamingDispatchHook;
    private final ChatRequestParameters loopParameters;

    private final List<String> responseBuffer = new ArrayList<>();
    private final Queue<Future<ToolRequestResult>> toolExecutionFutures = new ConcurrentLinkedQueue<>();
    private final boolean hasOutputGuardrails;

    private int sequentialToolsInvocationsLeft;

    private final ReentrantLock toolExecutionLock = new ReentrantLock();
    private final Deque<ToolExecution> bufferedToolExecutions = new ArrayDeque<>();
    private boolean intermediateResponseEmitted = false;
    private boolean terminated = false;

    private record ToolRequestResult(ToolExecutionRequest request, ToolExecutionResult result) {}

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
            Consumer<ToolExecution> toolExecutionHandler,
            Consumer<ChatResponse> intermediateResponseHandler,
            Consumer<ChatResponse> completeResponseHandler,
            Consumer<Throwable> errorHandler,
            ChatMemory temporaryMemory,
            TokenUsage tokenUsage,
            ToolServiceContext toolServiceContext,
            int sequentialToolsInvocationsLeft,
            ToolArgumentsErrorHandler toolArgumentsErrorHandler,
            ToolExecutionErrorHandler toolExecutionErrorHandler,
            Executor toolExecutor,
            GuardrailRequestParams commonGuardrailParams,
            Object methodKey) {
        this(
                chatRequest,
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
                toolExecutionHandler,
                intermediateResponseHandler,
                completeResponseHandler,
                errorHandler,
                temporaryMemory,
                tokenUsage,
                toolServiceContext,
                sequentialToolsInvocationsLeft,
                toolArgumentsErrorHandler,
                toolExecutionErrorHandler,
                toolExecutor,
                commonGuardrailParams,
                methodKey,
                null,
                null,
                null);
    }

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
            Consumer<ToolExecution> toolExecutionHandler,
            Consumer<ChatResponse> intermediateResponseHandler,
            Consumer<ChatResponse> completeResponseHandler,
            Consumer<Throwable> errorHandler,
            ChatMemory temporaryMemory,
            TokenUsage tokenUsage,
            ToolServiceContext toolServiceContext,
            int sequentialToolsInvocationsLeft,
            ToolArgumentsErrorHandler toolArgumentsErrorHandler,
            ToolExecutionErrorHandler toolExecutionErrorHandler,
            Executor toolExecutor,
            GuardrailRequestParams commonGuardrailParams,
            Object methodKey,
            StreamingChatModel streamingChatModel,
            StreamingToolDispatchHook streamingDispatchHook,
            ChatRequestParameters loopParameters) {
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
        this.toolExecutionHandler = toolExecutionHandler;
        this.errorHandler = errorHandler;

        this.temporaryMemory = temporaryMemory;
        this.tokenUsage = ensureNotNull(tokenUsage, "tokenUsage");
        this.commonGuardrailParams = commonGuardrailParams;

        this.toolServiceContext = toolServiceContext;
        this.toolExecutors = toolServiceContext != null ? toolServiceContext.toolExecutors() : Map.of();
        this.toolArgumentsErrorHandler = ensureNotNull(toolArgumentsErrorHandler, "toolArgumentsErrorHandler");
        this.toolExecutionErrorHandler = ensureNotNull(toolExecutionErrorHandler, "toolExecutionErrorHandler");
        this.toolExecutor = toolExecutor;
        this.streamingChatModel = streamingChatModel;
        this.streamingDispatchHook = streamingDispatchHook != null ? streamingDispatchHook : StreamingToolDispatchHook.INLINE;
        this.loopParameters = loopParameters != null ? loopParameters : chatRequest.parameters();

        this.hasOutputGuardrails = context.guardrailService().hasOutputGuardrails(methodKey);

        this.sequentialToolsInvocationsLeft = sequentialToolsInvocationsLeft;
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
            partialResponseWithContextHandler.accept(partialResponse, context);
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
            partialThinkingWithContextHandler.accept(partialThinking, context);
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
            partialToolCallWithContextHandler.accept(partialToolCall, context);
        }
    }

    @Override
    public void onCompleteToolCall(CompleteToolCall completeToolCall) {
        if (shouldScheduleToolsEagerly()) {
            ToolExecutionRequest toolRequest = completeToolCall.toolExecutionRequest();
            CompletableFuture<ToolRequestResult> future = new CompletableFuture<>();
            toolExecutionFutures.add(future);
            try {
                toolExecutor.execute(() -> {
                    if (future.isCancelled()) {
                        return;
                    }
                    try {
                        future.complete(new ToolRequestResult(toolRequest, execute(toolRequest)));
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
    }

    private boolean shouldScheduleToolsEagerly() {
        // Stream-level beforeToolExecution can veto the tool body, so keep it on the
        // post-intermediate dispatch path where its delivery order is observable.
        return context.toolService.maxToolCallsPerResponse() == 0
                && toolExecutor != null
                && beforeToolExecutionHandler == null;
    }

    private ToolExecutionResult execute(ToolExecutionRequest toolRequest) {
        Consumer<ToolExecution> externalAfter =
                toolExecutionHandler == null ? null : this::emitToolExecution;
        return context.toolService.executeTool(
                invocationContext, toolExecutors, toolRequest, beforeToolExecutionHandler, externalAfter);
    }

    private void emitToolExecution(ToolExecution toolExecution) {
        toolExecutionLock.lock();
        try {
            if (terminated) {
                return;
            }
            if (!intermediateResponseEmitted) {
                bufferedToolExecutions.add(toolExecution);
                return;
            }

            toolExecutionHandler.accept(toolExecution);
        } finally {
            toolExecutionLock.unlock();
        }
    }

    private Throwable drainBufferedToolExecutions() {
        toolExecutionLock.lock();
        try {
            if (terminated) {
                bufferedToolExecutions.clear();
                return null;
            }
            intermediateResponseEmitted = true;
            if (toolExecutionHandler == null) {
                return null;
            }
            return drainToolExecutionQueue();
        } finally {
            toolExecutionLock.unlock();
        }
    }

    private boolean terminateAndDrainVisibleToolExecutions() {
        toolExecutionLock.lock();
        try {
            if (terminated) {
                bufferedToolExecutions.clear();
                return false;
            }
            terminated = true;
            if (!intermediateResponseEmitted || toolExecutionHandler == null) {
                bufferedToolExecutions.clear();
                return true;
            }

            Throwable callbackError = drainToolExecutionQueue();
            if (callbackError != null) {
                LOG.error("onToolExecuted callback threw while handling a stream error", callbackError);
            }
            return true;
        } finally {
            toolExecutionLock.unlock();
        }
    }

    private Throwable drainToolExecutionQueue() {
        Throwable captured = null;
        while (true) {
            ToolExecution next = bufferedToolExecutions.poll();
            if (next == null) {
                return captured;
            }
            try {
                toolExecutionHandler.accept(next);
            } catch (Throwable t) {
                if (captured == null) {
                    captured = t;
                } else {
                    captured.addSuppressed(t);
                }
            }
        }
    }

    private boolean isTerminated() {
        toolExecutionLock.lock();
        try {
            return terminated;
        } finally {
            toolExecutionLock.unlock();
        }
    }

    private <T> void fireInvocationComplete(T result) {
        context.eventListenerRegistrar.fireEvent(AiServiceCompletedEvent.builder()
                .invocationContext(invocationContext)
                .result(result)
                .build());
    }

    private void fireToolExecutedEvent(ToolExecutionRequest request, ToolExecutionResult result) {
        context.eventListenerRegistrar.fireEvent(ToolExecutedEvent.builder()
                .invocationContext(invocationContext)
                .request(request)
                .resultContents(result.resultContents())
                .build());
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
        context.eventListenerRegistrar.fireEvent(AiServiceErrorEvent.builder()
                .invocationContext(invocationContext)
                .error(error)
                .build());
    }

    private void handleStreamingError(Throwable error) {
        cancelPendingToolCalls(null);
        if (!terminateAndDrainVisibleToolExecutions()) {
            return;
        }
        if (errorHandler != null) {
            try {
                fireErrorReceived(error);
                errorHandler.accept(error);
            } catch (Exception inner) {
                LOG.error("While handling the following error...", error);
                LOG.error("...the following error happened", inner);
            }
        } else {
            LOG.warn("Ignored error", error);
        }
    }

    @Override
    public void onCompleteResponse(ChatResponse chatResponse) {
        fireResponseReceivedEvent(chatResponse);
        AiMessage aiMessage = chatResponse.aiMessage();
        addToMemory(aiMessage);

        if (aiMessage.hasToolExecutionRequests()) {

            if (sequentialToolsInvocationsLeft-- == 0) {
                handleStreamingError(runtime(
                        "Something is wrong, exceeded %s sequential tool invocations",
                        context.toolService.maxSequentialToolsInvocations()));
                return;
            }

            List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
            int maxToolCallsPerResponse = context.toolService.maxToolCallsPerResponse();
            if (maxToolCallsPerResponse > 0 && toolRequests.size() > maxToolCallsPerResponse) {
                handleStreamingError(new ToolCallsLimitExceededException(maxToolCallsPerResponse, toolRequests.size()));
                return;
            }

            if (intermediateResponseHandler != null) {
                intermediateResponseHandler.accept(chatResponse);
            }

            Throwable callbackError = drainBufferedToolExecutions();
            if (callbackError != null) {
                handleStreamingError(callbackError);
                return;
            }
            if (isTerminated()) {
                return;
            }

            streamingDispatchHook.dispatch(() -> {
                try {
                    runToolBatchAndContinue(chatResponse, aiMessage, toolRequests);
                } catch (Throwable t) {
                    handleStreamingError(t);
                }
                return null;
            });
        } else {
            if (isTerminated()) {
                return;
            }

            ChatResponse finalChatResponse = finalResponse(chatResponse, aiMessage);

            if (completeResponseHandler != null) {
                // Invoke output guardrails
                if (hasOutputGuardrails) {
                    if (commonGuardrailParams != null) {
                        var newCommonParams = commonGuardrailParams.toBuilder()
                                .chatMemory(getMemory())
                                .build();

                        var outputGuardrailParams = OutputGuardrailRequest.builder()
                                .responseFromLLM(finalChatResponse)
                                .chatExecutor(chatExecutor)
                                .requestParams(newCommonParams)
                                .build();

                        finalChatResponse =
                                context.guardrailService().executeGuardrails(methodKey, outputGuardrailParams);
                    }

                    // If we have output guardrails, we should process all of the partial responses first before
                    // completing
                    if (partialResponseHandler != null) {
                        responseBuffer.forEach(partialResponseHandler::accept);
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

    private void runToolBatchAndContinue(
            ChatResponse chatResponse, AiMessage aiMessage, List<ToolExecutionRequest> toolRequests) {

        Map<ToolExecutionRequest, ToolExecutionResult> results;
        int maxToolCallsPerResponse = context.toolService.maxToolCallsPerResponse();
        if (shouldScheduleToolsEagerly()) {
            results = gatherScheduledToolResults();
        } else {
            results = ToolBatchDispatcher.dispatch(
                    ToolBatchDispatcher.Request.builder()
                            .toolRequests(toolRequests)
                            .toolExecutors(toolExecutors)
                            .executor(toolExecutor)
                            .invocationContext(invocationContext)
                            .beforeToolExecution(combine(
                                    context.toolService.beforeToolExecution(), beforeToolExecutionHandler))
                            .afterToolExecution(combine(
                                    context.toolService.afterToolExecution(), toolExecutionHandler))
                            .errorHandlerBypass(context.toolService.errorHandlerBypass())
                            .argumentsErrorHandler(toolArgumentsErrorHandler)
                            .executionErrorHandler(toolExecutionErrorHandler)
                            .hallucinationStrategy(context.toolService.hallucinatedToolNameStrategy())
                            .maxToolCallsPerResponse(0)
                            .useExecutorForSingleTool(shouldUseExecutorForDeferredSingleTool(
                                    maxToolCallsPerResponse))
                            .build());
        }

        List<ToolExecutionResult> toolResults = new ArrayList<>(results.size());
        boolean anyToolErrored = false;
        List<ReturnBehavior> returnBehaviors = new ArrayList<>(results.size());

        for (ToolExecutionRequest request : toolRequests) {
            ToolExecutionResult result = results.get(request);
            if (result == null) {
                throw runtime("No tool execution result was scheduled for tool request '%s'", request.name());
            }
            toolResults.add(result);
            fireToolExecutedEvent(request, result);
            ToolExecutionResultMessage resultMessage = toResultMessage(request, result);
            addToMemory(resultMessage);
            anyToolErrored = anyToolErrored || result.isError();
            returnBehaviors.add(toolServiceContext.returnBehavior(request.name()));
        }

        if (ToolService.shouldReturnImmediately(anyToolErrored, returnBehaviors)) {
            ChatResponse finalChatResponse = finalResponse(chatResponse, aiMessage);
            fireInvocationComplete(finalChatResponse);

            if (completeResponseHandler != null) {
                completeResponseHandler.accept(finalChatResponse);
            }
            return;
        }

        List<ChatMessage> messages = messagesToSend(invocationContext.chatMemoryId());

        ToolServiceContext updatedToolContext = context.toolService.refreshDynamicProvidersWithFactory(
                toolServiceContext, messages, invocationContext);
        updatedToolContext = ToolSearchService.addFoundTools(updatedToolContext, toolResults);

        ChatRequestParameters baseParameters =
                chatRequestParameters(invocationContext.methodArguments(), updatedToolContext.effectiveTools());
        // Apply forceToolChoiceAutoAfterFirstIteration: in streaming, every onCompleteResponse
        // invocation that schedules a follow-up is, by definition, after the first iteration.
        ChatRequestParameters override;
        if (context.toolService.forceToolChoiceAutoAfterFirstIteration()
                && loopParameters.toolChoice() == ToolChoice.REQUIRED) {
            override = ChatRequestParameters.builder()
                    .toolSpecifications(updatedToolContext.effectiveTools())
                    .toolChoice(ToolChoice.AUTO)
                    .build();
        } else {
            override = ChatRequestParameters.builder()
                    .toolSpecifications(updatedToolContext.effectiveTools())
                    .build();
        }
        ChatRequestParameters nextParameters = baseParameters.overrideWith(override);

        ChatRequest nextChatRequest = context.chatRequestTransformer.apply(
                ChatRequest.builder()
                        .messages(messages)
                        .parameters(nextParameters)
                        .build(),
                invocationContext.chatMemoryId());

        var handler = new AiServiceStreamingResponseHandler(
                nextChatRequest,
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
                toolExecutionHandler,
                intermediateResponseHandler,
                completeResponseHandler,
                errorHandler,
                temporaryMemory,
                TokenUsage.sum(tokenUsage, chatResponse.metadata().tokenUsage()),
                updatedToolContext,
                sequentialToolsInvocationsLeft,
                toolArgumentsErrorHandler,
                toolExecutionErrorHandler,
                toolExecutor,
                commonGuardrailParams,
                methodKey,
                streamingChatModel,
                streamingDispatchHook,
                nextParameters);

        fireRequestIssuedEvent(nextChatRequest);
        StreamingChatModel modelToUse = streamingChatModel != null ? streamingChatModel : context.streamingChatModel;
        modelToUse.chat(nextChatRequest, handler);
    }

    private boolean shouldUseExecutorForDeferredSingleTool(int maxToolCallsPerResponse) {
        return maxToolCallsPerResponse > 0 || (toolExecutor != null && beforeToolExecutionHandler != null);
    }

    private Map<ToolExecutionRequest, ToolExecutionResult> gatherScheduledToolResults() {
        Map<ToolExecutionRequest, ToolExecutionResult> results = new LinkedHashMap<>();

        for (Future<ToolRequestResult> future : toolExecutionFutures) {
            try {
                ToolRequestResult toolRequestResult = future.get();
                results.put(toolRequestResult.request(), toolRequestResult.result());
            } catch (ExecutionException e) {
                cancelPendingToolCalls(future);
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                    throw re;
                } else if (cause instanceof Error err) {
                    throw err;
                } else {
                    throw new RuntimeException(cause);
                }
            } catch (InterruptedException e) {
                cancelPendingToolCalls(future);
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        return results;
    }

    private void cancelPendingToolCalls(Future<ToolRequestResult> except) {
        for (Future<ToolRequestResult> future : toolExecutionFutures) {
            if (future != except && !future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private static <T> Consumer<T> combine(Consumer<T> first, Consumer<T> second) {
        if (first != null && second != null) {
            return first.andThen(second);
        }
        return first != null ? first : second;
    }

    private ChatResponse finalResponse(ChatResponse completeResponse, AiMessage aiMessage) {
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(completeResponse.metadata().toBuilder()
                        .tokenUsage(tokenUsage.add(completeResponse.metadata().tokenUsage()))
                        .build())
                .build();
    }

    private static ToolExecutionResultMessage toResultMessage(
            ToolExecutionRequest request, ToolExecutionResult result) {
        return ToolExecutionResultMessage.builder()
                .id(request.id())
                .toolName(request.name())
                .contents(result.resultContents())
                .isError(result.isError())
                .attributes(result.attributes())
                .build();
    }

    private ChatMemory getMemory() {
        return getMemory(invocationContext.chatMemoryId());
    }

    private ChatMemory getMemory(Object memId) {
        return context.hasChatMemory()
                ? context.chatMemoryService.getOrCreateChatMemory(invocationContext.chatMemoryId())
                : temporaryMemory;
    }

    private void addToMemory(ChatMessage chatMessage) {
        getMemory().add(chatMessage);
    }

    private List<ChatMessage> messagesToSend(Object memoryId) {
        List<ChatMessage> messages = getMemory(memoryId).messages();
        return context.storeRetrievedContentInChatMemory
                ? messages
                : UserMessage.replaceLast(messages, invocationContext.userMessage());
    }

    @Override
    public void onError(Throwable error) {
        cancelPendingToolCalls(null);
        if (!terminateAndDrainVisibleToolExecutions()) {
            return;
        }
        if (errorHandler != null) {
            try {
                fireErrorReceived(error);
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
