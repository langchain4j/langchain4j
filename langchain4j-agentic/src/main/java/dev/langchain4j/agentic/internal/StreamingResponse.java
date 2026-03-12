package dev.langchain4j.agentic.internal;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import java.util.concurrent.CompletableFuture;

public class StreamingResponse implements DelayedResponse<String> {

    private final CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

    public StreamingResponse(TokenStream tokenStream) {
        tokenStream
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();
    }

    @Override
    public boolean isDone() {
        return futureResponse.isDone();
    }

    @Override
    public String blockingGet() {
        return futureResponse.join().aiMessage().text();
    }

    @Override
    public String toString() {
        return result().toString();
    }
}
