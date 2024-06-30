package dev.langchain4j.model.jlama;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.assertj.core.util.Files;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class JlamaStreamingChatLanguageModelIT
{

    static File tmpDir;
    static StreamingChatLanguageModel model;

    @BeforeAll
    static void setup() {
        tmpDir = Files.newTemporaryFolder();

        model = JlamaStreamingChatLanguageModel.builder()
                .modelName("tjake/TinyLlama-1.1B-Chat-v1.0-Jlama-Q4")
                .modelCachePath(tmpDir.toPath())
                .maxTokens(25)
                .build();
    }

    @Test
    void should_stream_answer_and_return_response() throws Exception {

        // given
        String userMessage = "hello";

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
        assertThat(aiMessage.text()).isEqualTo(streamedAnswer.substring(1)); //Jlama bug fix needed

        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
