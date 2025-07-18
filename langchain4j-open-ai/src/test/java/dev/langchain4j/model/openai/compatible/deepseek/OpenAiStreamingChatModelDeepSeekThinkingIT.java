package dev.langchain4j.model.openai.compatible.deepseek;

import static dev.langchain4j.JsonTestUtils.jsonify;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

/**
 * See <a href="https://api-docs.deepseek.com/guides/reasoning_model">DeepSeek API Docs</a> for more info.
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class OpenAiStreamingChatModelDeepSeekThinkingIT { // TODO abstract? Move into AbstractBaseChatModelIT? name: OpenAiThinking...?

    private final SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

    @Test
    void should_answer_with_reasoning_when_returnThinking_is_true() throws Exception { // TODO name

        // given
        boolean returnThinking = true;

        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .modelName("deepseek-reasoner")
                .returnThinking(returnThinking)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        StringBuffer thinkingBuilder = new StringBuffer();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        StreamingChatResponseHandler spyHandler = spy(new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                System.out.println(); // TODO
            }

            @Override
            public void onPartialThinkingResponse(String partialThinkingResponse) {
                thinkingBuilder.append(partialThinkingResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        // when
        model.chat(List.of(userMessage), spyHandler);

        // then
        ChatResponse chatResponse = futureResponse.get(60, SECONDS);
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).isEqualTo(thinkingBuilder.toString());

        InOrder inOrder = inOrder(spyHandler);
        inOrder.verify(spyHandler, atLeastOnce()).onPartialThinkingResponse(any());
        inOrder.verify(spyHandler, atLeastOnce()).onPartialResponse(any());
        inOrder.verify(spyHandler).onCompleteResponse(any());
        inOrder.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler);

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage, aiMessage, userMessage2), handler2);

        // then
        AiMessage aiMessage2 = handler2.get().aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).containsIgnoringCase("Paris");

        // should NOT preserve thinking in the follow-up request
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body())
                .contains(jsonify(aiMessage.text()))
                .doesNotContain(jsonify(aiMessage.thinking()));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void should_answer_without_reasoning_when_returnThinking_is(Boolean returnThinking) throws Exception { // TODO name

        // given
        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .modelName("deepseek-reasoner")
                .returnThinking(returnThinking)
                .logRequests(true)
                .logResponses(true)
                .build();

        String userMessage = "What is the capital of Germany?";

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        StreamingChatResponseHandler spyHandler = spy(new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onPartialThinkingResponse(String partialThinkingResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        // when
        model.chat(userMessage, spyHandler);

        // then
        ChatResponse chatResponse = futureResponse.get(30, SECONDS);
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).isNull();

        verify(spyHandler, never()).onPartialThinkingResponse(any());
    }
}
