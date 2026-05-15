package dev.langchain4j.model.deliverance;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import io.teknek.deliverance.math.WrappedForkJoinPool;
import io.teknek.deliverance.model.AutoModelForCausaLm;
import io.teknek.deliverance.tensor.operations.ConfigurableTensorProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveranceStreamingLanguageModelIT {

    static StreamingLanguageModel model;

    @BeforeAll
    static void setup() {
        AutoModelForCausaLm.Builder builder = DeliveranceModels.builder((Path) null,
                DeliveranceTestUtils.GEMMA_MODEL_NAME);
        builder.withTensorProvider(new ConfigurableTensorProvider(builder.getAllocator(),
                new WrappedForkJoinPool(WrappedForkJoinPool.autoSizeByCores())));
        model = DeliveranceStreamingLanguageModel.builder()
                .modelBuilder(builder)
                .customizeGeneratorParameters(parameters -> parameters
                        .withTemperature(0.0f)
                        .withTopP(0.9f)
                        .withMaxTokens(64))
                .build();
    }

    @Test
    void should_stream_answer_and_return_response() throws Exception {
        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<Response<String>> futureResponse = new CompletableFuture<>();

        model.generate("When is the best time of year to visit Japan?", new StreamingResponseHandler<>() {
            @Override
            public void onNext(String token) {
                answerBuilder.append(token);
            }

            @Override
            public void onComplete(Response<String> response) {
                futureResponse.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        Response<String> response = futureResponse.get(30, SECONDS);
        String streamedAnswer = answerBuilder.toString();

        assertThat(streamedAnswer).isNotBlank();
        assertThat(streamedAnswer).contains(response.content());
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
