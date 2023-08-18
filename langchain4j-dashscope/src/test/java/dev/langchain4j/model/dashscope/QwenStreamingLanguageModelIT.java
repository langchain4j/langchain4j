package dev.langchain4j.model.dashscope;

import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class QwenStreamingLanguageModelIT {
    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#chatModelNameProvider")
    public void should_send_messages_and_receive_response(String modelName) throws ExecutionException, InterruptedException, TimeoutException {
        String apiKey = QwenTestHelper.apiKey();
        if (Utils.isNullOrBlank(apiKey)) {
            return;
        }
        StreamingLanguageModel model = QwenStreamingLanguageModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        model.process(
                "Please say 'hello' to me",
                new StreamingResponseHandler() {
                    final StringBuilder answerBuilder = new StringBuilder();
                    @Override
                    public void onNext(String partialResult) {
                        answerBuilder.append(partialResult);
                        System.out.println("onPartialResult: '" + partialResult + "'");
                    }
                    @Override
                    public void onComplete() {
                        future.complete(answerBuilder.toString());
                        System.out.println("onComplete");
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
