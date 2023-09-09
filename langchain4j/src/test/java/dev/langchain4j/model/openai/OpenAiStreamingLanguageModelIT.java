package dev.langchain4j.model.openai;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Result;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiStreamingLanguageModelIT {

    StreamingLanguageModel model = OpenAiStreamingLanguageModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_stream_answer() throws ExecutionException, InterruptedException, TimeoutException {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Result<String>> futureResult = new CompletableFuture<>();

        model.generate("What is the capital of Germany?", new StreamingResponseHandler<String>() {

            private final StringBuilder answerBuilder = new StringBuilder();

            @Override
            public void onNext(String token) {
                System.out.println("onNext: '" + token + "'");
                answerBuilder.append(token);
            }

            @Override
            public void onComplete(Result<String> result) {
                System.out.println("onComplete: '" + result + "'");
                futureAnswer.complete(answerBuilder.toString());
                futureResult.complete(result);
            }

            @Override
            public void onError(Throwable error) {
                futureAnswer.completeExceptionally(error);
                futureResult.completeExceptionally(error);
            }
        });

        String answer = futureAnswer.get(30, SECONDS);
        Result<String> result = futureResult.get(30, SECONDS);

        assertThat(answer).contains("Berlin");
        assertThat(result.get()).isEqualTo(answer);

        assertThat(result.tokenUsage().inputTokenCount()).isEqualTo(7);
        assertThat(result.tokenUsage().outputTokenCount()).isGreaterThan(1);
        assertThat(result.tokenUsage().totalTokenCount()).isGreaterThan(8);

        assertThat(result.finishReason()).isEqualTo(STOP);
    }
}