package dev.langchain4j.model.github;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.github.GitHubModelsChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.ai.inference.models.ChatCompletionsResponseFormatJsonObject;
import com.azure.core.util.BinaryData;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
class GitHubModelsChatModelIT {

    private static final Logger logger = LoggerFactory.getLogger(GitHubModelsChatModelIT.class);

    @Test
    void should_generate_answer_and_finish_reason_stop() {

        GitHubModelsChatModel model = GitHubModelsChatModel.builder()
                .gitHubToken(System.getenv("GITHUB_TOKEN"))
                .modelName(GPT_4_O_MINI)
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("What is the capital of France?");
        ChatResponse response = model.chat(userMessage);
        logger.info("Response: {}", response.aiMessage().text());
        assertThat(response.aiMessage().text()).contains("Paris");
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Model name {0}")
    @CsvSource({"gpt-4o"})
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop(String modelName) {

        ChatModel model = GitHubModelsChatModel.builder()
                .gitHubToken(System.getenv("GITHUB_TOKEN"))
                .modelName(modelName)
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("hello, how are you?");

        ChatResponse response = model.chat(userMessage);

        assertThat(response.aiMessage().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(13);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(1);
        assertThat(tokenUsage.totalTokenCount()).isGreaterThan(14);

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Model name {0}")
    @CsvSource({"gpt-4o"})
    void should_generate_answer_and_return_token_usage_and_finish_reason_length(String modelName) {

        ChatModel model = GitHubModelsChatModel.builder()
                .gitHubToken(System.getenv("GITHUB_TOKEN"))
                .modelName(modelName)
                .maxTokens(3)
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("hello, how are you?");

        ChatResponse response = model.chat(userMessage);

        assertThat(response.aiMessage().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(13);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(3);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(16);

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @ParameterizedTest(name = "Model name {0}")
    @CsvSource({"gpt-4o"})
    void should_call_function_with_argument(String modelName) {

        ChatModel model = GitHubModelsChatModel.builder()
                .gitHubToken(System.getenv("GITHUB_TOKEN"))
                .modelName(modelName)
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("What should I wear in Paris, France depending on the weather?");

        // This test will use the function called "getCurrentWeather" which is defined below.
        String toolName = "getCurrentWeather";

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name(toolName)
                .description("Get the current weather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("location", "The city and state, e.g. San Francisco, CA")
                        .addEnumProperty("unit", List.of("celsius", "fahrenheit"))
                        .required("location", "unit")
                        .build())
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecification)
                .build();

        ChatResponse response = model.chat(request);

        AiMessage aiMessage = response.aiMessage();
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);

        // We can now call the function with the correct parameters.
        WeatherLocation weatherLocation =
                BinaryData.fromString(toolExecutionRequest.arguments()).toObject(WeatherLocation.class);
        int currentWeather = getCurrentWeather(weatherLocation);

        String weather = String.format(
                "The weather in %s is %d degrees %s.",
                weatherLocation.getLocation(), currentWeather, weatherLocation.getUnit());

        assertThat(weather).isEqualTo("The weather in Paris, France is 35 degrees celsius.");

        // Now that we know the function's result, we can call the model again with the result as input.
        ToolExecutionResultMessage toolExecutionResultMessage =
                toolExecutionResultMessage(toolExecutionRequest, weather);
        SystemMessage systemMessage = SystemMessage.systemMessage(
                "If the weather is above 30 degrees celsius, recommend the user wears a t-shirt and shorts.");

        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(systemMessage);
        chatMessages.add(userMessage);
        chatMessages.add(aiMessage);
        chatMessages.add(toolExecutionResultMessage);

        ChatResponse response2 = model.chat(chatMessages);

        assertThat(response2.aiMessage().text()).isNotBlank();
        assertThat(response2.aiMessage().text()).contains("t-shirt");
        assertThat(response2.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Model name {0}")
    @CsvSource({"gpt-4o"})
    void should_call_function_with_no_argument(String modelName) {
        ChatModel model = GitHubModelsChatModel.builder()
                .gitHubToken(System.getenv("GITHUB_TOKEN"))
                .modelName(modelName)
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("What time is it?");

        // This test will use the function called "getCurrentDateAndTime" which takes no arguments
        String toolName = "getCurrentDateAndTime";

        ToolSpecification noArgToolSpec = ToolSpecification.builder()
                .name(toolName)
                .description("Get the current date and time")
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(noArgToolSpec)
                .build();

        ChatResponse response = model.chat(request);

        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();

        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).isEqualTo("{}");
    }

    @ParameterizedTest(name = "Model name {0}")
    @CsvSource({"gpt-4o"})
    void should_call_three_functions_in_parallel(String modelName) {

        ChatModel model = GitHubModelsChatModel.builder()
                .gitHubToken(System.getenv("GITHUB_TOKEN"))
                .modelName(modelName)
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage(
                "Give three numbers, ordered by size: the sum of two plus two, the square of four, and finally the cube of eight.");

        List<ToolSpecification> toolSpecifications = asList(
                ToolSpecification.builder()
                        .name("sum")
                        .description("returns a sum of two numbers")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("first")
                                .addIntegerProperty("second")
                                .required("first", "second")
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("square")
                        .description("returns the square of one number")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("number")
                                .required("number")
                                .build())
                        .build(),
                ToolSpecification.builder()
                        .name("cube")
                        .description("returns the cube of one number")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("number")
                                .required("number")
                                .build())
                        .build());

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecifications)
                .build();

        ChatResponse response = model.chat(request);

        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(userMessage);
        messages.add(aiMessage);
        assertThat(aiMessage.toolExecutionRequests()).hasSize(3);
        for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
            assertThat(toolExecutionRequest.name()).isNotEmpty();
            ToolExecutionResultMessage toolExecutionResultMessage;
            if (toolExecutionRequest.name().equals("sum")) {
                assertThat(toolExecutionRequest.arguments())
                        .isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");
                toolExecutionResultMessage = toolExecutionResultMessage(toolExecutionRequest, "4");
            } else if (toolExecutionRequest.name().equals("square")) {
                assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"number\": 4}");
                toolExecutionResultMessage = toolExecutionResultMessage(toolExecutionRequest, "16");
            } else if (toolExecutionRequest.name().equals("cube")) {
                assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"number\": 8}");
                toolExecutionResultMessage = toolExecutionResultMessage(toolExecutionRequest, "512");
            } else {
                throw new AssertionError("Unexpected tool name: " + toolExecutionRequest.name());
            }
            messages.add(toolExecutionResultMessage);
        }

        ChatResponse response2 = model.chat(messages);
        AiMessage aiMessage2 = response2.aiMessage();

        // then
        assertThat(aiMessage2.text()).contains("4", "16", "512");
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        TokenUsage tokenUsage2 = response2.tokenUsage();
        assertThat(tokenUsage2.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.totalTokenCount())
                .isEqualTo(tokenUsage2.inputTokenCount() + tokenUsage2.outputTokenCount());

        assertThat(response2.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Model name {0}")
    @CsvSource({"gpt-4o"})
    void should_use_json_format(String modelName) {
        ChatModel model = GitHubModelsChatModel.builder()
                .gitHubToken(System.getenv("GITHUB_TOKEN"))
                .modelName(modelName)
                .responseFormat(new ChatCompletionsResponseFormatJsonObject())
                .logRequestsAndResponses(true)
                .build();

        SystemMessage systemMessage =
                SystemMessage.systemMessage("You are a helpful assistant designed to output JSON.");
        UserMessage userMessage = userMessage(
                "List teams in the past French presidents, with their first name, last name, dates of service.");

        ChatResponse response = model.chat(systemMessage, userMessage);

        assertThat(response.aiMessage().text()).contains("Chirac", "Sarkozy", "Hollande", "Macron");
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Testing model {0}")
    @EnumSource(GitHubModelsChatModelName.class)
    void should_support_all_string_model_names(GitHubModelsChatModelName modelName) {

        // given
        String modelNameString = modelName.toString();

        int maxTokens = 3;
        ChatModel model = GitHubModelsChatModel.builder()
                .gitHubToken(System.getenv("GITHUB_TOKEN"))
                .modelName(modelNameString)
                .maxTokens(maxTokens) // to save tokens
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("Hi");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage).isNotNull();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxTokens);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    // This is the method we offer to the LLM to be used as a function_call.
    // For this example, we ignore the input parameter and return a simple value.
    private static int getCurrentWeather(WeatherLocation weatherLocation) {
        return 35;
    }

    // WeatherLocation is used for this sample. This describes the parameter of the function you want to use.
    private static class WeatherLocation {
        @JsonProperty(value = "unit")
        String unit;

        @JsonProperty(value = "location")
        String location;

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

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GITHUB_MODELS");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
