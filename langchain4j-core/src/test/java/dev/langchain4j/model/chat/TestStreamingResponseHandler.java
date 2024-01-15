package dev.langchain4j.model.chat;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;

import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;

public class TestStreamingResponseHandler<T> implements StreamingResponseHandler<T> {

    private final CompletableFuture<Response<T>> futureResponse = new CompletableFuture<>();

    @Override
    public void onNext(String token) {
        System.out.println("onNext: '" + token + "'");
    }

    @Override
    public void onComplete(Response<T> response) {
        System.out.println("onComplete: '" + response + "'");
        futureResponse.complete(response);
    }

    @Override
    public void onError(Throwable error) {
        futureResponse.completeExceptionally(error);
    }

    public Response<T> get() {
        try {
            return futureResponse.get(30, SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
