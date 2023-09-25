package dev.langchain4j.model.dashscope;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static dev.langchain4j.model.dashscope.QwenTestHelper.chatMessages;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class QwenStreamingChatModelIT {

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#chatModelNameProvider")
    public void should_send_messages_and_receive_response(String modelName) throws ExecutionException, InterruptedException, TimeoutException {
        String apiKey = QwenTestHelper.apiKey();
        if (Utils.isNullOrBlank(apiKey)) {
            return;
        }

        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();

        CompletableFuture<Response<AiMessage>> future = new CompletableFuture<>();
        model.generate(chatMessages(), new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                System.out.println("onNext: '" + token + "'");
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                future.complete(response);
                System.out.println("onComplete: '" + response.content().text() + "'");
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        Response<AiMessage> response = future.get(30, SECONDS);
        System.out.println(response);

        assertThat(response.content().text()).containsIgnoringCase("rain");
    }
}
