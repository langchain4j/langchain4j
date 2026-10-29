package dev.langchain4j.service;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;

import dev.langchain4j.model.chat.ChatExchange;
import dev.langchain4j.Internal;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolServiceContext;
import dev.langchain4j.service.tool.ToolServiceResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Builds the {@link ChatExecutor} handed to output guardrails on a {@code reprompt()}.
 * <p>
 * Output guardrails must only ever see a final textual response, never an intermediate tool-only
 * {@link dev.langchain4j.data.message.AiMessage}. So when the reprompted model call requests tools, this executor
 * fully resolves them through {@code ToolService.executeInferenceAndToolsLoop} before returning, instead of handing
 * the raw response straight to the next guardrail.
 * <p>
 * The same logic is used by every AI Service mode; they differ only in the model invoker used to perform each
 * model call: {@link #wrap} drives the synchronous tool loop (a {@code ChatModel} for the synchronous path, a
 * blocking {@code StreamingChatModel} call for the {@code TokenStream} path), while {@link #wrapAsync} drives the
 * asynchronous tool loop (a {@code ChatModel.chatAsync} for the {@code CompletableFuture} path, a non-blocking
 * {@code StreamingChatModel} call for the reactive {@code Flow.Publisher} path).
 */
@Internal
final class ToolAwareRepromptExecutor {

    private ToolAwareRepromptExecutor() {}

    /**
     * The last request of the tool loop and the response it ended with.
     */
    private static ChatExchange toolLoopExchange(ChatExchange initial, ToolServiceResult result) {
        ChatRequest request = result.lastChatRequest() != null ? result.lastChatRequest() : initial.request();
        return new ChatExchange(request, result.aggregateResponse());
    }

    /**
     * Synchronous tool-aware wrapper: {@link ChatExecutor#execute(List)} resolves any reprompt-requested tools
     * through the synchronous tool loop. Used by the synchronous and {@code TokenStream} output-guardrail paths.
     */
    static ChatExecutor wrap(
            ChatExecutor rawChatExecutor,
            AiServiceContext context,
            Object memoryId,
            ChatRequestParameters parameters,
            InvocationContext invocationContext,
            ToolServiceContext toolServiceContext,
            Function<ChatRequest, ChatResponse> chatModelInvoker) {
        return new ChatExecutor() {
            @Override
            public ChatResponse execute() {
                return rawChatExecutor.execute();
            }

            @Override
            public ChatResponse execute(List<ChatMessage> chatMessages) {
                return exchange(chatMessages).response();
            }

            @Override
            public ChatExchange exchange(List<ChatMessage> chatMessages) {
                ChatExchange initial = rawChatExecutor.exchange(chatMessages);

                if (!initial.response().aiMessage().hasToolExecutionRequests()) {
                    return initial;
                }

                ToolServiceResult result = context.toolService.executeInferenceAndToolsLoop(
                        context,
                        memoryId,
                        initial.response(),
                        parameters,
                        chatMessages,
                        null,
                        invocationContext,
                        toolServiceContext,
                        chatModelInvoker);
                return toolLoopExchange(initial, result);
            }
        };
    }

    /**
     * Non-blocking tool-aware wrapper: {@link ChatExecutor#executeAsync(List)} resolves any reprompt-requested
     * tools through the asynchronous tool loop, so the guardrail still only ever sees a final textual response,
     * without blocking the delivery thread. Used by the {@code CompletableFuture} and reactive output-guardrail
     * paths; {@code asyncChatModelInvoker} performs each model call (a {@code ChatModel.chatAsync} for the former,
     * a non-blocking {@code StreamingChatModel} call for the latter).
     */
    static ChatExecutor wrapAsync(
            ChatExecutor rawChatExecutor,
            AiServiceContext context,
            Object memoryId,
            ChatRequestParameters parameters,
            InvocationContext invocationContext,
            ToolServiceContext toolServiceContext,
            Function<ChatRequest, CompletableFuture<ChatResponse>> asyncChatModelInvoker) {
        return new ChatExecutor() {
            @Override
            public ChatResponse execute() {
                return rawChatExecutor.execute();
            }

            @Override
            public ChatResponse execute(List<ChatMessage> chatMessages) {
                return rawChatExecutor.execute(chatMessages);
            }

            @Override
            public CompletableFuture<ChatResponse> executeAsync(List<ChatMessage> chatMessages) {
                CompletableFuture<ChatExchange> exchange = exchangeAsync(chatMessages);
                CompletableFuture<ChatResponse> response = exchange.thenApply(ChatExchange::response);
                propagateCancellation(response, exchange);
                return response;
            }

            @Override
            public CompletableFuture<ChatExchange> exchangeAsync(List<ChatMessage> chatMessages) {
                CompletableFuture<ChatExchange> initialExchange = rawChatExecutor.exchangeAsync(chatMessages);
                CompletableFuture<ChatExchange> exchange = initialExchange.thenCompose(initial -> {
                    if (!initial.response().aiMessage().hasToolExecutionRequests()) {
                        return CompletableFuture.completedFuture(initial);
                    }
                    return context.toolService
                            .executeInferenceAndToolsLoopAsync(
                                    context,
                                    memoryId,
                                    initial.response(),
                                    parameters,
                                    chatMessages,
                                    null,
                                    invocationContext,
                                    toolServiceContext,
                                    null,
                                    asyncChatModelInvoker)
                            .thenApply(result -> toolLoopExchange(initial, result));
                });
                propagateCancellation(exchange, initialExchange);
                return exchange;
            }
        };
    }
}
