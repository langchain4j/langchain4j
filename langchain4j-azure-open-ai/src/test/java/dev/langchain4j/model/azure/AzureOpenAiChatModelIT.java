package dev.langchain4j.model.azure;

import com.azure.ai.openai.models.ChatCompletionsJsonResponseFormat;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.util.BinaryData;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

public class AzureOpenAiChatModelIT {

    @ParameterizedTest(name = "Deployment name {0} using {1}")
    @CsvSource({
            "gpt-4o,        gpt-4o"
    })
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop(String deploymentName, String gptVersion) {

        ChatLanguageModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .tokenizer(new AzureOpenAiTokenizer(gptVersion))
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("hello, how are you?");

        Response<AiMessage> response = model.generate(userMessage);

        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(13);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(1);
        assertThat(tokenUsage.totalTokenCount()).isGreaterThan(14);

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Deployment name {0} using {1}")
    @CsvSource({
            "gpt-4o,        gpt-4o"
    })
    void should_generate_answer_and_return_token_usage_and_finish_reason_length(String deploymentName, String gptVersion) {

        ChatLanguageModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI" +
                        "_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .tokenizer(new AzureOpenAiTokenizer(gptVersion))
                .maxTokens(3)
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("hello, how are you?");

        Response<AiMessage> response = model.generate(userMessage);

        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(13);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(3);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(16);

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @ParameterizedTest(name = "Deployment name {0} using {1}")
    @CsvSource({
            "gpt-4o,        gpt-4o"
    })
    void should_call_function_with_argument(String deploymentName, String gptVersion) {

        ChatLanguageModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .tokenizer(new AzureOpenAiTokenizer(gptVersion))
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("What should I wear in Paris, France depending on the weather?");

        // This test will use the function called "getCurrentWeather" which is defined below.
        String toolName = "getCurrentWeather";

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name(toolName)
                .description("Get the current weather")
                .parameters(getToolParameters())
                .build();

        Response<AiMessage> response = model.generate(Collections.singletonList(userMessage), toolSpecification);

        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(response.finishReason()).isEqualTo(STOP);

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

        Response<AiMessage> response2 = model.generate(chatMessages);

        assertThat(response2.content().text()).isNotBlank();
        assertThat(response2.content().text()).contains("t-shirt");
        assertThat(response2.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Deployment name {0} using {1}")
    @CsvSource({
            "gpt-4o,        gpt-4o"
    })
    void should_call_function_with_no_argument(String deploymentName, String gptVersion) {
        ChatLanguageModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .tokenizer(new AzureOpenAiTokenizer(gptVersion))
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("What time is it?");

        // This test will use the function called "getCurrentDateAndTime" which takes no arguments
        String toolName = "getCurrentDateAndTime";

        ToolSpecification noArgToolSpec = ToolSpecification.builder()
                .name(toolName)
                .description("Get the current date and time")
                .build();

        Response<AiMessage> response = model.generate(Collections.singletonList(userMessage), noArgToolSpec);

        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();

        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).isEqualTo("{}");
    }

    @ParameterizedTest(name = "Deployment name {0} using {1}")
    @CsvSource({
            "gpt-4o,        gpt-4o"
    })
    void should_call_three_functions_in_parallel(String deploymentName, String gptVersion) throws Exception {

        ChatLanguageModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .tokenizer(new AzureOpenAiTokenizer(gptVersion))
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("Give three numbers, ordered by size: the sum of two plus two, the square of four, and finally the cube of eight.");

        List<ToolSpecification> toolSpecifications = asList(
                ToolSpecification.builder()
                        .name("sum")
                        .description("returns a sum of two numbers")
                        .addParameter("first", INTEGER)
                        .addParameter("second", INTEGER)
                        .build(),
                ToolSpecification.builder()
                        .name("square")
                        .description("returns the square of one number")
                        .addParameter("number", INTEGER)
                        .build(),
                ToolSpecification.builder()
                        .name("cube")
                        .description("returns the cube of one number")
                        .addParameter("number", INTEGER)
                        .build()
        );

        Response<AiMessage> response = model.generate(Collections.singletonList(userMessage), toolSpecifications);

        AiMessage aiMessage = response.content();
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

        Response<AiMessage> response2 = model.generate(messages);
        AiMessage aiMessage2 = response2.content();

        // then
        assertThat(aiMessage2.text()).contains("4", "16", "512");
        assertThat(aiMessage2.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage2 = response2.tokenUsage();
        assertThat(tokenUsage2.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.totalTokenCount())
                .isEqualTo(tokenUsage2.inputTokenCount() + tokenUsage2.outputTokenCount());

        assertThat(response2.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Deployment name {0} using {1}")
    @CsvSource({
            "gpt-4o,        gpt-4o"
    })
    void should_use_json_format(String deploymentName, String gptVersion) {
        ChatLanguageModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .tokenizer(new AzureOpenAiTokenizer(gptVersion))
                .responseFormat(new ChatCompletionsJsonResponseFormat())
                .logRequestsAndResponses(true)
                .build();

        SystemMessage systemMessage = SystemMessage.systemMessage("You are a helpful assistant designed to output JSON.");
        UserMessage userMessage = userMessage("List teams in the past French presidents, with their first name, last name, dates of service.");

        Response<AiMessage> response = model.generate(systemMessage, userMessage);

        assertThat(response.content().text()).contains("Chirac", "Sarkozy", "Hollande", "Macron");
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Testing model {0}")
    @EnumSource(AzureOpenAiChatModelName.class)
    void should_support_all_string_model_names(AzureOpenAiChatModelName modelName) {

        // given
        String modelNameString = modelName.toString();

        ChatLanguageModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(modelNameString)
                .maxTokens(1)
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("Hi");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).isNotBlank();
    }

    @ParameterizedTest(name = "Deployment name {0}")
    @CsvSource({
            "gpt-4o"
    })
    void should_listen_request_and_response(String deploymentName) {

        // given
        AtomicReference<ChatModelRequest> requestReference = new AtomicReference<>();
        AtomicReference<ChatModelResponse> responseReference = new AtomicReference<>();

        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requestReference.set(requestContext.request());
                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                responseReference.set(responseContext.response());
                assertThat(responseContext.request()).isSameAs(requestReference.get());
                assertThat(responseContext.attributes().get("id")).isEqualTo("12345");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                fail("onError() must not be called");
            }
        };

        double temperature = 0.7;
        double topP = 1.0;
        int maxTokens = 7;

        AzureOpenAiChatModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .topP(topP)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .logRequestsAndResponses(true)
                .listeners(singletonList(listener))
                .build();

        UserMessage userMessage = UserMessage.from("hello");

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("add")
                .addParameter("a", INTEGER)
                .addParameter("b", INTEGER)
                .build();

        // when
        AiMessage aiMessage = model.generate(singletonList(userMessage), singletonList(toolSpecification)).content();

        // then
        ChatModelRequest request = requestReference.get();
        assertThat(request.model()).isEqualTo(deploymentName);
        assertThat(request.temperature()).isEqualTo(temperature);
        assertThat(request.topP()).isEqualTo(topP);
        assertThat(request.maxTokens()).isEqualTo(maxTokens);
        assertThat(request.messages()).containsExactly(userMessage);
        assertThat(request.toolSpecifications()).containsExactly(toolSpecification);

        ChatModelResponse response = responseReference.get();
        assertThat(response.id()).isNotBlank();
        assertThat(response.model()).isNotBlank();
        assertThat(response.tokenUsage().inputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount()).isGreaterThan(0);
        assertThat(response.finishReason()).isNotNull();
        assertThat(response.aiMessage()).isEqualTo(aiMessage);
    }

    @Test
    void should_listen_error() {

        // given
        String wrongApiKey = "banana";

        AtomicReference<ChatModelRequest> requestReference = new AtomicReference<>();
        AtomicReference<Throwable> errorReference = new AtomicReference<>();

        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requestReference.set(requestContext.request());
                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                fail("onResponse() must not be called");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                errorReference.set(errorContext.error());
                assertThat(errorContext.request()).isSameAs(requestReference.get());
                assertThat(errorContext.partialResponse()).isNull();
                assertThat(errorContext.attributes().get("id")).isEqualTo("12345");
            }
        };

        AzureOpenAiChatModel model = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(wrongApiKey)
                .maxRetries(0)
                .logRequestsAndResponses(true)
                .listeners(singletonList(listener))
                .build();

        String userMessage = "this message will fail";
        model.generate(userMessage);

        // then
        Throwable throwable = errorReference.get();
        assertThat(throwable).isInstanceOf(HttpResponseException.class);
        assertThat(throwable).hasMessageContaining("Access denied due to invalid subscription key or wrong API endpoint");
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
}
