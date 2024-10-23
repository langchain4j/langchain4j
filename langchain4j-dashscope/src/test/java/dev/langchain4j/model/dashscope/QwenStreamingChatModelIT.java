package dev.langchain4j.model.dashscope;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.model.dashscope.QwenTestHelper.*;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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

        assertThat(response.content().text()).containsIgnoringCase("rain");
        assertThat(response.content().text()).endsWith("That's all!");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#vlChatModelNameProvider")
    public void should_send_multimodal_image_url_and_receive_response(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();;
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(multimodalChatMessagesWithImageUrl(), handler);
        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).containsIgnoringCase("dog");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#vlChatModelNameProvider")
    public void should_send_multimodal_image_data_and_receive_response(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(multimodalChatMessagesWithImageData(), handler);
        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).containsIgnoringCase("parrot");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#audioChatModelNameProvider")
    public void should_send_multimodal_audio_url_and_receive_response(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();;
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(multimodalChatMessagesWithAudioUrl(), handler);
        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).containsIgnoringCase("阿里云");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#audioChatModelNameProvider")
    public void should_send_multimodal_audio_data_and_receive_response(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(multimodalChatMessagesWithAudioData(), handler);
        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).containsIgnoringCase("阿里云");
    }
}
