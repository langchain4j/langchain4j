package dev.langchain4j.service;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.tool.ToolService.executeWithErrorHandling;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.audit.api.event.AiServiceInvocationCompletedEvent;
import dev.langchain4j.audit.api.event.AiServiceInvocationErrorEvent;
import dev.langchain4j.audit.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.audit.api.event.ToolExecutedEvent;
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
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
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
    private final AiServiceContext context;
    private final InvocationContext invocationContext;
    private final GuardrailRequestParams commonGuardrailParams;
    private final Object methodKey;

    private final Consumer<String> partialResponseHandler;
    private final Consumer<PartialThinking> partialThinkingHandler;
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

    private record ToolRequestResult(ToolExecutionRequest request, ToolExecutionResult result) {}

    AiServiceStreamingResponseHandler(
            ChatExecutor chatExecutor,
            AiServiceContext context,
            InvocationContext invocationContext,
            Consumer<String> partialResponseHandler,
            Consumer<PartialThinking> partialThinkingHandler,
            Consumer<BeforeToolExecution> beforeToolExecutionHandler,
            Consumer<ToolExecution> toolExecutionHandler,
            Consumer<ChatResponse> intermediateResponseHandler,
            Consumer<ChatResponse> completeResponseHandler,
            Consumer<Throwable> errorHandler,
            ChatMemory temporaryMemory,
            TokenUsage tokenUsage,
            List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors,
            ToolArgumentsErrorHandler toolArgumentsErrorHandler,
            ToolExecutionErrorHandler toolExecutionErrorHandler,
            Executor toolExecutor,
            GuardrailRequestParams commonGuardrailParams,
            Object methodKey) {
        this.chatExecutor = ensureNotNull(chatExecutor, "chatExecutor");
        this.context = ensureNotNull(context, "context");
        this.invocationContext = ensureNotNull(invocationContext, "invocationContext");
        this.methodKey = methodKey;

        this.partialResponseHandler = ensureNotNull(partialResponseHandler, "partialResponseHandler");
        this.partialThinkingHandler = partialThinkingHandler;
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
    }

    @Override
    public void onPartialResponse(String partialResponse) {
        // If we're using output guardrails, then buffer the partial response until the guardrails have completed
        if (hasOutputGuardrails) {
            responseBuffer.add(partialResponse);
        } else {
            partialResponseHandler.accept(partialResponse);
        }
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking) {
        if (partialThinkingHandler != null) {
            partialThinkingHandler.accept(partialThinking);
        }
    }

    @Override
    public void onCompleteToolCall(CompleteToolCall completeToolCall) {
        if (toolExecutor != null) {
            ToolExecutionRequest toolExecutionRequest = completeToolCall.toolExecutionRequest();
            var future = CompletableFuture.supplyAsync(
                    () -> new ToolRequestResult(toolExecutionRequest, execute(toolExecutionRequest)), toolExecutor);
            toolExecutionFutures.add(future);
        }
    }

    private <T> void fireInteractionComplete(T result) {
        context.auditInvocationEventListenerRegistrar.fireEvent(AiServiceInvocationCompletedEvent.builder()
                .invocationContext(commonGuardrailParams.invocationContext())
                .result(result)
                .build());
    }

    private void fireToolExecutedEvent(ToolRequestResult toolRequestResult) {
        context.auditInvocationEventListenerRegistrar.fireEvent(ToolExecutedEvent.builder()
                .invocationContext(commonGuardrailParams.invocationContext())
                .request(toolRequestResult.request())
                .result(toolRequestResult.result().resultText())
                .build());
    }

    private void fireResponseReceivedEvent(ChatResponse chatResponse) {
        context.auditInvocationEventListenerRegistrar.fireEvent(AiServiceResponseReceivedEvent.builder()
                .invocationContext(commonGuardrailParams.invocationContext())
                .response(chatResponse)
                .build());
    }

    private void fireErrorReceived(Throwable error) {
        context.auditInvocationEventListenerRegistrar.fireEvent(AiServiceInvocationErrorEvent.builder()
                .invocationContext(commonGuardrailParams.invocationContext())
                .error(error)
                .build());
    }

    @Override
    public void onCompleteResponse(ChatResponse chatResponse) {
        fireResponseReceivedEvent(chatResponse);
        AiMessage aiMessage = chatResponse.aiMessage();
        addToMemory(aiMessage);

        if (aiMessage.hasToolExecutionRequests()) {

            if (intermediateResponseHandler != null) {
                intermediateResponseHandler.accept(chatResponse);
            }

            boolean immediateToolReturn = true;

            if (toolExecutor != null) {
                for (Future<ToolRequestResult> toolExecutionFuture : toolExecutionFutures) {
                    try {
                        ToolRequestResult toolExecutionRequestResult = toolExecutionFuture.get();
                        fireToolExecutedEvent(toolExecutionRequestResult);
                        ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                                toolExecutionRequestResult.request(),
                                toolExecutionRequestResult.result().resultText());
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
                fireInteractionComplete(finalChatResponse);

                if (completeResponseHandler != null) {
                    completeResponseHandler.accept(finalChatResponse);
                }
                return;
            }

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messagesToSend(invocationContext.chatMemoryId()))
                    .toolSpecifications(toolSpecifications)
                    .build();

            var handler = new AiServiceStreamingResponseHandler(
                    chatExecutor,
                    context,
                    invocationContext,
                    partialResponseHandler,
                    partialThinkingHandler,
                    beforeToolExecutionHandler,
                    toolExecutionHandler,
                    intermediateResponseHandler,
                    completeResponseHandler,
                    errorHandler,
                    temporaryMemory,
                    TokenUsage.sum(tokenUsage, chatResponse.metadata().tokenUsage()),
                    toolSpecifications,
                    toolExecutors,
                    toolArgumentsErrorHandler,
                    toolExecutionErrorHandler,
                    toolExecutor,
                    commonGuardrailParams,
                    methodKey);

            context.streamingChatModel.chat(chatRequest, handler);
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
                    responseBuffer.forEach(partialResponseHandler::accept);
                    responseBuffer.clear();
                }

                fireInteractionComplete(finalChatResponse);
                completeResponseHandler.accept(finalChatResponse);
            } else {
                fireInteractionComplete(finalChatResponse);
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
        // TODO applyToolHallucinationStrategy
        handleBeforeTool(toolRequest);
        ToolExecutionResult toolResult = executeWithErrorHandling(
                toolRequest, toolExecutor, invocationContext, toolArgumentsErrorHandler, toolExecutionErrorHandler);
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
