package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.googleai.FinishReasonMapper.fromGFinishReasonToFinishReason;
import static dev.langchain4j.model.googleai.GeminiFinishReason.*;
import static dev.langchain4j.model.googleai.GeminiHarmBlockThreshold.BLOCK_LOW_AND_ABOVE;
import static dev.langchain4j.model.googleai.GeminiHarmCategory.HARM_CATEGORY_HARASSMENT;
import static dev.langchain4j.model.googleai.GeminiHarmCategory.HARM_CATEGORY_HATE_SPEECH;
import static dev.langchain4j.model.googleai.GeneratedImageHelper.hasGeneratedImages;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.output.JsonSchemas;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.RetryingTest;

class GoogleAiGeminiChatModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    private static final String CAT_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";

    @Test
    void should_answer_in_json() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-pro")
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
                .modelName("gemini-1.5-flash")
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
                .modelName("gemini-1.5-flash")
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
                .modelName("gemini-2.5-flash")
                .build();

        // TODO use local file
        byte[] bytes = readBytes("https://storage.googleapis.com/cloud-samples-data/generative-ai/audio/pixel.mp3");
        String base64Data = new String(Base64.getEncoder().encode(bytes));

        UserMessage userMessage = UserMessage.from(
                AudioContent.from(base64Data, "audio/mp3"),
                TextContent.from("Give a summary of the audio"));

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

        // when
        URI videoUri = Paths.get("src/test/resources/example-video.mp4").toUri();
        String base64Data = new String(Base64.getEncoder().encode(readBytes(videoUri.toString())));

        ChatResponse response = gemini.chat(UserMessage.from(
                VideoContent.from(base64Data, "video/mp4"), TextContent.from("Give a summary of the video")));

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("example");
    }

    @Test
    void should_execute_python_code() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
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

    @Test
    void should_support_JSON_array_in_tools() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .logRequests(true)
                .logResponses(true)
                .build();

        List<ChatMessage> allMessages = new ArrayList<>();
        allMessages.add(UserMessage.from("Return a JSON list containing the first 10 fibonacci numbers."));

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("getFirstNFibonacciNumbers")
                .description("Get the first n fibonacci numbers")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("n")
                        .required("n")
                        .build())
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(allMessages)
                .toolSpecifications(toolSpecification)
                .build();

        // when
        ChatResponse response = gemini.chat(request);

        // then
        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("getFirstNFibonacciNumbers");
        assertThat(response.aiMessage().toolExecutionRequests().get(0).arguments())
                .contains("\"n\":10");

        allMessages.add(response.aiMessage());

        // when
        ToolExecutionResultMessage forecastResult =
                ToolExecutionResultMessage.from(null, "getFirstNFibonacciNumbers", "[0, 1, 1, 2, 3, 5, 8, 13, 21, 34]");
        allMessages.add(forecastResult);

        // then
        response = gemini.chat(allMessages);

        // then
        assertThat(response.aiMessage().text()).contains("[0, 1, 1, 2, 3, 5, 8, 13, 21, 34]");
    }

    @Disabled("TODO fix")
    @RetryingTest(5)
    void should_support_safety_settings() {
        // given
        Map<GeminiHarmCategory, GeminiHarmBlockThreshold> mapSafetySettings = new HashMap<>();
        mapSafetySettings.put(HARM_CATEGORY_HATE_SPEECH, BLOCK_LOW_AND_ABOVE);
        mapSafetySettings.put(HARM_CATEGORY_HARASSMENT, BLOCK_LOW_AND_ABOVE);

        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
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
                .modelName("gemini-1.5-flash")
                .logRequests(true)
                .logResponses(true)
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .rootElement(JsonObjectSchema.builder()
                                        .addProperties(new LinkedHashMap<String, JsonSchemaElement>() {
                                            {
                                                put(
                                                        "sentiment",
                                                        JsonEnumSchema.builder()
                                                                .enumValues("POSITIVE", "NEGATIVE")
                                                                .build());
                                            }
                                        })
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
        assertThat(response.aiMessage().text().trim()).isEqualTo("{\"sentiment\": \"POSITIVE\"}");
    }

    @Test
    void should_handle_enum_output_mode() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
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
                .modelName("gemini-1.5-flash")
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
                        UserMessage.from("Give me 3 dice rolls"))
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
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .logRequests(true)
                .logResponses(true)
                .build();

        List<ToolSpecification> listOfTools = Arrays.asList(
                ToolSpecification.builder().name("toolOne").build(),
                ToolSpecification.builder().name("toolTwo").build());

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Call toolOne"))
                .toolSpecifications(listOfTools)
                .build();

        // when
        ChatResponse response = gemini.chat(request);

        // then
        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("toolOne");

        // given
        gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .logRequests(true)
                .logResponses(true)
                .toolConfig(GeminiMode.ANY, "toolTwo")
                .build();

        // when
        ChatResponse chatResponse = gemini.chat(
                ChatRequest.builder().messages(UserMessage.from("Call toolOne")).build());

        // then
        assertThat(chatResponse.aiMessage().hasToolExecutionRequests()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void should_handle_timeout(int millis) {

        // given
        Duration timeout = Duration.ofMillis(millis);

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
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
                .modelName("gemini-2.5-flash-image-preview")
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

        if (hasGeneratedImages(aiMessage)) {
            List<dev.langchain4j.data.image.Image> generatedImages = GeneratedImageHelper.getGeneratedImages(aiMessage);
            assertThat(generatedImages).isNotEmpty();

            for (dev.langchain4j.data.image.Image image : generatedImages) {
                assertThat(image.base64Data()).isNotEmpty();
                assertThat(image.mimeType()).startsWith("image/");
            }
        }

        assertThat(aiMessage.text() != null || hasGeneratedImages(aiMessage)).isTrue();
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
