package dev.langchain4j.model.jlama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class JlamaStreamingChatModelIT {

    static File tmpDir;
    static StreamingChatLanguageModel model;

    @BeforeAll
    static void setup() {
        tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();

        model = JlamaStreamingChatModel.builder()
                .modelName("tjake/TinyLlama-1.1B-Chat-v1.0-Jlama-Q4")
                .modelCachePath(tmpDir.toPath())
                .maxTokens(30)
                .temperature(0.0f)
                .build();
    }

    @Test
    void should_stream_answer_and_return_response() throws Exception {

        // given
        String userMessage = "When is the best time of year to visit Japan?";

        // when
        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        model.generate(userMessage, new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {
                answerBuilder.append(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                futureResponse.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        Response<AiMessage> response = futureResponse.get(30, SECONDS);
        String streamedAnswer = answerBuilder.toString();

        // then
        assertThat(streamedAnswer).isNotBlank();

        AiMessage aiMessage = response.content();
        assertThat(streamedAnswer).contains(aiMessage.text());

        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
