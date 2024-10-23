package dev.langchain4j.model.azure;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.model.azure.AzureOpenAiLanguageModelName.GPT_3_5_TURBO_INSTRUCT;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class AzureOpenAiStreamingLanguageModelIT {

    StreamingLanguageModel model = AzureOpenAiStreamingLanguageModel.builder()
            .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .apiKey(System.getenv("AZURE_OPENAI_KEY"))
            .deploymentName("gpt-35-turbo-instruct-0914")
            .tokenizer(new AzureOpenAiTokenizer(GPT_3_5_TURBO_INSTRUCT))
            .temperature(0.0)
            .maxTokens(20)
            .logRequestsAndResponses(true)
            .build();

    @Test
    void should_stream_answer_and_finish_reason_stop() throws Exception {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<String>> futureResponse = new CompletableFuture<>();

        model.generate("The capital of France is: ", new StreamingResponseHandler<String>() {

            private final StringBuilder answerBuilder = new StringBuilder();

            @Override
            public void onNext(String token) {
                answerBuilder.append(token);
            }

            @Override
            public void onComplete(Response<String> response) {
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

        assertThat(answer).containsIgnoringCase("Paris");
        assertThat(response.content()).isEqualTo(answer);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(7);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(1);
        assertThat(response.tokenUsage().totalTokenCount()).isGreaterThan(8);

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_and_finish_reason_length() throws Exception {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<String>> futureResponse = new CompletableFuture<>();

        model.generate("Describe the capital of France in 100 words: ", new StreamingResponseHandler<String>() {

            private final StringBuilder answerBuilder = new StringBuilder();

            @Override
            public void onNext(String token) {
                answerBuilder.append(token);
            }

            @Override
            public void onComplete(Response<String> response) {
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

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}