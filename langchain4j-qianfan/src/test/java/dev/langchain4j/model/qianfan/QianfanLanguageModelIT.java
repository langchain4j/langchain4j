package dev.langchain4j.model.qianfan;


import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+")
class QianfanLanguageModelIT {

    //see your api key and secret key here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application
    private String apiKey = System.getenv("QIANFAN_API_KEY");
    private String secretKey = System.getenv("QIANFAN_SECRET_KEY");

    QianfanLanguageModel model = QianfanLanguageModel.builder().endpoint("codellama_7b_instruct").topP(1.0).maxRetries(1)
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
        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage).isNotNull();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(1);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        assertThat(response.finishReason()).isNull();
    }
}