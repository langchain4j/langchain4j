package dev.langchain4j.model.googleai;

import static dev.langchain4j.JsonTestUtils.jsonify;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GoogleAiGeminiChatModelThinkingIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    static final int THOUGHT_LENGTH_THRESHOLD = 100; // TODO this is brittle, check raw HTTP responses instead

    private final SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder().build());

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_think_and_return_thinking(boolean sendThinking) {

        // given
        boolean includeThoughts = true;
        boolean returnThinking = true;

        GeminiThinkingConfig thinkingConfig = GeminiThinkingConfig.builder()
                .includeThoughts(includeThoughts)
                .thinkingBudget(20)
                .build();

        ChatModel model = GoogleAiGeminiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")

                .thinkingConfig(thinkingConfig)
                .returnThinking(returnThinking)
                .sendThinking(sendThinking)

                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse1 = model.chat(userMessage1);

        // then
        AiMessage aiMessage1 = chatResponse1.aiMessage();
        assertThat(aiMessage1.text())
                .containsIgnoringCase("Berlin")
                .hasSizeLessThan(THOUGHT_LENGTH_THRESHOLD);
        assertThat(aiMessage1.thinking())
                .isNotBlank()
                .hasSizeGreaterThan(THOUGHT_LENGTH_THRESHOLD);
        assertThat(aiMessage1.text()).doesNotContain(aiMessage1.thinking());
        assertThat(aiMessage1.attributes()).isEmpty();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        ChatResponse chatResponse2 = model.chat(userMessage1, aiMessage1, userMessage2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text())
                .containsIgnoringCase("Paris")
                .hasSizeLessThan(THOUGHT_LENGTH_THRESHOLD);
        assertThat(aiMessage2.thinking())
                .isNotBlank()
                .hasSizeGreaterThan(THOUGHT_LENGTH_THRESHOLD);
        assertThat(aiMessage2.text()).doesNotContain(aiMessage2.thinking());
        assertThat(aiMessage2.attributes()).isEmpty();

        // should send thinking in the follow-up request
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body()).contains(jsonify(aiMessage1.text()));
        if (sendThinking) {
            assertThat(httpRequests.get(1).body()).contains(jsonify(aiMessage1.thinking()));
        } else {
            assertThat(httpRequests.get(1).body()).doesNotContain(jsonify(aiMessage1.thinking()));
        }
    }

    @Test
    void should_think_and_NOT_return_thinking() {

        // given
        boolean includeThoughts = true;
        boolean returnThinking = false;

        GeminiThinkingConfig thinkingConfig = GeminiThinkingConfig.builder()
                .includeThoughts(includeThoughts)
                .thinkingBudget(20)
                .build();

        ChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")

                .thinkingConfig(thinkingConfig)
                .returnThinking(returnThinking)

                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text())
                .containsIgnoringCase("Berlin")
                .hasSizeLessThan(THOUGHT_LENGTH_THRESHOLD);
        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.attributes()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_think_and_return_thinking_with_tools(boolean sendThinking) {

        // given
        boolean includeThoughts = true;
        boolean returnThinking = true;

        GeminiThinkingConfig thinkingConfig = GeminiThinkingConfig.builder()
                .includeThoughts(includeThoughts)
                .thinkingBudget(20)
                .build();

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("getWeather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();

        ChatModel model = GoogleAiGeminiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")

                .thinkingConfig(thinkingConfig)
                .returnThinking(returnThinking)
                .sendThinking(sendThinking)
                .defaultRequestParameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecification)
                        .build())

                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the weather in Munich?");

        // when
        ChatResponse chatResponse1 = model.chat(userMessage1);

        // then
        AiMessage aiMessage1 = chatResponse1.aiMessage();
        String thinking1 = aiMessage1.thinking();
        assertThat(thinking1).isNotBlank();
        String signature1 = aiMessage1.attribute("thinking_signature", String.class);
        assertThat(signature1).isNotBlank();
        assertThat(aiMessage1.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest1 = aiMessage1.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest1.arguments()).contains("Munich");

        // given
        ToolExecutionResultMessage toolResultMessage1 = ToolExecutionResultMessage.from(toolExecutionRequest1, "sunny");

        // when
        ChatResponse chatResponse2 = model.chat(userMessage1, aiMessage1, toolResultMessage1);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("sun");
        assertThat(aiMessage2.thinking()).isNull();
        assertThat(aiMessage2.attributes()).isEmpty();
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the weather in Paris?");

        // when
        ChatResponse chatResponse3 = model.chat(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2);

        // then
        AiMessage aiMessage3 = chatResponse3.aiMessage();
        String thinking2 = aiMessage3.thinking();
        assertThat(thinking2).isNotBlank();
        String signature2 = aiMessage3.attribute("thinking_signature", String.class);
        assertThat(signature2).isNotBlank();
        assertThat(aiMessage3.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest2 = aiMessage3.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest2.name()).isEqualTo(toolSpecification.name());
        assertThat(toolExecutionRequest2.arguments()).contains("Paris");

        // given
        ToolExecutionResultMessage toolResultMessage2 = ToolExecutionResultMessage.from(toolExecutionRequest2, "rainy");

        // when
        ChatResponse chatResponse4 = model.chat(userMessage1, aiMessage1, toolResultMessage1, aiMessage2, userMessage2, aiMessage3, toolResultMessage2);

        // then
        AiMessage aiMessage4 = chatResponse4.aiMessage();
        assertThat(aiMessage4.text()).containsIgnoringCase("rain");
        if (sendThinking) {
            assertThat(aiMessage4.thinking()).isNull();
            assertThat(aiMessage4.attributes()).isEmpty();
        }
        assertThat(aiMessage4.toolExecutionRequests()).isEmpty();

        // should send thinking in the follow-up requests
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(4);

        if (sendThinking) {
            assertThat(httpRequests.get(1).body())
                    .contains(jsonify(thinking1))
                    .contains(jsonify(signature1));
        } else {
            assertThat(httpRequests.get(1).body())
                    .doesNotContain(jsonify(thinking1))
                    .doesNotContain(jsonify(signature1));
        }

        if (sendThinking) {
            assertThat(httpRequests.get(3).body())
                    .contains(jsonify(thinking2))
                    .contains(jsonify(signature2));
        } else {
            assertThat(httpRequests.get(3).body())
                    .doesNotContain(jsonify(thinking2))
                    .doesNotContain(jsonify(signature2));
        }
    }

    @Test
    void should_NOT_think() {

        // given
        GeminiThinkingConfig thinkingConfig = null;

        ChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")

                .thinkingConfig(thinkingConfig)

                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text())
                .contains("Berlin")
                .hasSizeLessThan(THOUGHT_LENGTH_THRESHOLD);
        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.attributes()).isEmpty();
    }

    @Test
    void should_answer_with_thinking_prepended_to_content_when_returnThinking_is_not_set() {

        // given
        boolean includeThoughts = true;
        Boolean returnThinking = null;

        GeminiThinkingConfig thinkingConfig = GeminiThinkingConfig.builder()
                .includeThoughts(includeThoughts)
                .thinkingBudget(20)
                .build();

        ChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")

                .thinkingConfig(thinkingConfig)
                .returnThinking(returnThinking)

                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text())
                .contains("Berlin")
                .hasSizeGreaterThan(THOUGHT_LENGTH_THRESHOLD);
        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.attributes()).isEmpty();
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
