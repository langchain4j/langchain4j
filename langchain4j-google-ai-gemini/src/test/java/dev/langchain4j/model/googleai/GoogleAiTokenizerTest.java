package dev.langchain4j.model.googleai;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class GoogleAiTokenizerTest {
    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void should_estimate_token_count_for_text() {
        // given
        GoogleAiTokenizer tokenizer = GoogleAiTokenizer.builder()
            .logRequestsAndResponses(true)
            .modelName("gemini-1.5-flash")
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .build();

        // when
        int count = tokenizer.estimateTokenCountInText("Hello world!");

        // then
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void should_estimate_token_count_for_a_message() {
        // given
        GoogleAiTokenizer tokenizer = GoogleAiTokenizer.builder()
            .logRequestsAndResponses(true)
            .modelName("gemini-1.5-flash")
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .build();

        // when
        int count = tokenizer.estimateTokenCountInMessage(UserMessage.from("Hello World!"));

        // then
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void should_estimate_token_count_for_list_of_messages() {
        // given
        GoogleAiTokenizer tokenizer = GoogleAiTokenizer.builder()
            .logRequestsAndResponses(true)
            .modelName("gemini-1.5-flash")
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .build();

        // when
        int count = tokenizer.estimateTokenCountInMessages(
            Arrays.asList(
                UserMessage.from("Hello World!"),
                AiMessage.from("Hi! How can I help you today?")
            )
        );

        // then
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void should_estimate_token_count_for_tool_exec_reqs() {
        // given
        GoogleAiTokenizer tokenizer = GoogleAiTokenizer.builder()
            .logRequestsAndResponses(true)
            .modelName("gemini-1.5-flash")
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .build();

        // when
        int count = tokenizer.estimateTokenCountInToolExecutionRequests(
            Arrays.asList(
                ToolExecutionRequest.builder()
                    .name("weatherForecast")
                    .arguments("{ \"location\": \"Paris\" }")
                    .build(),
                ToolExecutionRequest.builder()
                    .name("weatherForecast")
                    .arguments("{ \"location\": \"London\" }")
                    .build()
            )
        );

        // then
        assertThat(count).isGreaterThan(0);
    }


    @Test
    void should_estimate_token_count_for_tool_specs() {
        // given
        GoogleAiTokenizer tokenizer = GoogleAiTokenizer.builder()
            .logRequestsAndResponses(true)
            .modelName("gemini-1.5-flash")
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .build();

        // when
        int count = tokenizer.estimateTokenCountInToolSpecifications(
            Arrays.asList(
                ToolSpecification.builder()
                    .name("weatherForecast")
                    .description("Get the weather forecast for a given location on a give date")
                    .addParameter("location", JsonSchemaProperty.STRING, JsonSchemaProperty.description("the location"))
                    .addParameter("date", JsonSchemaProperty.STRING, JsonSchemaProperty.description("the date"))
                    .build(),
                ToolSpecification.builder()
                    .name("convertFahrenheitToCelsius")
                    .description("Convert a temperature in Fahrenheit to Celsius")
                    .addParameter("fahrenheit", JsonSchemaProperty.NUMBER, JsonSchemaProperty.description("the temperature in Fahrenheit"))
                    .build()
            )
        );

        // then
        assertThat(count).isGreaterThan(0);
    }
}
