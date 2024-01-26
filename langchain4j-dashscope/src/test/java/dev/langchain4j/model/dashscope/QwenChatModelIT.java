package dev.langchain4j.model.dashscope;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static dev.langchain4j.model.dashscope.QwenTestHelper.apiKey;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenChatModelIT {

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#chatModelNameProvider")
    public void should_send_messages_and_receive_response(String modelName) {
        ChatLanguageModel model = QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        Response<AiMessage> response = model.generate(QwenTestHelper.chatMessages());
        System.out.println(response);

        assertThat(response.content().text()).containsIgnoringCase("rain");
    }
}
