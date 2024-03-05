package dev.langchain4j.model.qianfan;

import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+")
class QianfanStreamingLanguageModelIT {

    //see your api key and secret key here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application
    private String apiKey = System.getenv("QIANFAN_API_KEY");
    private String secretKey = System.getenv("QIANFAN_SECRET_KEY");

    QianfanStreamingLanguageModel model = QianfanStreamingLanguageModel.builder().endpoint("sqlcoder_7b").topP(1.0).maxRetries(1)
            .apiKey(apiKey)
            .secretKey(secretKey)

            .build();

    @Test
    void should_stream_answer_and_return_response()  {

        // given
        String prompt = "hello";

        // when

        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();

        model.generate(prompt, handler);

        Response<String> response = handler.get();

        // then
        assertThat(response).isNotNull();

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
    }
}