package dev.langchain4j.agentic.internal;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import java.util.concurrent.CompletableFuture;

public class StreamingResponse implements DelayedResponse<String> {

    private final TokenStream tokenStream;

    private final StringBuilder answerBuilder = new StringBuilder();
    private final CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

    public StreamingResponse(TokenStream tokenStream) {
        this.tokenStream = tokenStream;
        tokenStream
                .onPartialResponse(answerBuilder::append)
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();
    }

    public TokenStream tokenStream() {
        return tokenStream;
    }

    @Override
    public boolean isDone() {
        return futureResponse.isDone();
    }

    @Override
    public String blockingGet() {
        futureResponse.join();
        return answerBuilder.toString();
    }

    @Override
    public String toString() {
        return result().toString();
    }
}
