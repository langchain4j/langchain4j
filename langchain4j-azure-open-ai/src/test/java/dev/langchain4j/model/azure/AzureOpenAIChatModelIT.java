package dev.langchain4j.model.azure;

import com.azure.core.util.BinaryData;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.*;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.*;
import static org.assertj.core.api.Assertions.assertThat;

public class AzureOpenAIChatModelIT {

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        ChatLanguageModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .serviceVersion(System.getenv("AZURE_OPENAI_SERVICE_VERSION"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
                .build();
        UserMessage userMessage = userMessage("hello, how are you?");

        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(13);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(1);
        assertThat(tokenUsage.totalTokenCount()).isGreaterThan(14);

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_length() {

        ChatLanguageModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .serviceVersion(System.getenv("AZURE_OPENAI_SERVICE_VERSION"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
                .maxTokens(3)
                .build();

        UserMessage userMessage = userMessage("hello, how are you?");

        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(13);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(3);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(16);

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_call_function_with_argument() {

        ChatLanguageModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .serviceVersion(System.getenv("AZURE_OPENAI_SERVICE_VERSION"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
                .build();

        UserMessage userMessage = userMessage("What should I wear in Boston depending on the weather?");

        String toolName = "getCurrentWeather";

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name(toolName)
                .description("Get the current weather")
                .parameters(getToolParameters())
                .build();

        Response<AiMessage> response = model.generate(Collections.singletonList(userMessage), toolSpecification);

        assertThat(response.content().text()).isBlank();
        assertThat(response.content().toolExecutionRequest().name()).isEqualTo(toolName);
        System.out.println(response);

        ToolExecutionRequest toolExecutionRequest = response.content().toolExecutionRequest();
        WeatherLocation weatherLocation = BinaryData.fromString(toolExecutionRequest.arguments()).toObject(WeatherLocation.class);
        int currentWeather = 0;
        if (Objects.equals(toolExecutionRequest.name(), toolName)) {
            currentWeather = getCurrentWeather(weatherLocation);
        }
        String weatherQuestion = String.format("The weather in %s is %d degrees %s.",
                weatherLocation.getLocation(), currentWeather, weatherLocation.getUnit());

        assertThat(weatherQuestion).isEqualTo("The weather in Boston, MA is 35 degrees celsius.");

        ToolExecutionResultMessage toolExecutionResultMessage = toolExecutionResultMessage(toolName, weatherQuestion);
        Response<AiMessage> response2 = model.generate(toolExecutionResultMessage);

        System.out.println(response2);

        assertThat(response2.content().text()).isNotBlank();
        assertThat(response2.finishReason()).isEqualTo(STOP);
    }

    private static ToolParameters getToolParameters() {
        Map<String, Map<String, Object>> properties = new HashMap<>();

        Map<String, Object> location = new HashMap<>();
        location.put("type", "string");
        location.put("description", "The city and state, e.g. San Francisco, CA");
        properties.put("location", location);

        Map<String, Object> unit = new HashMap<>();
        unit.put("type", "string");
        unit.put("enum", Arrays.asList("celsius", "fahrenheit"));
        properties.put("unit", unit);

        List<String> required = Arrays.asList("location", "unit");

        return ToolParameters.builder()
                .properties(properties)
                .required(required)
                .build();
    }

    // This is the method we offer to OpenAI to be used as a function_call.
    // For this example, we ignore the input parameter and return a simple value.
    private static int getCurrentWeather(WeatherLocation weatherLocation) {
        return 35;
    }

    // WeatherLocation is used for this sample. This describes the parameter of the function you want to use.
    private static class WeatherLocation {
        @JsonProperty(value = "unit") String unit;
        @JsonProperty(value = "location") String location;
        @JsonCreator
        WeatherLocation(@JsonProperty(value = "unit") String unit, @JsonProperty(value = "location") String location) {
            this.unit = unit;
            this.location = location;
        }

        public String getUnit() {
            return unit;
        }

        public String getLocation() {
            return location;
        }
    }
}
