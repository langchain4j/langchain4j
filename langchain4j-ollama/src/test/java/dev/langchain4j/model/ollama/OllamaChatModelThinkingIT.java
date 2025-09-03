package dev.langchain4j.model.ollama;

import static dev.langchain4j.JsonTestUtils.jsonify;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

class OllamaChatModelThinkingIT extends AbstractOllamaThinkingModelInfrastructure {

    private final SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

    @Test
    void should_think_and_return_thinking() {

        // given
        boolean think = true;
        boolean returnThinking = true;

        ChatModel model = OllamaChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)

                .think(think)
                .returnThinking(returnThinking)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text())
                .containsIgnoringCase("Berlin")
                .doesNotContain("<think>", "</think>");
        assertThat(aiMessage.thinking()).isNotBlank();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        ChatResponse chatResponse2 = model.chat(userMessage, aiMessage, userMessage2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).isNotBlank();

        // should NOT send thinking in the follow-up request
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body())
                .contains(jsonify(aiMessage.text()))
                .doesNotContain(jsonify(aiMessage.thinking()));
    }

    @Test
    void should_think_and_NOT_return_thinking() {

        // given
        boolean think = true;
        boolean returnThinking = false;

        ChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)

                .think(think)
                .returnThinking(returnThinking)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text())
                .containsIgnoringCase("Berlin")
                .doesNotContain("<think>", "</think>");
        assertThat(aiMessage.thinking()).isNull();

        // TODO verify that raw HTTP response contains "thinking" field and that it is not sent back on the follow-up request
    }

    @Test
    void should_NOT_think() {

        // given
        boolean think = false;

        ChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)

                .think(think)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text())
                .containsIgnoringCase("Berlin")
                .doesNotContain("<think>", "</think>");
        assertThat(aiMessage.thinking()).isNull();

        // TODO verify that raw HTTP response does not contain "thinking" field
    }

    @Test
    void should_answer_with_thinking_prepended_to_content_when_think_is_not_set() {

        // given
        Boolean think = null;

        ChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)

                .think(think)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text())
                .containsIgnoringCase("Berlin")
                .contains("<think>", "</think>");
        assertThat(aiMessage.thinking()).isNull();
    }
}
