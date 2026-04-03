package dev.langchain4j.service;

import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.AiServiceParamsUtil.chatRequestParameters;
import static dev.langchain4j.service.tool.ToolService.refreshDynamicProviders;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
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
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolInvocationLimitExceededException;
import dev.langchain4j.service.tool.ToolLimitExceededBehavior;
import dev.langchain4j.service.tool.ToolServiceContext;
import dev.langchain4j.service.tool.search.ToolSearchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
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

    private static final String TOOL_LIMIT_EXCEEDED_MESSAGE =
            "Tool '%s' has reached its invocation limit of %d. Please proceed without it.";
    private static final String TOOL_LOOP_ENDED_MESSAGE =
            "Tool loop ended because another tool reached its invocation limit. Please proceed without tools.";

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
    private final Queue<Future<ToolRequestResult>> toolExecutionFutures = new ConcurrentLinkedQueue<>();

    private final List<String> responseBuffer = new ArrayList<>();
    private final boolean hasOutputGuardrails;

    private int sequentialToolsInvocationsLeft;
    private final Map<String, Integer> toolInvocationCounts;
    private final Set<String> overBudgetToolNames = ConcurrentHashMap.newKeySet();

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
                new HashMap<>());
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
            Map<String, Integer> toolInvocationCounts) {
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

        this.hasOutputGuardrails = context.guardrailService().hasOutputGuardrails(methodKey);

        this.sequentialToolsInvocationsLeft = sequentialToolsInvocationsLeft;
        this.toolInvocationCounts = toolInvocationCounts;
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
        if (toolExecutor != null) {
            ToolExecutionRequest toolRequest = completeToolCall.toolExecutionRequest();

            // Check per-tool budget before dispatching execution.
            // Use synchronized access to toolInvocationCounts since onCompleteToolCall
            // may be called from the streaming model's thread while futures resolve concurrently.
            String toolName = toolRequest.name();
            int limit = context.toolService.maxToolInvocationsFor(toolName);
            boolean overBudget;
            synchronized (toolInvocationCounts) {
                int currentCount = toolInvocationCounts.getOrDefault(toolName, 0);
                if (currentCount >= limit) {
                    overBudget = true;
                } else {
                    toolInvocationCounts.merge(toolName, 1, Integer::sum);
                    overBudget = false;
                }
            }

            if (overBudget) {
                overBudgetToolNames.add(toolName);
                ToolExecutionResult overBudgetResult = ToolExecutionResult.builder()
                        .isError(true)
                        .resultText(String.format(TOOL_LIMIT_EXCEEDED_MESSAGE, toolName, limit))
                        .build();
                toolExecutionFutures.add(
                        CompletableFuture.completedFuture(new ToolRequestResult(toolRequest, overBudgetResult)));
            } else {
                var future = CompletableFuture.supplyAsync(
                        () -> {
                            ToolExecutionResult toolResult = execute(toolRequest);
                            return new ToolRequestResult(toolRequest, toolResult);
                        },
                        toolExecutor);
                toolExecutionFutures.add(future);
            }
        }
    }

    private <T> void fireInvocationComplete(T result) {
        context.eventListenerRegistrar.fireEvent(AiServiceCompletedEvent.builder()
                .invocationContext(invocationContext)
                .result(result)
                .build());
    }

    private void fireToolExecutedEvent(ToolRequestResult toolRequestResult) {
        context.eventListenerRegistrar.fireEvent(ToolExecutedEvent.builder()
                .invocationContext(invocationContext)
                .request(toolRequestResult.request())
                .resultText(toolRequestResult.result().resultText())
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

    @Override
    public void onCompleteResponse(ChatResponse chatResponse) {
        fireResponseReceivedEvent(chatResponse);
        AiMessage aiMessage = chatResponse.aiMessage();
        addToMemory(aiMessage);

        if (aiMessage.hasToolExecutionRequests()) {

            if (sequentialToolsInvocationsLeft-- == 0) {
                throw runtime(
                        "Something is wrong, exceeded %s sequential tool invocations",
                        context.toolService.maxSequentialToolsInvocations());
            }

            if (intermediateResponseHandler != null) {
                intermediateResponseHandler.accept(chatResponse);
            }

            boolean immediateToolReturn = true;
            List<ToolExecutionResult> toolResults = new ArrayList<>();

            if (toolExecutor != null) {
                for (Future<ToolRequestResult> toolExecutionFuture : toolExecutionFutures) {
                    try {
                        ToolRequestResult toolRequestResult = toolExecutionFuture.get();
                        ToolExecutionRequest toolRequest = toolRequestResult.request();
                        ToolExecutionResult toolResult = toolRequestResult.result();

                        fireToolExecutedEvent(new ToolRequestResult(toolRequest, toolResult));
                        toolResults.add(toolResult);
                        ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.builder()
                                .id(toolRequest.id())
                                .toolName(toolRequest.name())
                                .text(toolResult.resultText())
                                .isError(toolResult.isError())
                                .attributes(toolResult.attributes())
                                .build();
                        addToMemory(toolExecutionResultMessage);
                        immediateToolReturn = immediateToolReturn
                                && context.toolService.isImmediateTool(toolExecutionResultMessage.toolName());
                    } catch (ExecutionException e) {
                        if (e.getCause() instanceof RuntimeException re) {
                            throw re;
                        } else {
                            throw new RuntimeException(e.getCause());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
            } else {
                // Classify requests before executing (mirrors non-streaming path).
                // This ensures END/ERROR behavior prevents ALL tools from executing,
                // not just those appearing after the over-budget tool in the list.
                boolean endLoop = false;
                Map<String, Integer> projectedCounts = new HashMap<>(toolInvocationCounts);
                for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                    String toolName = toolRequest.name();
                    int currentCount = projectedCounts.getOrDefault(toolName, 0);
                    int limit = context.toolService.maxToolInvocationsFor(toolName);
                    if (currentCount >= limit) {
                        overBudgetToolNames.add(toolName);
                        ToolLimitExceededBehavior behavior = context.toolService.toolLimitExceededBehaviorFor(toolName);
                        if (behavior == ToolLimitExceededBehavior.ERROR) {
                            throw new ToolInvocationLimitExceededException(toolName, limit, currentCount + 1);
                        }
                        if (behavior == ToolLimitExceededBehavior.END) {
                            endLoop = true;
                        }
                    } else {
                        projectedCounts.merge(toolName, 1, Integer::sum);
                    }
                }

                // Execute within-budget tools (or skip all if END was triggered).
                // Re-check each request against projected counts to handle parallel calls
                // to the same tool (e.g., 3 calls to search with limit=2: first 2 execute, 3rd rejected).
                Map<String, Integer> executionCounts = new HashMap<>(toolInvocationCounts);
                for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                    String toolName = toolRequest.name();
                    int currentCount = executionCounts.getOrDefault(toolName, 0);
                    int limit = context.toolService.maxToolInvocationsFor(toolName);
                    ToolExecutionResult toolResult;
                    if (endLoop) {
                        toolResult = currentCount >= limit
                                ? ToolExecutionResult.builder()
                                        .isError(true)
                                        .resultText(String.format(TOOL_LIMIT_EXCEEDED_MESSAGE, toolName, limit))
                                        .build()
                                : ToolExecutionResult.builder()
                                        .isError(true)
                                        .resultText(TOOL_LOOP_ENDED_MESSAGE)
                                        .build();
                    } else if (currentCount >= limit) {
                        toolResult = ToolExecutionResult.builder()
                                .isError(true)
                                .resultText(String.format(TOOL_LIMIT_EXCEEDED_MESSAGE, toolName, limit))
                                .build();
                    } else {
                        toolResult = execute(toolRequest);
                        toolInvocationCounts.merge(toolName, 1, Integer::sum);
                        executionCounts.merge(toolName, 1, Integer::sum);
                    }

                    toolResults.add(toolResult);
                    ToolRequestResult toolRequestResult = new ToolRequestResult(toolRequest, toolResult);
                    fireToolExecutedEvent(toolRequestResult);
                    ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.builder()
                            .id(toolRequest.id())
                            .toolName(toolRequest.name())
                            .text(toolResult.resultText())
                            .isError(toolResult.isError())
                            .attributes(toolResult.attributes())
                            .build();
                    addToMemory(toolExecutionResultMessage);
                    immediateToolReturn =
                            immediateToolReturn && context.toolService.isImmediateTool(toolRequest.name());
                }
            }

            if (immediateToolReturn) {
                ChatResponse finalChatResponse = finalResponse(chatResponse, aiMessage);
                fireInvocationComplete(finalChatResponse);

                if (completeResponseHandler != null) {
                    completeResponseHandler.accept(finalChatResponse);
                }
                return;
            }

            // Check if any over-budget tools require END/ERROR behavior.
            // This runs after all tool results (both executed and rejected) are collected
            // into memory, so the conversation history is complete.
            for (String toolName : overBudgetToolNames) {
                int limit = context.toolService.maxToolInvocationsFor(toolName);
                ToolLimitExceededBehavior behavior = context.toolService.toolLimitExceededBehaviorFor(toolName);
                if (behavior == ToolLimitExceededBehavior.ERROR) {
                    int currentCount = toolInvocationCounts.getOrDefault(toolName, 0);
                    throw new ToolInvocationLimitExceededException(toolName, limit, currentCount + 1);
                }
                if (behavior == ToolLimitExceededBehavior.END) {
                    List<ChatMessage> endMessages = messagesToSend(invocationContext.chatMemoryId());
                    ChatRequestParameters endParams =
                            chatRequestParameters(invocationContext.methodArguments(), List.of());
                    ChatRequest endRequest = context.chatRequestTransformer.apply(
                            ChatRequest.builder()
                                    .messages(endMessages)
                                    .parameters(endParams)
                                    .build(),
                            invocationContext.chatMemoryId());

                    var endHandler = new AiServiceStreamingResponseHandler(
                            endRequest,
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
                            ToolServiceContext.Empty.INSTANCE,
                            sequentialToolsInvocationsLeft,
                            toolArgumentsErrorHandler,
                            toolExecutionErrorHandler,
                            toolExecutor,
                            commonGuardrailParams,
                            methodKey,
                            toolInvocationCounts);

                    fireRequestIssuedEvent(endRequest);
                    context.streamingChatModel.chat(endRequest, endHandler);
                    return;
                }
            }

            List<ChatMessage> messages = messagesToSend(invocationContext.chatMemoryId());

            ToolServiceContext updatedToolContext =
                    refreshDynamicProviders(toolServiceContext, messages, invocationContext);
            updatedToolContext = ToolSearchService.addFoundTools(updatedToolContext, toolResults);

            // Level 1: Remove exhausted tools before the next LLM call
            updatedToolContext =
                    updatedToolContext.withoutTools(context.toolService.findExhaustedTools(toolInvocationCounts));

            ChatRequestParameters parameters =
                    chatRequestParameters(invocationContext.methodArguments(), updatedToolContext.effectiveTools());

            ChatRequest nextChatRequest = context.chatRequestTransformer.apply(
                    ChatRequest.builder()
                            .messages(messages)
                            .parameters(parameters)
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
                    toolInvocationCounts);

            fireRequestIssuedEvent(nextChatRequest);
            context.streamingChatModel.chat(nextChatRequest, handler);
        } else {
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

    private ChatResponse finalResponse(ChatResponse completeResponse, AiMessage aiMessage) {
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(completeResponse.metadata().toBuilder()
                        .tokenUsage(tokenUsage.add(completeResponse.metadata().tokenUsage()))
                        .build())
                .build();
    }

    private ToolExecutionResult execute(ToolExecutionRequest toolRequest) {
        return context.toolService.executeTool(
                invocationContext, toolExecutors, toolRequest, beforeToolExecutionHandler, toolExecutionHandler);
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
        return getMemory(memoryId).messages();
    }

    @Override
    public void onError(Throwable error) {
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
