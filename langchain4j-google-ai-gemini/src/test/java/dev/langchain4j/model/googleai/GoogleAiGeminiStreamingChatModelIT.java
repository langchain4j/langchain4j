package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.googleai.GeminiHarmBlockThreshold.BLOCK_LOW_AND_ABOVE;
import static dev.langchain4j.model.googleai.GeminiHarmCategory.HARM_CATEGORY_HARASSMENT;
import static dev.langchain4j.model.googleai.GeminiHarmCategory.HARM_CATEGORY_HATE_SPEECH;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate.GeminiFinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.output.JsonSchemas;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiStreamingChatModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void should_answer_in_json() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .responseFormat(ResponseFormat.JSON)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the firstname of the John Doe?\n"
                + "Reply in JSON following with the following format: {\"firstname\": string}");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

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
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .build();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from("Hello, my name is Guillaume"));
        messages.add(AiMessage.from("Hi, how can I assist you today?"));
        messages.add(UserMessage.from("What is my name? Reply with just my name."));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(messages, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).contains("Guillaume");
    }

    @Test
    void should_support_audio_file() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .build();

        UserMessage userMessage = UserMessage.from(
                AudioContent.from(
                        new String(
                                Base64.getEncoder()
                                        .encode( // TODO use local file
                                                readBytes(
                                                        "https://storage.googleapis.com/cloud-samples-data/generative-ai/audio/pixel.mp3"))),
                        "audio/mp3"),
                TextContent.from("Give a summary of the audio"));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Pixel");
    }

    @Test
    @Disabled
    void should_support_video_file() {
        // ToDo waiting for the normal GoogleAiGeminiChatModel to implement the test
    }

    void should_execute_python_code() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .logRequests(true)
                .logResponses(true)
                .allowCodeExecution(true)
                .includeCodeExecutionOutput(true)
                .build();

        UserMessage userMessage =
                UserMessage.from("Calculate `fibonacci(13)`. Write code in Python and execute it to get the result.");
        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        String text = response.aiMessage().text();
        System.out.println("text = " + text);

        assertThat(text).containsIgnoringCase("233");
    }

    @Disabled("TODO fix")
    void should_support_safety_settings() {
        // given
        Map<GeminiHarmCategory, GeminiHarmBlockThreshold> mapSafetySettings = new HashMap<>();
        mapSafetySettings.put(HARM_CATEGORY_HATE_SPEECH, BLOCK_LOW_AND_ABOVE);
        mapSafetySettings.put(HARM_CATEGORY_HARASSMENT, BLOCK_LOW_AND_ABOVE);

        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")
                .logRequests(true)
                .logResponses(true)
                .safetySettings(mapSafetySettings)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat("You're a dumb f*cking idiot bastard!", handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.finishReason())
                .isEqualTo(FinishReasonMapper.fromGFinishReasonToFinishReason(GeminiFinishReason.SAFETY));
    }

    @Test
    void should_comply_to_response_schema() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
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
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(
                List.of(
                        SystemMessage.from("Your role is to extract information related to a person,"
                                + "like their name, their address, the city the live in."),
                        UserMessage.from("In the town of Liverpool, lived Tommy Skybridge, a young little boy.")),
                handler);
        ChatResponse response = handler.get();

        System.out.println("response = " + response);

        // then
        assertThat(response.aiMessage().text().trim())
                .isEqualToIgnoringWhitespace("{\"name\": \"Tommy Skybridge\", \"address\": {\"city\": \"Liverpool\"}}");
    }

    @Test
    void should_handle_enum_type() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
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
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(
                List.of(
                        SystemMessage.from("Your role is to analyze the sentiment of the text you receive."),
                        UserMessage.from("This is super exciting news, congratulations!")),
                handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).isEqualToIgnoringWhitespace("{\"sentiment\":\"POSITIVE\"}");
    }

    @Test
    void should_handle_enum_output_mode() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
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
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(
                List.of(
                        SystemMessage.from("Your role is to analyze the sentiment of the text you receive."),
                        UserMessage.from("This is super exciting news, congratulations!")),
                handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text().trim()).isEqualTo("POSITIVE");
    }

    @Test
    void should_allow_array_as_response_schema() throws JsonProcessingException {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
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
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(
                List.of(
                        SystemMessage.from("Your role is to return a list of 6-faces dice rolls"),
                        UserMessage.from("Give me 3 dice rolls, all at once")),
                handler);
        ChatResponse response = handler.get();

        System.out.println("response = " + response);

        // then
        Integer[] diceRolls = new ObjectMapper().readValue(response.aiMessage().text(), Integer[].class);
        assertThat(diceRolls.length).isEqualTo(3);
    }

    @Test
    void should_deserialize_to_POJO() throws Exception {

        // given
        record Person(String name, int age) {}

        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.0-flash")
                .logRequests(true)
                .logResponses(true)
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchemas.jsonSchemaFrom(Person.class).get())
                        .build())
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat("Klaus is 37 years old", handler);
        ChatResponse response = handler.get();

        Person person = new ObjectMapper().readValue(response.aiMessage().text(), Person.class);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.age).isEqualTo(37);
    }

    @Test
    void should_support_tool_config() {
        // given
        StreamingChatModel model1 = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .logRequests(true)
                .logResponses(true)
                .build();

        List<ChatMessage> chatMessages = List.of(UserMessage.from("Call toolOne"));

        List<ToolSpecification> listOfTools = Arrays.asList(
                ToolSpecification.builder().name("toolOne").build(),
                ToolSpecification.builder().name("toolTwo").build());

        ChatRequest request1 = ChatRequest.builder()
                .messages(chatMessages)
                .toolSpecifications(listOfTools)
                .build();

        // when
        TestStreamingChatResponseHandler handler1 = new TestStreamingChatResponseHandler();
        model1.chat(request1, handler1);
        ChatResponse response1 = handler1.get();

        // then
        assertThat(response1.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response1.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("toolOne");

        // given
        StreamingChatModel model2 = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .logRequests(true)
                .logResponses(true)
                .toolConfig(GeminiMode.ANY, "toolTwo")
                .build();

        // when
        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        model2.chat(request1, handler2);
        ChatResponse response2 = handler2.get();

        // then
        assertThat(response2.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response2.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("toolTwo");
    }

    static class Transactions {
        @Tool("returns amount of a given transaction")
        double getTransactionAmount(@P("ID of a transaction") String id) {
            System.out.printf("called getTransactionAmount(%s)%n", id);
            switch (id) {
                case "T001":
                    return 11.1;
                case "T002":
                    return 22.2;
                default:
                    throw new IllegalArgumentException("Unknown transaction ID: " + id);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void should_handle_timeout(int millis) throws Exception {

        // given
        Duration timeout = Duration.ofMillis(millis);

        GoogleAiGeminiStreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-lite")
                .logRequests(true)
                .logResponses(true)
                .timeout(timeout)
                .build();

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();

        // when
        model.chat("hello, how are you?", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                futureError.completeExceptionally(new RuntimeException("onPartialResponse should not be called"));
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureError.completeExceptionally(new RuntimeException("onCompleteResponse should not be called"));
            }

            @Override
            public void onError(Throwable error) {
                futureError.complete(error);
            }
        });

        Throwable error = futureError.get(5, SECONDS);

        assertThat(error).isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }

    @Test
    void should_generate_image_streaming() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-image")
                .timeout(Duration.ofMinutes(1))
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat("funny puppy", handler);
        ChatResponse response = handler.get();

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

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
