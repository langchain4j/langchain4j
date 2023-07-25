package dev.langchain4j.model.localai;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
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

        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder answerBuilder = new StringBuilder();

        model.sendUserMessage("Say 'hello'", new StreamingResponseHandler() {

            @Override
            public void onNext(String token) {
                answerBuilder.append(token);
            }

            @Override
            public void onComplete() {
                future.complete(answerBuilder.toString());
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        String answer = future.get(30, SECONDS);

        assertThat(answer).containsIgnoringCase("hello");
    }
}