package dev.langchain4j.model.dashscope;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static dev.langchain4j.model.dashscope.QwenTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenStreamingChatModelIT {

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#nonMultimodalChatModelNameProvider")
    public void should_send_non_multimodal_messages_and_receive_response(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(chatMessages(), handler);
        Response<AiMessage> response = handler.get();
        System.out.println(response);

        assertThat(response.content().text()).containsIgnoringCase("rain");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#multimodalChatModelNameProvider")
    public void should_send_multimodal_image_url_and_receive_response(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();;
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(multimodalChatMessagesWithImageUrl(), handler);
        Response<AiMessage> response = handler.get();
        System.out.println(response);

        assertThat(response.content().text()).containsIgnoringCase("dog");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#multimodalChatModelNameProvider")
    public void should_send_multimodal_image_data_and_receive_response(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(multimodalChatMessagesWithImageData(), handler);
        Response<AiMessage> response = handler.get();
        System.out.println(response);

        assertThat(response.content().text()).containsIgnoringCase("parrot");
    }
}
