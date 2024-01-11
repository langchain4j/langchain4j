package dev.langchain4j.model.wenxin;


import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WenxinLanguageModelIT  {

    //see your client id and secret key here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application
    private String clientId ="your client id";
    private String secretKey ="your secret key";
    WenxinLanguageModel model = WenxinLanguageModel.builder().serviceName("sqlcoder_7b").topP(1.0f).maxRetries(1)
            .clientId(clientId)
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