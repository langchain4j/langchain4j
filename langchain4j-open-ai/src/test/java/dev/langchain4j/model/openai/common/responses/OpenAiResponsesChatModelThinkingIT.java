package dev.langchain4j.model.openai.common.responses;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesChatModelThinkingIT {

    private static final String ENCRYPTED_REASONING_KEY = "encrypted_reasoning";

    private static final ToolSpecification WEATHER_TOOL = ToolSpecification.builder()
            .name("getWeather")
            .description("Returns the current weather for a given city")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("city")
                    .required("city")
                    .build())
            .build();

    private static final ToolSpecification TIME_TOOL = ToolSpecification.builder()
            .name("getTime")
            .description("Returns the current time for a given country")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("country")
                    .required("country")
                    .build())
            .build();

    @Test
    void should_return_reasoning_summary() {

        // given
        String reasoningSummary = "auto";

        ChatModel model = OpenAiResponsesChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("gpt-5-mini")
                .reasoningEffort("medium")
                .reasoningSummary(reasoningSummary)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage =
                UserMessage.from("A bat and ball cost $1.10 in total. The bat costs $1.00 more than the ball. "
                        + "How much does the ball cost? Think carefully step by step.");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("0.05");
        assertThat(aiMessage.thinking()).isNotBlank();

        OpenAiTokenUsage tokenUsage = (OpenAiTokenUsage) chatResponse.tokenUsage();
        assertThat(tokenUsage.outputTokensDetails().reasoningTokens()).isNotNull();
    }

    @Test
    void should_not_return_reasoning_summary_when_not_requested() {

        // given
        ChatModel model = OpenAiResponsesChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("gpt-5-mini")
                .reasoningEffort("medium")
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

    @Test
    void should_return_encrypted_reasoning_and_send_it_back__single_tool_call() {

        // given
        List<String> include = List.of("reasoning.encrypted_content");

        SpyingHttpClient spyingHttpClient =
                new SpyingHttpClient(JdkHttpClient.builder().build());

        ChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("gpt-5-mini")
                .reasoningEffort("medium")
                .include(include)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the weather in Munich?");

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL)
                        .build())
                .build();

        // when
        ChatResponse chatResponse1 = model.chat(chatRequest);

        // then
        AiMessage aiMessage1 = chatResponse1.aiMessage();
        assertThat(aiMessage1.toolExecutionRequests()).hasSize(1);
        assertThat(aiMessage1.toolExecutionRequests().get(0).name()).isEqualTo("getWeather");
        String encryptedContent1 = aiMessage1.attribute(ENCRYPTED_REASONING_KEY, String.class);
        assertThat(encryptedContent1).isNotBlank();

        // when
        ChatRequest chatRequest2 = ChatRequest.builder()
                .messages(
                        userMessage,
                        aiMessage1,
                        ToolExecutionResultMessage.from(
                                aiMessage1.toolExecutionRequests().get(0), "sunny, 22°C"))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL)
                        .build())
                .build();

        ChatResponse chatResponse2 = model.chat(chatRequest2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("sun");
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        // verify encrypted_content was sent back in turn 2
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body()).contains("encrypted_content").contains(encryptedContent1);
    }

    @Test
    void should_return_encrypted_reasoning_and_send_it_back__two_parallel_tool_calls() {

        // given
        List<String> include = List.of("reasoning.encrypted_content");

        SpyingHttpClient spyingHttpClient =
                new SpyingHttpClient(JdkHttpClient.builder().build());

        ChatModel model = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("gpt-5-mini")
                .reasoningEffort("medium")
                .include(include)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from(
                "What is the weather in Munich and the current time in France? Call both tools in parallel.");

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL, TIME_TOOL)
                        .build())
                .build();

        // when
        ChatResponse chatResponse1 = model.chat(chatRequest);

        // then
        AiMessage aiMessage1 = chatResponse1.aiMessage();
        assertThat(aiMessage1.toolExecutionRequests()).hasSize(2);
        String encryptedContent1 = aiMessage1.attribute(ENCRYPTED_REASONING_KEY, String.class);
        assertThat(encryptedContent1).isNotBlank();

        // when
        ChatRequest chatRequest2 = ChatRequest.builder()
                .messages(
                        userMessage,
                        aiMessage1,
                        ToolExecutionResultMessage.from(
                                aiMessage1.toolExecutionRequests().get(0), "sunny, 22°C"),
                        ToolExecutionResultMessage.from(
                                aiMessage1.toolExecutionRequests().get(1), "14:35"))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL, TIME_TOOL)
                        .build())
                .build();

        ChatResponse chatResponse2 = model.chat(chatRequest2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();

        // verify encrypted_content was sent back in turn 2
        List<HttpRequest> httpRequests = spyingHttpClient.requests();
        assertThat(httpRequests).hasSize(2);
        assertThat(httpRequests.get(1).body()).contains("encrypted_content").contains(encryptedContent1);
    }
}
