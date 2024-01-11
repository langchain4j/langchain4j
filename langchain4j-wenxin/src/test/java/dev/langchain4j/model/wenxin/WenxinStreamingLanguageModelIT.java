package dev.langchain4j.model.wenxin;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class WenxinStreamingLanguageModelIT  {

    //see your client id and secret key here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application
    private String clientId ="your client id";
    private String secretKey ="your secret key";
    WenxinStreamingLanguageModel model = WenxinStreamingLanguageModel.builder().serviceName("sqlcoder_7b").topP(1.0f).maxRetries(1)
            .clientId(clientId)
            .secretKey(secretKey)

            .build();

    @Test
    void should_stream_answer_and_return_response() throws Exception {

        // given
        String prompt = "hello";

        // when
        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<Response<String>> futureResponse = new CompletableFuture<>();

        model.generate(prompt, new StreamingResponseHandler<String>() {

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

        // then
        assertThat(streamedAnswer).isNotBlank();

        assertThat(response.content()).isEqualTo(streamedAnswer);

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
    }
}