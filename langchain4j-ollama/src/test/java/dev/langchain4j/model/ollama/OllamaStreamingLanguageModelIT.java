package dev.langchain4j.model.ollama;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled("needs Ollama running locally")
class OllamaStreamingLanguageModelIT {

    StreamingLanguageModel model = OllamaStreamingLanguageModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("orca-mini")
            .build();

    @Test
    void should_stream_answer() throws ExecutionException, InterruptedException, TimeoutException {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<String>> futureResponse = new CompletableFuture<>();

        model.generate("What is the capital of Germany?", new StreamingResponseHandler<String>() {

            private final StringBuilder answerBuilder = new StringBuilder();

            @Override
            public void onNext(String token) {
                System.out.println("onNext: '" + token + "'");
                answerBuilder.append(token);
            }

            @Override
            public void onComplete(Response<String> response) {
                System.out.println("onComplete: '" + response + "'");
                futureAnswer.complete(answerBuilder.toString());
                futureResponse.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                futureAnswer.completeExceptionally(error);
                futureResponse.completeExceptionally(error);
            }
        });

        String answer = futureAnswer.get(30, SECONDS);
        Response<String> response = futureResponse.get(30, SECONDS);

        assertThat(answer).contains("Berlin");
        assertThat(response.content()).isEqualTo(answer);
    }
}
