package dev.langchain4j.model.dashscope;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static dev.langchain4j.model.dashscope.QwenTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenMultiModalChatModelIT {
    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#multiModalChatModelNameProvider")
    public void should_send_image_url_and_receive_response(String modelName) {
        ChatLanguageModel model = QwenMultiModalChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();;

        Response<AiMessage> response = model.generate(multiModalChatMessagesWithImageUrl());
        System.out.println(response);

        assertThat(response.content().text()).containsIgnoringCase("dog");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#multiModalChatModelNameProvider")
    public void should_send_image_data_and_receive_response(String modelName) {
        ChatLanguageModel model = QwenMultiModalChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();;

        Response<AiMessage> response = model.generate(multiModalChatMessagesWithImageData());
        System.out.println(response);

        assertThat(response.content().text()).containsIgnoringCase("parrot");
    }
}
