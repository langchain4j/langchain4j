package dev.langchain4j.model.openai.compatible.deepseek;

import static dev.langchain4j.JsonTestUtils.jsonify;
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
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * See <a href="https://api-docs.deepseek.com/guides/reasoning_model">DeepSeek API Docs</a> for more info.
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class OpenAiChatModelDeepSeekThinkingIT {

    private final SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

    @Test
    void should_return_thinking() {

        // given
        boolean returnThinking = true;

        ChatModel model = OpenAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .modelName("deepseek-reasoner")

                .returnThinking(returnThinking)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse1 = model.chat(userMessage1);

        // then
        AiMessage aiMessage1 = chatResponse1.aiMessage();
        assertThat(aiMessage1.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage1.thinking()).isNotBlank();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        ChatResponse chatResponse2 = model.chat(userMessage1, aiMessage1, userMessage2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).isNotBlank();

        // should NOT send thinking in the follow-up request
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body())
                .contains(jsonify(aiMessage1.text()))
                .doesNotContain(jsonify(aiMessage1.thinking()));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void should_NOT_return_thinking(Boolean returnThinking) {

        // given
        ChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .modelName("deepseek-reasoner")

                .returnThinking(returnThinking)

                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).isNull();
    }
}
