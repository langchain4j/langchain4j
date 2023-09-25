package dev.langchain4j.model.localai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class LocalAiStreamingChatModelIT {

    @Test
    @Disabled("until we host LocalAI instance somewhere")
    void should_stream_answer() throws Exception {

        StreamingChatLanguageModel model = LocalAiStreamingChatModel.builder()
                .baseUrl("http://localhost:8080")
                .modelName("ggml-gpt4all-j")
                .maxTokens(3)
                .logRequests(true)
                .logResponses(true)
                .build();

        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();

        model.generate("Say 'hello'", new StreamingResponseHandler<AiMessage>() {

            @Override
            public void onNext(String token) {
                answerBuilder.append(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                futureAnswer.complete(answerBuilder.toString());
            }

            @Override
            public void onError(Throwable error) {
                futureAnswer.completeExceptionally(error);
            }
        });

        String answer = futureAnswer.get(30, SECONDS);

        assertThat(answer).containsIgnoringCase("hello");
    }
}