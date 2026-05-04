package dev.langchain4j.model.deliverance;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
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

class DeliveranceStreamingChatModelIT {

    static StreamingChatModel model;

    @BeforeAll
    static void setup() {
        AutoModelForCausaLm.Builder builder = DeliveranceModels.builder((Path) null,
                DeliveranceTestUtils.GEMMA_MODEL_NAME);
        builder.withTensorProvider(new ConfigurableTensorProvider(builder.getAllocator(),
                new WrappedForkJoinPool(WrappedForkJoinPool.autoSizeByCores())));

        model = DeliveranceStreamingChatModel.builder()
                .modelBuilder(builder)
                .defaultRequestParameters(parameters -> parameters
                        .temperature(0.0)
                        .topP(0.9)
                        .maxOutputTokens(64))
                .build();
    }

    @Test
    void should_stream_answer_and_return_response() throws Exception {
        String userMessage = "When is the best time of year to visit Japan?";

        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        model.chat(userMessage, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                answerBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        ChatResponse response = futureResponse.get(30, SECONDS);
        String streamedAnswer = answerBuilder.toString();

        assertThat(streamedAnswer).isNotBlank();

        AiMessage aiMessage = response.aiMessage();
        assertThat(streamedAnswer).contains(aiMessage.text());
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
