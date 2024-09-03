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
import static org.assertj.core.api.Fail.fail;

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
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#multimodalChatModelNameProvider")
    public void should_send_multimodal_image_url_and_receive_response(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();;
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(multimodalChatMessagesWithImageUrl(), handler);
        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).containsIgnoringCase("dog");
        assertThat(response.content().text()).endsWith("That's all!");
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

        assertThat(response.content().text()).containsIgnoringCase("parrot");
        assertThat(response.content().text()).endsWith("That's all!");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#listenableModelNameProvider")
    void should_listen_request_and_response(String modelName) {

        // given
        AtomicReference<ChatModelRequest> requestReference = new AtomicReference<>();
        AtomicReference<ChatModelResponse> responseReference = new AtomicReference<>();

        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requestReference.set(requestContext.request());
                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                responseReference.set(responseContext.response());
                assertThat(responseContext.request()).isSameAs(requestReference.get());
                assertThat(responseContext.attributes().get("id")).isEqualTo("12345");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                fail("onError() must not be called");
            }
        };

        float temperature = 0.7f;
        double topP = 1.0;
        int maxTokens = 7;

        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .listeners(singletonList(listener))
                .build();

        UserMessage userMessage = UserMessage.from("hello");

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);
        AiMessage aiMessage = handler.get().content();

        // then
        ChatModelRequest request = requestReference.get();
        assertThat(request.model()).isEqualTo(modelName);
        assertThat(request.temperature()).isEqualTo(temperature);
        assertThat(request.topP()).isEqualTo(topP);
        assertThat(request.maxTokens()).isEqualTo(maxTokens);
        assertThat(request.messages()).containsExactly(userMessage);

        ChatModelResponse response = responseReference.get();
        assertThat(response.id()).isNotBlank();
        assertThat(response.model()).isNotBlank();
        assertThat(response.tokenUsage().inputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount()).isGreaterThan(0);
        assertThat(response.finishReason()).isNotNull();
        assertThat(response.aiMessage()).isEqualTo(aiMessage);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#listenableModelNameProvider")
    void should_listen_error(String modelName) throws ExecutionException, InterruptedException, TimeoutException {

        // given
        String wrongApiKey = "banana";

        AtomicReference<ChatModelRequest> requestReference = new AtomicReference<>();
        AtomicReference<Throwable> errorReference = new AtomicReference<>();

        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requestReference.set(requestContext.request());
                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                fail("onResponse() must not be called");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                errorReference.set(errorContext.error());
                assertThat(errorContext.request()).isSameAs(requestReference.get());
                assertThat(errorContext.partialResponse().aiMessage().text()).isBlank(); // can be non-null if it fails in the middle of streaming
                assertThat(errorContext.attributes().get("id")).isEqualTo("12345");
            }
        };

        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(wrongApiKey)
                .modelName(modelName)
                .listeners(singletonList(listener))
                .build();

        String userMessage = "this message will fail";

        CompletableFuture<Throwable> future = new CompletableFuture<>();
        StreamingResponseHandler<AiMessage> handler = new StreamingResponseHandler<AiMessage>() {

            @Override
            public void onNext(String token) {
                fail("onNext() must not be called");
            }

            @Override
            public void onError(Throwable error) {
                future.complete(error);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                fail("onComplete() must not be called");
            }
        };

        // when
        model.generate(userMessage, handler);
        Throwable throwable = future.get(5, SECONDS);

        // then
        assertThat(throwable).isExactlyInstanceOf(com.alibaba.dashscope.exception.ApiException.class);
        assertThat(throwable).hasMessageContaining("Invalid API-key provided");

        assertThat(errorReference.get()).isSameAs(throwable);
    }
}
