package dev.langchain4j.model.localai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class LocalAiStreamingChatModelIT extends AbstractLocalAiInfrastructure {

    StreamingChatLanguageModel model = LocalAiStreamingChatModel.builder()
            .baseUrl(localAi.getBaseUrl())
            .modelName("ggml-gpt4all-j")
            .maxTokens(3)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_stream_answer_and_return_response() throws Exception {

        // given
        String userMessage = "hello";

        // when
        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        model.generate(userMessage, new StreamingResponseHandler<AiMessage>() {

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
        assertThat(aiMessage.text()).isEqualTo(streamedAnswer);

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isEqualTo(STOP);
    }
}