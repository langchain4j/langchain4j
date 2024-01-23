package dev.langchain4j.model.dashscope;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static dev.langchain4j.model.dashscope.QwenTestHelper.apiKey;
import static dev.langchain4j.model.dashscope.QwenTestHelper.multiModalChatMessages;
import static org.assertj.core.api.Assertions.assertThat;

public class QwenMultiModalChatModelIT {
    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#multiModalChatModelNameProvider")
    public void should_send_messages_and_receive_response(String modelName) {
        String apiKey = apiKey();
        if (Utils.isNullOrBlank(apiKey)) {
            return;
        }

        ChatLanguageModel model = QwenMultiModalChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();;

        Response<AiMessage> response = model.generate(multiModalChatMessages());
        System.out.println(response);

        assertThat(response.content().text()).containsIgnoringCase("dog");
    }
}
