package dev.langchain4j.model.dashscope;

import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class QwenStreamingLanguageModelIT {

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#languageModelNameProvider")
    public void should_send_messages_and_receive_response(String modelName) throws ExecutionException, InterruptedException, TimeoutException {
        String apiKey = QwenTestHelper.apiKey();
        if (Utils.isNullOrBlank(apiKey)) {
            return;
        }

        StreamingLanguageModel model = QwenStreamingLanguageModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();

        CompletableFuture<Response<String>> future = new CompletableFuture<>();
        model.generate("Please say 'hello' to me", new StreamingResponseHandler<String>() {
            @Override
            public void onNext(String token) {
                System.out.println("onNext: '" + token + "'");
            }

            @Override
            public void onComplete(Response<String> response) {
                future.complete(response);
                System.out.println("onComplete: '" + response.content() + "'");
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        Response<String> response = future.get(30, SECONDS);
        System.out.println(response);

        assertThat(response.content()).containsIgnoringCase("hello");
    }
}
