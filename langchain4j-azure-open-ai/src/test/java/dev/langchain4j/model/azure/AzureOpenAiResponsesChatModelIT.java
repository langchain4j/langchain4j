package dev.langchain4j.model.azure;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.JsonSchemaElementUtils.jsonSchemaElementFrom;
import static dev.langchain4j.model.azure.AzureModelBuilders.getAzureOpenaiEndpoint;
import static dev.langchain4j.model.azure.AzureModelBuilders.getAzureOpenaiKey;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.ai.openai.models.ReasoningEffortValue;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiResponsesChatModelIT {

    private static final String DEPLOYMENT_NAME = "gpt-6-luna";

    @ParameterizedTest(name = "Deployment name {0}")
    @CsvSource({"gpt-6-luna"})
    void should_use_json_format(String deploymentName) {
        ChatModel model = AzureModelBuilders.responsesChatModelBuilder()
                .deploymentName(deploymentName)
                .responseFormat(ResponseFormat.JSON)
                .temperature(1.0)
                .logRequestsAndResponses(true)
                .build();

        String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler.";

        String expectedJson = "{\"name\": \"Klaus\", \"surname\": \"Heisler\"}";

        String answer = model.chat(userMessage);

        assertThat(answer).isEqualToIgnoringWhitespace(expectedJson);
    }

    @Disabled("requires deployment of all models")
    @ParameterizedTest(name = "Testing model {0}")
    @ValueSource(strings = {"gpt-4o", "gpt-6-luna"})
    void should_support_all_string_model_names(AzureOpenAiChatModelName modelName) {

        // given
        String modelNameString = modelName.toString();

        ChatModel model = AzureModelBuilders.responsesChatModelBuilder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(modelNameString)
                .maxOutputTokens(16)
                .logRequestsAndResponses(true)
                .build();

        UserMessage userMessage = userMessage("Hi");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes({@JsonSubTypes.Type(Circle.class), @JsonSubTypes.Type(Rectangle.class)})
    interface Shape {}

    record Circle(double radius) implements Shape {}

    record Rectangle(double width, double height) implements Shape {}

    record Shapes(List<Shape> shapes) {}

    // TODO move to common tests
    @Test
    void should_generate_valid_json_with_anyof() throws JsonProcessingException {

        // given
        JsonSchemaElement circleSchema = jsonSchemaElementFrom(Circle.class);
        JsonSchemaElement rectangleSchema = jsonSchemaElementFrom(Rectangle.class);

        JsonSchema jsonSchema = JsonSchema.builder()
                .name("Shapes")
                .rootElement(JsonObjectSchema.builder()
                        .addProperty(
                                "shapes",
                                JsonArraySchema.builder()
                                        .items(JsonAnyOfSchema.builder()
                                                .anyOf(circleSchema, rectangleSchema)
                                                .build())
                                        .build())
                        .required(List.of("shapes"))
                        .build())
                .build();

        ResponseFormat responseFormat =
                ResponseFormat.builder().type(JSON).jsonSchema(jsonSchema).build();

        UserMessage userMessage = UserMessage.from("""
                        Extract information from the following text:
                        1. A circle with a radius of 5
                        2. A rectangle with a width of 10 and a height of 20
                        """);

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .responseFormat(responseFormat)
                .build();

        ChatModel model = AzureModelBuilders.responsesChatModelBuilder()
                .deploymentName(DEPLOYMENT_NAME)
                .strictJsonSchema(true)
                .temperature(1.0)
                .logRequestsAndResponses(true)
                .build();

        // when
        ChatResponse chatResponse = model.chat(request);

        // then
        Shapes shapes = new ObjectMapper().readValue(chatResponse.aiMessage().text(), Shapes.class);
        assertThat(shapes).isNotNull();
        assertThat(shapes.shapes()).isNotNull().containsExactlyInAnyOrder(new Circle(5), new Rectangle(10, 20));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    // todo: should be mokksy test
    void should_handle_timeout(int millis) {

        // given
        Duration timeout = Duration.ofMillis(millis);

        ChatModel model = AzureModelBuilders.responsesChatModelBuilder()
                .deploymentName(DEPLOYMENT_NAME)
                .logRequestsAndResponses(true)
                .maxRetries(0)
                .timeout(timeout)
                .build();

        // when
        assertThatThrownBy(() -> model.chat("hi"))
                .isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }

    @Test
    void should_work_with_o_models() {

        // given
        ChatModel model = AzureModelBuilders.responsesChatModelBuilder()
                .endpoint(getAzureOpenaiEndpoint())
                .apiKey(getAzureOpenaiKey())
                .deploymentName(DEPLOYMENT_NAME)
                .temperature(1.0)
                .logRequestsAndResponses(true)
                .build();

        // when
        String answer = model.chat("What is the capital of Germany?");

        // then
        assertThat(answer).contains("Berlin");
    }

    @Test
    void should_support_maxCompletionTokens() {

        // given
        int maxCompletionTokens = 200;

        ChatModel model = AzureModelBuilders.responsesChatModelBuilder()
                .endpoint(getAzureOpenaiEndpoint())
                .apiKey(getAzureOpenaiKey())
                .deploymentName(DEPLOYMENT_NAME)
                .maxOutputTokens(maxCompletionTokens)
                .temperature(1.0)
                .logRequestsAndResponses(true)
                .build();

        // when
        ChatResponse response = model.chat(userMessage("Tell me a long story"));

        // then
        // the answer text is not asserted: a reasoning model may use the whole budget for reasoning and return no text
        assertThat(response.finishReason()).isEqualTo(LENGTH);
        assertThat(response.tokenUsage().outputTokenCount()).isLessThanOrEqualTo(maxCompletionTokens);
    }

    @Test
    void should_support_ReasoningEffort() {

        // given
        ReasoningEffortValue reasoningEffort = ReasoningEffortValue.LOW;

        ChatModel model = AzureModelBuilders.responsesChatModelBuilder()
                .endpoint(getAzureOpenaiEndpoint())
                .apiKey(getAzureOpenaiKey())
                .deploymentName(DEPLOYMENT_NAME)
                .reasoningEffort(reasoningEffort.toString())
                .temperature(1.0)
                .logRequestsAndResponses(true)
                .build();

        // when
        String answer = model.chat("What is the capital of Germany?");

        // then
        assertThat(answer).contains("Berlin");
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_AZURE_OPENAI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
