package dev.langchain4j.service;

import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.AiServiceParamsUtil.chatRequestParameters;
import static dev.langchain4j.service.tool.ToolService.executeWithErrorHandling;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
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
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
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

    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final ToolArgumentsErrorHandler toolArgumentsErrorHandler;
    private final ToolExecutionErrorHandler toolExecutionErrorHandler;
    private final Executor toolExecutor;
    private final Queue<Future<ToolRequestResult>> toolExecutionFutures = new ConcurrentLinkedQueue<>();

    private final List<String> responseBuffer = new ArrayList<>();
    private final boolean hasOutputGuardrails;

    private int sequentialToolsInvocationsLeft;

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
            List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors,
            int sequentialToolsInvocationsLeft,
            ToolArgumentsErrorHandler toolArgumentsErrorHandler,
            ToolExecutionErrorHandler toolExecutionErrorHandler,
            Executor toolExecutor,
            GuardrailRequestParams commonGuardrailParams,
            Object methodKey) {
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

        this.toolSpecifications = copy(toolSpecifications);
        this.toolExecutors = copy(toolExecutors);
        this.toolArgumentsErrorHandler = ensureNotNull(toolArgumentsErrorHandler, "toolArgumentsErrorHandler");
        this.toolExecutionErrorHandler = ensureNotNull(toolExecutionErrorHandler, "toolExecutionErrorHandler");
        this.toolExecutor = toolExecutor;

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
            return;
        }
        if (partialToolCallWithContextHandler != null) {
            PartialToolCallContext context = new PartialToolCallContext(new CancellationUnsupportedStreamingHandle());
            partialToolCallWithContextHandler.accept(partialToolCall, context);
        }
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
        if (partialToolCallHandler != null) {
            partialToolCallHandler.accept(partialToolCall);
            return;
        }
        if (partialToolCallWithContextHandler != null) {
            partialToolCallWithContextHandler.accept(partialToolCall, context);
        }
    }

    @Override
    public void onCompleteToolCall(CompleteToolCall completeToolCall) {
        if (toolExecutor != null) {
            ToolExecutionRequest toolRequest = completeToolCall.toolExecutionRequest();
            var future = CompletableFuture.supplyAsync(
                    () -> {
                        ToolExecutionResult toolResult = execute(toolRequest);
                        return new ToolRequestResult(toolRequest, toolResult);
                    },
                    toolExecutor);
            toolExecutionFutures.add(future);
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

            if (toolExecutor != null) {
                for (Future<ToolRequestResult> toolExecutionFuture : toolExecutionFutures) {
                    try {
                        ToolRequestResult toolRequestResult = toolExecutionFuture.get();
                        fireToolExecutedEvent(toolRequestResult);
                        ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                                toolRequestResult.request(),
                                toolRequestResult.result().resultText());
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
                for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                    ToolExecutionResult toolResult = execute(toolRequest);
                    ToolRequestResult toolRequestResult = new ToolRequestResult(toolRequest, toolResult);
                    fireToolExecutedEvent(toolRequestResult);
                    addToMemory(ToolExecutionResultMessage.from(toolRequest, toolResult.resultText()));
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

            ChatRequest nextChatRequest = context.chatRequestTransformer.apply(
                    ChatRequest.builder()
                            .messages(messagesToSend(invocationContext.chatMemoryId()))
                            .parameters(chatRequestParameters(invocationContext.methodArguments(), toolSpecifications))
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
                    toolSpecifications,
                    toolExecutors,
                    sequentialToolsInvocationsLeft,
                    toolArgumentsErrorHandler,
                    toolExecutionErrorHandler,
                    toolExecutor,
                    commonGuardrailParams,
                    methodKey);

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
        ToolExecutor toolExecutor = toolExecutors.get(toolRequest.name());
        ToolExecutionResult toolResult;
        handleBeforeTool(toolRequest);
        if (toolExecutor == null) {
            toolResult = context.toolService.applyToolHallucinationStrategy(toolRequest);
        } else {
            toolResult = executeWithErrorHandling(
                    toolRequest, toolExecutor, invocationContext, toolArgumentsErrorHandler, toolExecutionErrorHandler);
        }
        handleAfterTool(toolRequest, toolResult);
        return toolResult;
    }

    private void handleBeforeTool(ToolExecutionRequest request) {
        if (beforeToolExecutionHandler != null) {
            BeforeToolExecution beforeToolExecution =
                    BeforeToolExecution.builder().request(request).build();
            beforeToolExecutionHandler.accept(beforeToolExecution);
        }
    }

    private void handleAfterTool(ToolExecutionRequest request, ToolExecutionResult result) {
        if (toolExecutionHandler != null) {
            ToolExecution toolExecution =
                    ToolExecution.builder().request(request).result(result).build();
            toolExecutionHandler.accept(toolExecution);
        }
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
