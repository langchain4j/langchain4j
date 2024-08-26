package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.SneakyThrows;

import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestStreamingResponseHandler<T> implements StreamingResponseHandler<T> {

    private final CompletableFuture<Response<T>> futureResponse = new CompletableFuture<>();

    private final StringBuffer textContentBuilder = new StringBuffer();

    @Override
    public void onNext(String token) {
        textContentBuilder.append(token);
    }

    @Override
    public void onComplete(Response<T> response) {

        String expectedTextContent = textContentBuilder.toString();
        if (response.content() instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) response.content();
            if (aiMessage.hasToolExecutionRequests()){
                assertThat(aiMessage.toolExecutionRequests().size()).isGreaterThan(0);
                assertThat(aiMessage.text()).isNull();
            } else {
                assertThat(aiMessage.text()).isEqualTo(expectedTextContent);
            }
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

    @SneakyThrows
    public Response<T> get() {
        return futureResponse.get(30, SECONDS);
    }
}
