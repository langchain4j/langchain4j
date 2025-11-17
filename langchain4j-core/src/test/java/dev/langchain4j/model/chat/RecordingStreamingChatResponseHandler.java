package dev.langchain4j.model.chat;

import static java.util.concurrent.TimeUnit.SECONDS;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Streaming handler that records partial responses but does not assert on the final result.
 * Useful for integration tests where the model may not stream every token.
 */
public class RecordingStreamingChatResponseHandler implements StreamingChatResponseHandler {

    private final StringBuffer partialResponseBuilder = new StringBuffer();
    private final CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

    @Override
    public void onPartialResponse(String partialResponse) {
        partialResponseBuilder.append(partialResponse);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        futureResponse.complete(completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        futureResponse.completeExceptionally(error);
    }

    public ChatResponse get() {
        try {
            return futureResponse.get(60, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public String getPartialResponse() {
        return partialResponseBuilder.toString();
    }
}
