package dev.langchain4j.model.ollama;

import static dev.langchain4j.JsonTestUtils.jsonify;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class OllamaStreamingChatModelThinkingIT extends AbstractOllamaThinkingModelInfrastructure {

    private final SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

    @Test
    void should_think_and_return_thinking() {

        // given
        boolean think = true;
        boolean returnThinking = true;

        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)

                .think(think)
                .returnThinking(returnThinking)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingChatResponseHandler spyHandler1 = spy(new TestStreamingChatResponseHandler());
        model.chat(List.of(userMessage1), spyHandler1);

        // then
        AiMessage aiMessage1 = spyHandler1.get().aiMessage();
        assertThat(aiMessage1.text())
                .containsIgnoringCase("Berlin")
                .doesNotContain("<think>", "</think>");
        assertThat(aiMessage1.thinking())
                .isNotBlank()
                .isEqualTo(spyHandler1.getThinking());

        InOrder inOrder1 = inOrder(spyHandler1);
        inOrder1.verify(spyHandler1).get();
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialThinking(any());
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialResponse(any());
        inOrder1.verify(spyHandler1).onCompleteResponse(any());
        inOrder1.verify(spyHandler1).getThinking();
        inOrder1.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler1);

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage1, aiMessage1, userMessage2), handler2);

        // then
        AiMessage aiMessage2 = handler2.get().aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).isNotBlank();

        // should NOT send thinking in the follow-up request
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body())
                .contains(jsonify(aiMessage1.text()))
                .doesNotContain(jsonify(aiMessage1.thinking()));
    }

    @Test
    void should_think_and_NOT_return_thinking() {

        // given
        boolean think = true;
        boolean returnThinking = false;

        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)

                .think(think)
                .returnThinking(returnThinking)

                .logRequests(true)
                .logResponses(true)
                .build();

        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());
        model.chat(userMessage, spyHandler);

        // then
        AiMessage aiMessage = spyHandler.get().aiMessage();
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

        // TODO verify that raw SSE events contain "thinking" field and that it is not sent back on the follow-up request
    }

    @Test
    void should_NOT_think() {

        // given
        boolean think = false;

        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)

                .think(think)

                .logRequests(true)
                .logResponses(true)
                .build();

        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());
        model.chat(userMessage, spyHandler);

        // then
        AiMessage aiMessage = spyHandler.get().aiMessage();
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

        // TODO verify that raw SSE events do not contain "thinking" field
    }

    @Test
    void should_answer_with_thinking_prepended_to_content_when_think_is_not_set() {

        // given
        Boolean think = null;

        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)

                .think(think)

                .logRequests(true)
                .logResponses(true)
                .build();

        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());
        model.chat(userMessage, spyHandler);

        // then
        AiMessage aiMessage = spyHandler.get().aiMessage();
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
}
