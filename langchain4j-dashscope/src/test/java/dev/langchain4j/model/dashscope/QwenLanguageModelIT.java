package dev.langchain4j.model.dashscope;

import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

public class QwenLanguageModelIT {
    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#languageModelNameProvider")
    public void should_send_messages_and_receive_response(String modelName) {
        String apiKey = QwenTestHelper.apiKey();
        if (Utils.isNullOrBlank(apiKey)) {
            return;
        }

        LanguageModel model = QwenLanguageModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
        Response<String> response = model.generate("Please say 'hello' to me");
        System.out.println(response);

        assertThat(response.content()).containsIgnoringCase("hello");
    }
}
