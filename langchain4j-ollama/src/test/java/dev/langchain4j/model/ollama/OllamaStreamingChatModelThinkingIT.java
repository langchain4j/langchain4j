package dev.langchain4j.model.ollama;

import static dev.langchain4j.JsonTestUtils.jsonify;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
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
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class OllamaStreamingChatModelThinkingIT extends AbstractOllamaThinkingModelInfrastructure {
    // TODO do not serialize empty collections and arrays

    private final SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

    @Test
    void should_answer_with_thinking() throws Exception { // TODO name

        // given
        Boolean returnThinking = true;

        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .returnThinking(returnThinking) // TODO use the same name for all providers
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        StringBuffer thinkingBuilder = new StringBuffer();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        StreamingChatResponseHandler spyHandler = spy(new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onPartialThinkingResponse(String partialThinkingResponse) {
                thinkingBuilder.append(partialThinkingResponse);
                System.out.println("OLOLOOOOOOO");
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
        assertThat(aiMessage.text())
                .containsIgnoringCase("Berlin")
                .doesNotContain("<think>", "</think>");
        assertThat(aiMessage.thinking()).containsIgnoringCase("Berlin");
//        assertThat(aiMessage.thinking()).isEqualTo(thinkingBuilder.toString()); TODO

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

    @Test
    void should_answer_with_thinking_merged_with_content_when_returnThinking_is_not_set() { // TODO name

        // given
        Boolean returnThinking = null;

        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .returnThinking(returnThinking)
                .logRequests(true)
                .logResponses(true)
                .build();

        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());
        model.chat(userMessage, spyHandler);

        // then
        ChatResponse chatResponse = spyHandler.get();
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text())
                .containsIgnoringCase("Berlin")
                .contains("<think>", "</think>");
        assertThat(aiMessage.thinking()).isNull();

        InOrder inOrder = inOrder(spyHandler);
        inOrder.verify(spyHandler, atLeastOnce()).onPartialResponse(any());
        inOrder.verify(spyHandler).onCompleteResponse(any());
        inOrder.verifyNoMoreInteractions();
        verify(spyHandler).get();
        verifyNoMoreInteractions(spyHandler);
    }

    @Test
    void should_answer_without_thinking_when_returnThinking_is_false() { // TODO name

        // given
        Boolean returnThinking = false;

        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .returnThinking(returnThinking)
                .logRequests(true)
                .logResponses(true)
                .build();

        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());
        model.chat(userMessage, spyHandler);

        // then
        ChatResponse chatResponse = spyHandler.get();
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text())
                .containsIgnoringCase("Berlin")
                .doesNotContain("<think>", "</think>");
        assertThat(aiMessage.thinking()).isNull();

        InOrder inOrder = inOrder(spyHandler);
        inOrder.verify(spyHandler, atLeastOnce()).onPartialResponse(any());
        inOrder.verify(spyHandler).onCompleteResponse(any());
        inOrder.verifyNoMoreInteractions();
        verify(spyHandler).get();
        verifyNoMoreInteractions(spyHandler);
    }
}
