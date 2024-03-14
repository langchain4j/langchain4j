package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;

import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestStreamingResponseHandler<T> implements StreamingResponseHandler<T> {

    private final CompletableFuture<Response<T>> futureResponse = new CompletableFuture<>();

    private final StringBuffer textContentBuilder = new StringBuffer();

    @Override
    public void onNext(String token) {
        System.out.println("onNext: '" + token + "'");
        textContentBuilder.append(token);
    }

    @Override
    public void onComplete(Response<T> response) {
        System.out.println("onComplete: '" + response + "'");

        String expectedTextContent = textContentBuilder.toString();
        if (response.content() instanceof AiMessage) {
            assertThat(((AiMessage) response.content()).text()).isEqualTo(expectedTextContent);
        } else if (response.content() instanceof String) {
            assertThat(response.content()).isEqualTo(expectedTextContent);
        } else {
            throw illegalArgument("Unknown response content: " + response.content());
        }

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
