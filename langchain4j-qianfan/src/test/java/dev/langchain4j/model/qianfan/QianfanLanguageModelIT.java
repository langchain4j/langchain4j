package dev.langchain4j.model.qianfan;


import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class QianfanLanguageModelIT {

    //see your api key and secret key here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application
    private String apiKey ="your api key";
    private String secretKey ="your secret key";
    QianfanLanguageModel model = QianfanLanguageModel.builder().endpoint("codellama_7b_instruct").topP(1.0f).maxRetries(1)
            .apiKey(apiKey)
            .secretKey(secretKey)

            .build();

    @Test
    void should_send_prompt_and_return_response() {

        // given
        String prompt = "hello";

        // when
        Response<String> response = model.generate(prompt);
        System.out.println(response);

        // then
        assertThat(response.content()).isNotBlank();

        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isNull();
    }
}