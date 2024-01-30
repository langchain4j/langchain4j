package dev.langchain4j.model.azure;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class AzureOpenAiStreamingLanguageModelIT {

    Logger logger = LoggerFactory.getLogger(AzureOpenAiStreamingLanguageModelIT.class);

    StreamingLanguageModel model = AzureOpenAiStreamingLanguageModel.builder()
            .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .serviceVersion(System.getenv("AZURE_OPENAI_SERVICE_VERSION"))
            .apiKey(System.getenv("AZURE_OPENAI_KEY"))
            .deploymentName("davinci-002")
            .logRequestsAndResponses(true)
            .build();

    @Test
    void should_stream_answer() throws Exception {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<String>> futureResponse = new CompletableFuture<>();

        model.generate("The capital of France is: ", new StreamingResponseHandler<String>() {

            private final StringBuilder answerBuilder = new StringBuilder();

            @Override
            public void onNext(String token) {
                logger.info("onNext: '" + token + "'");
                answerBuilder.append(token);
            }

            @Override
            public void onComplete(Response<String> response) {
                logger.info("onComplete: '" + response + "'");
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

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}