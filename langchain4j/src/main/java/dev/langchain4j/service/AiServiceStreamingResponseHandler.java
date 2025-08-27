package dev.langchain4j.service;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
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
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
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
    private final Object memoryId;
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
    private final Queue<CompletableFuture<ToolExecutionResultMessage>> toolResultFutures = new ConcurrentLinkedQueue<>();

    private final List<String> responseBuffer = new ArrayList<>();
    private final boolean hasOutputGuardrails;

    AiServiceStreamingResponseHandler(
            ChatExecutor chatExecutor,
            AiServiceContext context,
            Object memoryId,
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
        this.memoryId = ensureNotNull(memoryId, "memoryId");
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
            CompletableFuture<ToolExecutionResultMessage> future = CompletableFuture.supplyAsync(() -> {
                ToolExecutionRequest toolExecutionRequest = completeToolCall.toolExecutionRequest();
                return execute(toolExecutionRequest);
            }, toolExecutor);
            toolResultFutures.add(future);
        }
    }

    @Override
    public void onCompleteResponse(ChatResponse chatResponse) {
        AiMessage aiMessage = chatResponse.aiMessage();
        addToMemory(aiMessage);

        if (aiMessage.hasToolExecutionRequests()) {

            if (intermediateResponseHandler != null) {
                intermediateResponseHandler.accept(chatResponse);
            }
          
            boolean immediateToolReturn = true;

            if (toolExecutor != null) {
                for (CompletableFuture<ToolExecutionResultMessage> toolResultFuture : toolResultFutures) {
                    try {
                        ToolExecutionResultMessage toolExecutionResultMessage = toolResultFuture.get();
                        addToMemory(toolExecutionResultMessage);
                        immediateToolReturn = immediateToolReturn && context.toolService.isImmediateTool(toolExecutionResultMessage.toolName());
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
                for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                    ToolExecutionResultMessage toolExecutionResultMessage = execute(toolExecutionRequest);
                    addToMemory(toolExecutionResultMessage);
                    immediateToolReturn = immediateToolReturn && context.toolService.isImmediateTool(toolExecutionRequest.name());
                }
            }

            if (immediateToolReturn) {
                if (completeResponseHandler != null) {
                    ChatResponse finalChatResponse = finalResponse(chatResponse, aiMessage);
                    completeResponseHandler.accept(finalChatResponse);
                }
                return;
            }

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messagesToSend(memoryId))
                    .toolSpecifications(toolSpecifications)
                    .build();

            var handler = new AiServiceStreamingResponseHandler(
                    chatExecutor,
                    context,
                    memoryId,
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
            if (completeResponseHandler != null) {
                ChatResponse finalChatResponse = finalResponse(chatResponse, aiMessage);

                // Invoke output guardrails
                if (hasOutputGuardrails) {
                    if (commonGuardrailParams != null) {
                        var newCommonParams = GuardrailRequestParams.builder()
                                .chatMemory(getMemory())
                                .augmentationResult(commonGuardrailParams.augmentationResult())
                                .userMessageTemplate(commonGuardrailParams.userMessageTemplate())
                                .variables(commonGuardrailParams.variables())
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

                completeResponseHandler.accept(finalChatResponse);
            }
        }
    }

    private ChatResponse finalResponse(ChatResponse completeResponse, AiMessage aiMessage) {
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(completeResponse.metadata().toBuilder()
                        .tokenUsage(tokenUsage.add(
                                completeResponse.metadata().tokenUsage()))
                        .build())
                .build();
    }

    private ToolExecutionResultMessage execute(ToolExecutionRequest toolRequest) {
        ToolExecutor toolExecutor = toolExecutors.get(toolRequest.name());
        // TODO applyToolHallucinationStrategy
        handleBeforeTool(toolRequest);
        ToolExecutionResultMessage toolResult = executeWithErrorHandling(toolRequest, toolExecutor, memoryId,
                toolArgumentsErrorHandler, toolExecutionErrorHandler);
        handleAfterTool(toolRequest, toolResult);
        return toolResult;
    }

    private void handleBeforeTool(ToolExecutionRequest toolExecutionRequest) {
        if (beforeToolExecutionHandler != null) {
            BeforeToolExecution beforeToolExecution = BeforeToolExecution.builder()
                    .request(toolExecutionRequest)
                    .build();
            beforeToolExecutionHandler.accept(beforeToolExecution);
        }
    }

    private void handleAfterTool(ToolExecutionRequest toolRequest, ToolExecutionResultMessage toolResult) {
        if (toolExecutionHandler != null) {
            ToolExecution toolExecution = ToolExecution.builder()
                    .request(toolRequest)
                    .result(toolResult.text())
                    .build();
            toolExecutionHandler.accept(toolExecution);
        }
    }

    private ChatMemory getMemory() {
        return getMemory(memoryId);
    }

    private ChatMemory getMemory(Object memId) {
        return context.hasChatMemory() ? context.chatMemoryService.getOrCreateChatMemory(memoryId) : temporaryMemory;
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
