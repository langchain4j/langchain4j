package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestStreamingChatResponseHandler implements StreamingChatResponseHandler {

    private final StringBuffer responseBuilder = new StringBuffer();
    private final CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

    @Override
    public void onPartialResponse(String partialResponse) {
        responseBuilder.append(partialResponse);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        AiMessage aiMessage = completeResponse.aiMessage();
        if (!aiMessage.hasToolExecutionRequests()) {
            assertThat(aiMessage.text()).isEqualTo(responseBuilder.toString());
        }
        futureResponse.complete(completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        futureResponse.completeExceptionally(error);
    }

    public ChatResponse get() {
        try {
            return futureResponse.get(30, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
