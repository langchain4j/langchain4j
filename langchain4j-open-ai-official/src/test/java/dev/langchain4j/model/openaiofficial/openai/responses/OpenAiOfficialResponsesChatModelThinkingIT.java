package dev.langchain4j.model.openaiofficial.openai.responses;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialSpyingHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static com.openai.client.okhttp.OkHttpClient.*;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesChatModelThinkingIT {

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
        Reasoning.Summary reasoningSummary = Reasoning.Summary.AUTO;

        ChatModel model = OpenAiOfficialResponsesChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("gpt-5-mini")
                .reasoningEffort(ReasoningEffort.LOW)
                .reasoningSummary(reasoningSummary)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse = model.chat(userMessage);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).isNotBlank();

        OpenAiOfficialTokenUsage tokenUsage =
                ((OpenAiOfficialResponsesChatResponseMetadata) chatResponse.metadata()).tokenUsage();
        assertThat(tokenUsage.outputTokensDetails()).isNotNull();
        assertThat(tokenUsage.outputTokensDetails().reasoningTokens()).isNotNull();
    }

    @Test
    void should_not_return_reasoning_summary_when_not_requested() {

        // given
        ChatModel model = OpenAiOfficialResponsesChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("gpt-5-mini")
                .reasoningEffort(ReasoningEffort.LOW)
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

        OpenAiOfficialSpyingHttpClient spyingHttpClient = new OpenAiOfficialSpyingHttpClient(builder().build());

        ChatModel model = OpenAiOfficialResponsesChatModel.builder()
                .client(spyingOpenAIClient(spyingHttpClient))
                .modelName("gpt-5-mini")
                .reasoningEffort(ReasoningEffort.MEDIUM)
                .include(include)
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
                        ToolExecutionResultMessage.from(aiMessage1.toolExecutionRequests().get(0), "sunny, 22°C"))
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
        List<String> requestBodies = spyingHttpClient.requestBodies();
        assertThat(requestBodies).hasSize(2);
        assertThat(requestBodies.get(1))
                .contains("encrypted_content")
                .contains(encryptedContent1);
    }

    @Test
    void should_return_encrypted_reasoning_and_send_it_back__two_parallel_tool_calls() {

        // given
        List<String> include = List.of("reasoning.encrypted_content");

        OpenAiOfficialSpyingHttpClient spyingHttpClient = new OpenAiOfficialSpyingHttpClient(builder().build());

        ChatModel model = OpenAiOfficialResponsesChatModel.builder()
                .client(spyingOpenAIClient(spyingHttpClient))
                .modelName("gpt-5-mini")
                .reasoningEffort(ReasoningEffort.MEDIUM)
                .include(include)
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
                        ToolExecutionResultMessage.from(aiMessage1.toolExecutionRequests().get(0), "sunny, 22°C"),
                        ToolExecutionResultMessage.from(aiMessage1.toolExecutionRequests().get(1), "14:35"))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL, TIME_TOOL)
                        .build())
                .build();

        ChatResponse chatResponse2 = model.chat(chatRequest2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();

        // verify encrypted_content was sent back in turn 2
        List<String> requestBodies = spyingHttpClient.requestBodies();
        assertThat(requestBodies).hasSize(2);
        assertThat(requestBodies.get(1))
                .contains("encrypted_content")
                .contains(encryptedContent1);
    }

    static OpenAIClient spyingOpenAIClient(OpenAiOfficialSpyingHttpClient spy) {
        String baseUrl = System.getenv("OPENAI_BASE_URL");
        String apiKey = System.getenv("OPENAI_API_KEY");
        String orgId = System.getenv("OPENAI_ORGANIZATION_ID");

        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder().apiKey(apiKey);
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        if (orgId != null) {
            builder.organization(orgId);
        }
        return builder.build().withOptions(b -> b.httpClient(spy));
    }
}
