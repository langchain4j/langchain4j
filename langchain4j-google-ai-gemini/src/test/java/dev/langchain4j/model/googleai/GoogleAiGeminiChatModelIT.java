package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.JsonSchemaElementUtils.jsonSchemaElementFrom;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.googleai.FinishReasonMapper.fromGFinishReasonToFinishReason;
import static dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate.GeminiFinishReason.SAFETY;
import static dev.langchain4j.model.googleai.GeminiHarmBlockThreshold.BLOCK_LOW_AND_ABOVE;
import static dev.langchain4j.model.googleai.GeminiHarmCategory.HARM_CATEGORY_HARASSMENT;
import static dev.langchain4j.model.googleai.GeminiHarmCategory.HARM_CATEGORY_HATE_SPEECH;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.V;
import dev.langchain4j.service.output.JsonSchemas;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiChatModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    private static final String CAT_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";

    @Test
    void should_answer_in_json() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .responseFormat(ResponseFormat.JSON)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        ChatResponse response = gemini.chat(UserMessage.from("What is the firstname of the John Doe?\n"
                + "Reply in JSON following with the following format: {\"firstname\": string}"));

        // then
        String jsonText = response.aiMessage().text();
        assertThat(jsonText).contains("\"firstname\"").contains("\"John\"");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }

    @Test
    void should_support_multiturn_chatting() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .build();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from("Hello, my name is Guillaume"));
        messages.add(AiMessage.from("Hi, how can I assist you today?"));
        messages.add(UserMessage.from("What is my name? Reply with just my name."));

        // when
        ChatResponse response = gemini.chat(messages);

        // then
        assertThat(response.aiMessage().text()).contains("Guillaume");
    }

    // Not supported yet, needs to implement the File Upload API first
    // @Test
    void should_support_sending_images_as_uri() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .build();

        // when
        ChatResponse response = gemini.chat(UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL), // TODO use file upload API
                TextContent.from("Describe this image in a single word")));

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_support_audio_file() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .build();

        // TODO use local file
        byte[] bytes = readBytes("https://storage.googleapis.com/cloud-samples-data/generative-ai/audio/pixel.mp3");
        String base64Data = new String(Base64.getEncoder().encode(bytes));

        UserMessage userMessage = UserMessage.from(
                AudioContent.from(base64Data, "audio/mp3"), TextContent.from("Give a summary of the audio"));

        // when
        ChatResponse response = gemini.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Pixel");
    }

    @Test
    void should_support_video_file() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .build();

        URI videoUri = Paths.get("src/test/resources/example-video.mp4").toUri();
        String base64Data = new String(Base64.getEncoder().encode(readBytes(videoUri.toString())));

        UserMessage userMessage = UserMessage.from(
                VideoContent.from(base64Data, "video/mp4"), TextContent.from("What do you see on this video?"));

        // when
        ChatResponse response = gemini.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("example");
    }

    void should_execute_python_code() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .logRequests(true)
                .logResponses(true)
                .allowCodeExecution(true)
                .includeCodeExecutionOutput(true)
                .build();

        // when
        ChatResponse response = gemini.chat(singletonList(UserMessage.from(
                "Calculate `fibonacci(13)`. " + "Write code in Python and execute it to get the result.")));

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("233");
    }

    @Disabled("TODO fix")
    void should_support_safety_settings() {
        // given
        Map<GeminiHarmCategory, GeminiHarmBlockThreshold> mapSafetySettings = new HashMap<>();
        mapSafetySettings.put(HARM_CATEGORY_HATE_SPEECH, BLOCK_LOW_AND_ABOVE);
        mapSafetySettings.put(HARM_CATEGORY_HARASSMENT, BLOCK_LOW_AND_ABOVE);

        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .logRequests(true)
                .logResponses(true)
                .safetySettings(mapSafetySettings)
                .build();

        // when
        ChatResponse response = gemini.chat(ChatRequest.builder()
                .messages(UserMessage.from("You're a dumb f*cking idiot bastard!"))
                .build());

        // then
        assertThat(response.finishReason()).isEqualTo(fromGFinishReasonToFinishReason(SAFETY));
    }

    @Test
    void should_comply_to_response_schema() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .logRequests(true)
                .logResponses(true)
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .rootElement(JsonObjectSchema.builder()
                                        .addStringProperty("name")
                                        .addProperty(
                                                "address",
                                                JsonObjectSchema.builder()
                                                        .addStringProperty("city")
                                                        .required("city")
                                                        .build())
                                        .required("name", "address")
                                        .additionalProperties(false)
                                        .build())
                                .build())
                        .build())
                .build();

        // when
        ChatResponse response = gemini.chat(ChatRequest.builder()
                .messages(
                        SystemMessage.from("Your role is to extract information related to a person,"
                                + "like their name, their address, the city the live in."),
                        UserMessage.from("In the town of Liverpool, lived Tommy Skybridge, a young little boy."))
                .build());

        // then
        assertThat(response.aiMessage().text().trim())
                .isEqualToIgnoringWhitespace("{\"name\": \"Tommy Skybridge\", \"address\": {\"city\": \"Liverpool\"}}");
    }

    @Test
    void should_handle_enum_type() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")
                .logRequests(true)
                .logResponses(true)
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .rootElement(JsonObjectSchema.builder()
                                        .addProperty(
                                                "sentiment",
                                                JsonEnumSchema.builder()
                                                        .enumValues("POSITIVE", "NEGATIVE")
                                                        .build())
                                        .required("sentiment")
                                        .additionalProperties(false)
                                        .build())
                                .build())
                        .build())
                .build();

        // when
        ChatResponse response = gemini.chat(ChatRequest.builder()
                .messages(
                        SystemMessage.from("Your role is to analyze the sentiment of the text you receive."),
                        UserMessage.from("This is super exciting news, congratulations!"))
                .build());

        // then
        assertThat(response.aiMessage().text()).isEqualToIgnoringWhitespace("{\"sentiment\":\"POSITIVE\"}");
    }

    @Test
    void should_handle_enum_output_mode() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .logRequests(true)
                .logResponses(true)
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .rootElement(JsonEnumSchema.builder()
                                        .enumValues("POSITIVE", "NEUTRAL", "NEGATIVE")
                                        .build())
                                .build())
                        .build())
                .build();

        // when
        ChatResponse response = gemini.chat(ChatRequest.builder()
                .messages(
                        SystemMessage.from("Your role is to analyze the sentiment of the text you receive."),
                        UserMessage.from("This is super exciting news, congratulations!"))
                .build());

        // then
        assertThat(response.aiMessage().text().trim()).isEqualTo("POSITIVE");
    }

    @Test
    void should_allow_array_as_response_schema() throws JsonProcessingException {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .logRequests(true)
                .logResponses(true)
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .rootElement(JsonArraySchema.builder()
                                        .items(new JsonIntegerSchema())
                                        .build())
                                .build())
                        .build())
                .build();

        // when
        ChatResponse response = gemini.chat(ChatRequest.builder()
                .messages(
                        SystemMessage.from("Your role is to return a list of 6-faces dice rolls"),
                        UserMessage.from("Give me 3 dice rolls, all at once"))
                .build());

        // then
        Integer[] diceRolls = new ObjectMapper().readValue(response.aiMessage().text(), Integer[].class);
        assertThat(diceRolls.length).isEqualTo(3);
    }

    private static class Color {
        private String name;
        private int red;
        private int green;
        private int blue;
        private boolean muted;

        @JsonCreator
        public Color(
                @JsonProperty("name") String name,
                @JsonProperty("red") int red,
                @JsonProperty("green") int green,
                @JsonProperty("blue") int blue,
                @JsonProperty("muted") boolean muted) {
            this.name = name;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.muted = muted;
        }
    }

    @Test
    void should_deserialize_to_POJO() throws JsonProcessingException {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")
                .logRequests(true)
                .logResponses(true)
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchemas.jsonSchemaFrom(Color.class).get())
                        .build())
                .build();

        // when
        ChatResponse response = gemini.chat(ChatRequest.builder()
                .messages(
                        SystemMessage.from("Your role is to extract information from the description of a color"),
                        UserMessage.from(
                                "'Cobalt blue' is a blend of a lot of blue, a bit of green, and almost no red."))
                .build());

        Color color = new ObjectMapper().readValue(response.aiMessage().text(), Color.class);

        // then
        assertThat(color.name.toLowerCase()).containsAnyOf("cobalt", "blue");
        assertThat(color.muted).isFalse();
        assertThat(color.red).isLessThanOrEqualTo(color.green);
        assertThat(color.green).isLessThanOrEqualTo(color.blue);
    }

    @Test
    void should_support_tool_config() {
        // given
        GoogleAiGeminiChatModel model1 = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .logRequests(true)
                .logResponses(true)
                .build();

        List<ToolSpecification> listOfTools = Arrays.asList(
                ToolSpecification.builder().name("toolOne").build(),
                ToolSpecification.builder().name("toolTwo").build());

        ChatRequest request1 = ChatRequest.builder()
                .messages(UserMessage.from("Call toolOne"))
                .toolSpecifications(listOfTools)
                .build();

        // when
        ChatResponse response1 = model1.chat(request1);

        // then
        assertThat(response1.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response1.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("toolOne");

        // given
        GoogleAiGeminiChatModel model2 = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .logRequests(true)
                .logResponses(true)
                .toolConfig(GeminiMode.ANY, "toolTwo")
                .build();

        // when
        ChatResponse response2 = model2.chat(request1);

        // then
        assertThat(response2.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response2.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("toolTwo");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void should_handle_timeout(int millis) {

        // given
        Duration timeout = Duration.ofMillis(millis);

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .logRequests(true)
                .logResponses(true)
                .maxRetries(0)
                .timeout(timeout)
                .build();

        // when
        assertThatThrownBy(() -> model.chat("hi"))
                .isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }

    @Test
    void should_generate_image() {

        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-image")
                .build();

        // when
        ChatResponse response = gemini.chat(ChatRequest.builder()
                .messages(
                        UserMessage.from(
                                "A high-resolution, studio-lit product photograph of a minimalist ceramic coffee mug in matte black"))
                .build());

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage).isNotNull();

        List<dev.langchain4j.data.image.Image> generatedImages = aiMessage.images();
        if (!generatedImages.isEmpty()) {
            for (dev.langchain4j.data.image.Image image : generatedImages) {
                assertThat(image.base64Data()).isNotEmpty();
                assertThat(image.mimeType()).startsWith("image/");
            }
        }

        assertThat(aiMessage.text() != null || !aiMessage.images().isEmpty()).isTrue();
    }

    public interface ImageGenerator {

        @dev.langchain4j.service.UserMessage("A high-resolution, studio-lit product photograph of {{requiredImage}}")
        ImageContent generateImageOf(@V("requiredImage") String requiredImage);
    }

    @Test
    void ai_service_should_generate_image() {
        ImageGenerator imageGenerator = AiServices.builder(ImageGenerator.class)
                .chatModel(GoogleAiGeminiChatModel.builder()
                        .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                        .modelName("gemini-2.5-flash-image")
                        .build())
                .build();

        ImageContent image = imageGenerator.generateImageOf("a minimalist ceramic coffee mug in matte black");
        assertThat(image).isNotNull();
        assertThat(image.image().base64Data()).isNotEmpty();
        assertThat(image.image().mimeType()).startsWith("image/");
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
        ChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")
                .logRequestsAndResponses(true)
                .build();

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

        UserMessage userMessage = UserMessage.from(
                """
                        Extract information from the following text:
                        1. A circle with a radius of 5
                        2. A rectangle with a width of 10 and a height of 20
                        """);

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .responseFormat(responseFormat)
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        Shapes shapes = new ObjectMapper().readValue(chatResponse.aiMessage().text(), Shapes.class);
        assertThat(shapes).isNotNull();
        assertThat(shapes.shapes()).isNotNull().containsExactlyInAnyOrder(new Circle(5), new Rectangle(10, 20));
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }

    @Test
    void should_support_raw_json_schema() throws JsonProcessingException {
        // given
        String rawSchema =
                """
        {
          "type": "object",
          "properties": {
            "name": {
              "type": "string"
            },
            "birthDate": {
              "type": "string",
              "format": "date"
            },
            "preferredContactTime": {
              "type": "string",
              "format": "time"
              },
            "height": {
              "type": "number",
              "minimum": 1.83,
              "maximum": 1.88
            },
            "role": {
              "type": "string",
              "enum": ["developer", "maintainer", "researcher"]
            },
            "isAvailable": { "type": "boolean" },
            "tags": {
              "type": "array",
              "items": {
                "type": "string"
              },
              "minItems": 1,
              "maxItems": 5
            },
            "address": {
              "type": "object",
              "properties": {
                "city": { "type": "string" },
                "streetName": { "type": "string" },
                "streetNumber": { "type": "string" }
              },
              "required": ["city", "streetName", "streetNumber"],
              "additionalProperties": true
            }
          },
          "required": ["name", "birthDate", "height", "role", "tags", "address"]
        }
        """;

        JsonRawSchema jsonRawSchema = JsonRawSchema.builder().schema(rawSchema).build();
        JsonSchema jsonSchema = JsonSchema.builder().rootElement(jsonRawSchema).build();

        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(jsonSchema)
                .build();

        // Build the Gemini model
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .logRequests(true)
                .logResponses(true)
                .responseFormat(responseFormat)
                .build();

        // when
        UserMessage userMessage = UserMessage.from(
                """
                   Tell me about a software engineer named Sherlock Holmes,
                   who was born on November 28 1990 and sees the world over six feet from the ground.
                   He is an open-source contributor, an active volunteer and lives in London at 221B Baker Street.
                   He plays the violin and he likes to conduct various physics and chemistry experiments.
                   He accepts clients or prefers to be contacted at 09:00am.
                   """);

        ChatResponse response =
                gemini.chat(ChatRequest.builder().messages(userMessage).build());

        // then
        String jsonText = response.aiMessage().text().trim();
        Map<String, Object> result = new ObjectMapper().readValue(jsonText, Map.class);

        assertThat(result)
                .containsKeys(
                        "name",
                        "birthDate",
                        "height",
                        "role",
                        "tags",
                        "address",
                        "preferredContactTime",
                        "isAvailable");
        assertThat(result.get("name")).isEqualTo("Sherlock Holmes");
        assertThat(result.get("birthDate")).isEqualTo("1990-11-28");
        assertThat(result.get("role")).isIn("developer", "contributor", "volunteer");
        assertThat(result.get("isAvailable")).isInstanceOf(Boolean.class);

        assertThat(result.get("height")).isInstanceOf(Number.class);
        float height = ((Number) result.get("height")).floatValue();
        assertThat(height).isBetween(1.83f, 1.88f);

        assertDoesNotThrow(() -> OffsetTime.parse((String) result.get("preferredContactTime")));

        assertThat(result.get("tags")).isInstanceOf(List.class);
        List<String> tags = (List<String>) result.get("tags");
        assertThat(tags.size()).isBetween(1, 5);

        Map<String, Object> address = (Map<String, Object>) result.get("address");
        assertThat(address.keySet()).contains("city", "streetName", "streetNumber");
    }

    @Test
    void should_process_image_with_media_resolution_low() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .mediaResolution(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_LOW)
                .logRequests(true)
                .logResponses(true)
                .build();

        byte[] imageBytes = readBytes(CAT_IMAGE_URL);
        String base64Data = Base64.getEncoder().encodeToString(imageBytes);

        UserMessage userMessage = UserMessage.from(
                ImageContent.from(base64Data, "image/png"),
                TextContent.from("What animal is shown in this image? Reply with just the animal name."));

        // when
        ChatResponse response = gemini.chat(userMessage);

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotNull();
        assertThat(response.aiMessage().text().toLowerCase()).containsAnyOf("cat", "kitten", "feline");
    }

    @Test
    void should_process_image_with_media_resolution_high() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .mediaResolution(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_HIGH)
                .logRequests(true)
                .logResponses(true)
                .build();

        byte[] imageBytes = readBytes(CAT_IMAGE_URL);
        String base64Data = Base64.getEncoder().encodeToString(imageBytes);

        UserMessage userMessage = UserMessage.from(
                ImageContent.from(base64Data, "image/png"),
                TextContent.from("What animal is shown in this image? Reply with just the animal name."));

        // when
        ChatResponse response = gemini.chat(userMessage);

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotNull();
        assertThat(response.aiMessage().text().toLowerCase()).containsAnyOf("cat", "kitten", "feline");
    }

    @Test
    void should_process_image_with_media_resolution_medium() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .mediaResolution(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_MEDIUM)
                .logRequests(true)
                .logResponses(true)
                .build();

        byte[] imageBytes = readBytes(CAT_IMAGE_URL);
        String base64Data = Base64.getEncoder().encodeToString(imageBytes);

        UserMessage userMessage = UserMessage.from(
                ImageContent.from(base64Data, "image/png"),
                TextContent.from("What animal is shown in this image? Reply with just the animal name."));

        // when
        ChatResponse response = gemini.chat(userMessage);

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotNull();
        assertThat(response.aiMessage().text().toLowerCase()).containsAnyOf("cat", "kitten", "feline");
    }

    @Test
    void should_process_image_with_media_resolution_unspecified() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .mediaResolution(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_UNSPECIFIED)
                .logRequests(true)
                .logResponses(true)
                .build();

        byte[] imageBytes = readBytes(CAT_IMAGE_URL);
        String base64Data = Base64.getEncoder().encodeToString(imageBytes);

        UserMessage userMessage = UserMessage.from(
                ImageContent.from(base64Data, "image/png"),
                TextContent.from("What animal is shown in this image? Reply with just the animal name."));

        // when
        ChatResponse response = gemini.chat(userMessage);

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotNull();
        assertThat(response.aiMessage().text().toLowerCase()).containsAnyOf("cat", "kitten", "feline");
    }

    @Test
    void should_process_video_with_media_resolution_low() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .mediaResolution(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_LOW)
                .logRequests(true)
                .logResponses(true)
                .build();

        URI videoUri = Paths.get("src/test/resources/example-video.mp4").toUri();
        String base64Data = new String(Base64.getEncoder().encode(readBytes(videoUri.toString())));

        UserMessage userMessage = UserMessage.from(
                VideoContent.from(base64Data, "video/mp4"),
                TextContent.from("What do you see in this video? Reply with a short description."));

        // when
        ChatResponse response = gemini.chat(userMessage);

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotNull();
        assertThat(response.aiMessage().text()).containsIgnoringCase("example");
    }
}
