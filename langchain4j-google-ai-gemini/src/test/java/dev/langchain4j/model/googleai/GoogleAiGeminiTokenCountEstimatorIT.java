package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.Arrays;

import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.Test;

class GoogleAiGeminiTokenCountEstimatorIT {
    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void should_estimate_token_count_for_text() {
        // given
        TokenCountEstimator tokenCountEstimator = GoogleAiGeminiTokenCountEstimator.builder()
                .logRequestsAndResponses(true)
                .modelName("gemini-1.5-flash")
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .build();

        // when
        int count = tokenCountEstimator.estimateTokenCountInText("Hello world!");

        // then
        assertThat(count).isEqualTo(4);
    }

    @Test
    void should_estimate_token_count_for_a_message() {
        // given
        TokenCountEstimator tokenCountEstimator = GoogleAiGeminiTokenCountEstimator.builder()
                .logRequestsAndResponses(true)
                .modelName("gemini-1.5-flash")
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .build();

        // when
        int count = tokenCountEstimator.estimateTokenCountInMessage(UserMessage.from("Hello World!"));

        // then
        assertThat(count).isEqualTo(4);
    }

    @Test
    void should_estimate_token_count_for_list_of_messages() {
        // given
        TokenCountEstimator tokenCountEstimator = GoogleAiGeminiTokenCountEstimator.builder()
                .logRequestsAndResponses(true)
                .modelName("gemini-1.5-flash")
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .build();

        // when
        int count = tokenCountEstimator.estimateTokenCountInMessages(
                Arrays.asList(UserMessage.from("Hello World!"), AiMessage.from("Hi! How can I help you today?")));

        // then
        assertThat(count).isEqualTo(14);
    }

    @Test
    void should_estimate_token_count_for_tool_exec_reqs() {
        // given
        GoogleAiGeminiTokenCountEstimator tokenCountEstimator = GoogleAiGeminiTokenCountEstimator.builder()
                .logRequestsAndResponses(true)
                .modelName("gemini-1.5-flash")
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .build();

        // when
        int count = tokenCountEstimator.estimateTokenCountInToolExecutionRequests(Arrays.asList(
                ToolExecutionRequest.builder()
                        .name("weatherForecast")
                        .arguments("{ \"location\": \"Paris\" }")
                        .build(),
                ToolExecutionRequest.builder()
                        .name("weatherForecast")
                        .arguments("{ \"location\": \"London\" }")
                        .build()));

        // then
        assertThat(count).isEqualTo(29);
    }

    @Test
    void should_estimate_token_count_for_tool_specs() {
        // given
        GoogleAiGeminiTokenCountEstimator tokenCountEstimator = GoogleAiGeminiTokenCountEstimator.builder()
                .logRequestsAndResponses(true)
                .modelName("gemini-1.5-flash")
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .build();

        // when
        int count = tokenCountEstimator.estimateTokenCountInToolSpecifications(Arrays.asList(
                ToolSpecification.builder()
                        .name("weatherForecast")
                        .description("Get the weather forecast for a given location on a give date")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("location", "the location")
                                .addStringProperty("date", "the date")
                                .required("location", "date")
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("convertFahrenheitToCelsius")
                        .description("Convert a temperature in Fahrenheit to Celsius")
                        .parameters(JsonObjectSchema.builder()
                                .addNumberProperty("fahrenheit", "the temperature in Fahrenheit")
                                .required("fahrenheit")
                                .build())
                        .build()));

        // then
        assertThat(count).isEqualTo(102);
    }
}
