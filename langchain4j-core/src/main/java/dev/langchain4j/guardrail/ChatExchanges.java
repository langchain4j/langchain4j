package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;

import dev.langchain4j.model.chat.ChatExchange;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.concurrent.CompletableFuture;

final class ChatExchanges {

    private ChatExchanges() {}

    static CompletableFuture<ChatExchange> exchangeAsync(
            ChatRequest request, CompletableFuture<ChatResponse> responseFuture) {
        CompletableFuture<ChatExchange> exchangeFuture =
                responseFuture.thenApply(response -> new ChatExchange(request, response));
        propagateCancellation(exchangeFuture, responseFuture);
        return exchangeFuture;
    }
}
