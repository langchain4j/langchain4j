package dev.langchain4j.model.azure;

import com.azure.core.util.BinaryData;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.internal.JsonSchemaElementUtils.jsonSchemaElementFrom;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureOpenAiChatModelIT {

    @ParameterizedTest(name = "Deployment name {0}")
    @CsvSource({
            "gpt-4o"
    })
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop(String deploymentName) {

        ChatModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
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

    @ParameterizedTest(name = "Deployment name {0}")
    @CsvSource({
            "gpt-4o"
    })
    void should_generate_answer_and_return_token_usage_and_finish_reason_length(String deploymentName) {

        ChatModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI" +
                        "_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
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

    @ParameterizedTest(name = "Deployment name {0}")
    @CsvSource({
            "gpt-4o"
    })
    void should_execute_tool_forcefully_then_answer(String deploymentName) {

        ChatModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
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
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecification)
                        .toolChoice(REQUIRED)
                        .build())
                .build();

        ChatResponse response = model.chat(request);

        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);

        // We can now call the function with the correct parameters.
        WeatherLocation weatherLocation = BinaryData.fromString(toolExecutionRequest.arguments()).toObject(WeatherLocation.class);
        int currentWeather = getCurrentWeather(weatherLocation);

        String weather = String.format("The weather in %s is %d degrees %s.",
                weatherLocation.getLocation(), currentWeather, weatherLocation.getUnit());

        assertThat(weather).isEqualTo("The weather in Paris, France is 35 degrees celsius.");

        // Now that we know the function's result, we can call the model again with the result as input.
        ToolExecutionResultMessage toolExecutionResultMessage = toolExecutionResultMessage(toolExecutionRequest, weather);
        SystemMessage systemMessage = SystemMessage.systemMessage("If the weather is above 30 degrees celsius, recommend the user wears a t-shirt and shorts.");

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

    @ParameterizedTest(name = "Deployment name {0}")
    @CsvSource({
            "gpt-4o"
    })
    void should_call_function_with_no_argument(String deploymentName) {
        ChatModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
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
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).isEqualTo("{}");
    }

    @ParameterizedTest(name = "Deployment name {0}")
    @CsvSource({
            "gpt-4o"
    })
    void should_call_three_functions_in_parallel(String deploymentName) {

        ChatModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("Give three numbers, ordered by size: the sum of two plus two, the square of four, and finally the cube of eight.");

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
                        .build()
        );

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
                assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");
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

    @ParameterizedTest(name = "Deployment name {0}")
    @CsvSource({
            "gpt-4o"
    })
    void should_use_json_format(String deploymentName) {
        ChatModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .responseFormat(ResponseFormat.JSON)
                .logRequestsAndResponses(true)
                .build();

        SystemMessage systemMessage = SystemMessage.systemMessage("You are a helpful assistant designed to output JSON.");
        UserMessage userMessage = userMessage("List teams in the past French presidents, with their first name, last name, dates of service.");

        ChatResponse response = model.chat(systemMessage, userMessage);

        assertThat(response.aiMessage().text()).contains("Chirac", "Sarkozy", "Hollande", "Macron");
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Disabled("requires deployment of all models")
    @ParameterizedTest(name = "Testing model {0}")
    @EnumSource(value = AzureOpenAiChatModelName.class, mode = EnumSource.Mode.EXCLUDE, names = {
            "GPT_3_5_TURBO_0301",
            "GPT_3_5_TURBO_16K",
            "GPT_4_0125_PREVIEW",
            "GPT_4_1106_PREVIEW",
            "GPT_4_TURBO",
            "GPT_4_32K"})
    void should_support_all_string_model_names(AzureOpenAiChatModelName modelName) {

        // given
        String modelNameString = modelName.toString();

        ChatModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(modelNameString)
                .maxTokens(1)
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("Hi");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    // This is the method we offer to OpenAI to be used as a function_call.
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


    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes({
            @JsonSubTypes.Type(Circle.class),
            @JsonSubTypes.Type(Rectangle.class)
    })
    interface Shape {

    }

    record Circle(double radius) implements Shape {

    }

    record Rectangle(double width,
                     double height) implements Shape {

    }

    record Shapes(List<Shape> shapes) {
    }

    // TODO move to common tests
    @Test
    void should_generate_valid_json_with_anyof() throws JsonProcessingException {

        // given
        JsonSchemaElement circleSchema = jsonSchemaElementFrom(Circle.class);
        JsonSchemaElement rectangleSchema = jsonSchemaElementFrom(Rectangle.class);

        JsonSchema jsonSchema = JsonSchema.builder()
                .name("Shapes")
                .rootElement(JsonObjectSchema.builder()
                        .addProperty("shapes", JsonArraySchema.builder()
                                .items(JsonAnyOfSchema.builder()
                                        .anyOf(circleSchema, rectangleSchema)
                                        .build())
                                .build())
                        .required(List.of("shapes"))
                        .build())
                .build();

        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(JSON)
                .jsonSchema(jsonSchema)
                .build();

        UserMessage userMessage = UserMessage.from("""
                Extract information from the following text:
                1. A circle with a radius of 5
                2. A rectangle with a width of 10 and a height of 20
                """);

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .responseFormat(responseFormat)
                .build();

        ChatModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName("gpt-4o-mini")
                .strictJsonSchema(true)
                .logRequestsAndResponses(true)
                .build();

        // when
        ChatResponse chatResponse = model.chat(request);

        // then
        Shapes shapes = new ObjectMapper().readValue(chatResponse.aiMessage().text(), Shapes.class);
        assertThat(shapes).isNotNull();
        assertThat(shapes.shapes())
                .isNotNull()
                .containsExactlyInAnyOrder(
                        new Circle(5),
                        new Rectangle(10, 20)
                );
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void should_handle_timeout(int millis) {

        // given
        Duration timeout = Duration.ofMillis(millis);

        ChatModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName("gpt-4o")
                .logRequestsAndResponses(true)
                .maxRetries(0)
                .timeout(timeout)
                .build();

        // when
        assertThatThrownBy(() -> model.chat("hi"))
                .isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_AZURE_OPENAI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
