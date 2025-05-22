package dev.langchain4j.model.vertexai.gemini;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.LambdaStreamingResponseHandler.onPartialResponse;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.vertexai.gemini.HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT;
import static dev.langchain4j.model.vertexai.gemini.HarmCategory.HARM_CATEGORY_HARASSMENT;
import static dev.langchain4j.model.vertexai.gemini.HarmCategory.HARM_CATEGORY_HATE_SPEECH;
import static dev.langchain4j.model.vertexai.gemini.HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT;
import static dev.langchain4j.model.vertexai.gemini.SafetyThreshold.BLOCK_LOW_AND_ABOVE;
import static dev.langchain4j.model.vertexai.gemini.SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE;
import static dev.langchain4j.model.vertexai.gemini.SafetyThreshold.BLOCK_NONE;
import static dev.langchain4j.model.vertexai.gemini.SafetyThreshold.BLOCK_ONLY_HIGH;
import static dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModelIT.CAT_IMAGE_URL;
import static dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModelIT.DICE_IMAGE_URL;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Type;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.gson.Gson;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.RetryingTest;

class VertexAiGeminiStreamingChatModelIT {

    public static final String GEMINI_1_5_PRO = "gemini-1.5-pro-001";

    StreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName(GEMINI_1_5_PRO)
            .build();

    @Test
    void should_stream_answer() {

        // given
        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).contains("Berlin");

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(7);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount())
                .isEqualTo(response.tokenUsage().inputTokenCount()
                        + response.tokenUsage().outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest
    @MethodSource
    void should_support_system_instructions(List<ChatMessage> messages) {

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(messages, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("lieb");
    }

    static Stream<Arguments> should_support_system_instructions() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(asList(SystemMessage.from("Translate in German"), UserMessage.from("I love apples"))))
                .add(Arguments.of(asList(UserMessage.from("I love apples"), SystemMessage.from("Translate in German"))))
                .add(Arguments.of(asList(
                        SystemMessage.from("Translate in Italian"),
                        UserMessage.from("I love apples"),
                        SystemMessage.from("No, translate in German!"))))
                .add(Arguments.of(asList(
                        SystemMessage.from("Translate in German"),
                        UserMessage.from(asList(TextContent.from("I love apples"), TextContent.from("I see apples"))))))
                .add(Arguments.of(asList(
                        SystemMessage.from("Translate in German"),
                        UserMessage.from(asList(TextContent.from("I see apples"), TextContent.from("I love apples"))))))
                .add(Arguments.of(asList(
                        SystemMessage.from("Translate in German"),
                        UserMessage.from("I see appels"),
                        AiMessage.from("Ich sehe Äpfel"),
                        UserMessage.from("I love apples"))))
                .build();
    }

    @Test
    void should_respect_maxOutputTokens() {

        // given
        StreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
                .maxOutputTokens(1)
                .build();

        String userMessage = "Tell me a joke";

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).isNotBlank();

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(4);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(1);
        assertThat(response.tokenUsage().totalTokenCount())
                .isEqualTo(response.tokenUsage().inputTokenCount()
                        + response.tokenUsage().outputTokenCount());

        assertThat(response.finishReason()).isIn(LENGTH, STOP);
    }

    @Test
    void should_allow_custom_generativeModel_and_generationConfig() {

        // given
        VertexAI vertexAi = new VertexAI(System.getenv("GCP_PROJECT_ID"), System.getenv("GCP_LOCATION"));
        GenerativeModel generativeModel = new GenerativeModel(GEMINI_1_5_PRO, vertexAi);
        GenerationConfig generationConfig = GenerationConfig.getDefaultInstance();

        StreamingChatModel model = new VertexAiGeminiStreamingChatModel(generativeModel, generationConfig);

        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).contains("Berlin");
    }

    @Test
    void should_accept_text_and_image_from_public_url() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL), TextContent.from("What do you see? Reply in one word."));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_image_from_google_storage_url() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from("gs://langchain4j-test/cat.png"),
                TextContent.from("What do you see? Reply in one word."));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_base64_image() {

        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(base64Data, "image/png"), TextContent.from("What do you see? Reply in one word."));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_multiple_images_from_public_urls() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from(DICE_IMAGE_URL),
                TextContent.from("What do you see? Reply with one word per image."));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat").containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_images_from_google_storage_urls() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from("gs://langchain4j-test/cat.png"),
                ImageContent.from("gs://langchain4j-test/dice.png"),
                TextContent.from("What do you see? Reply with one word per image."));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat").containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_base64_images() {

        // given
        String catBase64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        String diceBase64Data = Base64.getEncoder().encodeToString(readBytes(DICE_IMAGE_URL));
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(catBase64Data, "image/png"),
                ImageContent.from(diceBase64Data, "image/png"),
                TextContent.from("What do you see? Reply with one word per image."));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat").containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_images_from_different_sources() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from("gs://langchain4j-test/dog.jpg"),
                ImageContent.from(Base64.getEncoder().encodeToString(readBytes(DICE_IMAGE_URL)), "image/png"),
                TextContent.from("What do you see? Reply with one word per image."));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text())
                .containsIgnoringCase("cat")
                //                .containsIgnoringCase("dog")  // sometimes model replies with "puppy" instead of "dog"
                .containsIgnoringCase("dice");
    }

    @Test
    void should_accept_function_call() {

        // given
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-2.0-flash")
                .temperature(0.0f)
                .build();

        ToolSpecification weatherToolSpec = ToolSpecification.builder()
                .name("getWeatherForecast")
                .description("Get the weather forecast for a location")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("location", "the location to get the weather forecast for")
                        .required("location")
                        .build())
                .build();

        List<ChatMessage> allMessages = new ArrayList<>();

        UserMessage weatherQuestion = UserMessage.from("What is the weather in Paris?");
        allMessages.add(weatherQuestion);

        ChatRequest request = ChatRequest.builder()
                .messages(allMessages)
                .toolSpecifications(weatherToolSpec)
                .build();
        
        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(request, handler);
        ChatResponse messageResponse = handler.get();

        // then
        assertThat(messageResponse.aiMessage().hasToolExecutionRequests()).isTrue();
        ToolExecutionRequest toolExecutionRequest =
                messageResponse.aiMessage().toolExecutionRequests().get(0);

        assertThat(toolExecutionRequest.arguments()).contains("Paris");
        assertThat(toolExecutionRequest.name()).isEqualTo("getWeatherForecast");

        allMessages.add(messageResponse.aiMessage());

        // when (feeding the function return value back)
        ToolExecutionResultMessage toolExecResMsg = ToolExecutionResultMessage.from(
                toolExecutionRequest, "{\"location\":\"Paris\",\"forecast\":\"sunny\", \"temperature\": 20}");
        allMessages.add(toolExecResMsg);

        handler = new TestStreamingChatResponseHandler();
        model.chat(allMessages, handler);
        ChatResponse weatherResponse = handler.get();

        // then
        assertThat(weatherResponse.aiMessage().text()).containsIgnoringCase("sunny");
    }

    static class StockInventory {
        @Tool("Get the stock inventory for a product identified by its ID")
        public int getStockInventory(@P("ID of the product") String product) {
            if (product.equals("ABC")) {
                return 10;
            } else if (product.equals("XYZ")) {
                return 42;
            } else {
                return 0;
            }
        }
    }

    interface StreamingStockAssistant {
        @dev.langchain4j.service.SystemMessage("You MUST call `getStockInventory()` for stock inventory requests.")
        TokenStream chat(String msg);
    }

    @Test
    void should_work_with_parallel_function_calls() throws InterruptedException, ExecutionException, TimeoutException {
        // given
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-1.5-flash-001")
                .logRequests(true)
                .logResponses(true)
                .temperature(0.0f)
                .topK(1)
                .build();

        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        StreamingStockAssistant assistant = AiServices.builder(StreamingStockAssistant.class)
                .streamingChatModel(model)
                .chatMemory(chatMemory)
                .tools(new StockInventory())
                .build();

        // when
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("Is there more stock of ABC or of XYZ?")
                .onPartialResponse(System.out::println)
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();
        ChatResponse response = future.get(30, TimeUnit.SECONDS);

        // then
        assertThat(response.aiMessage().toString()).contains("XYZ");

        chatMemory.messages().forEach(System.out::println);

        // Chat memory contains:
        // - SystemMessage -> "You MUST call `getStockInventory()` for stock inventory requests."
        // - UserMessage -> "Is there more stock of ABC or of XYZ?"
        // - AiMessage with 2 parallel tool execution requests:
        //     * { id = null, name = "getStockInventory", arguments = "{"product":"ABC"}" }
        //     * { id = null, name = "getStockInventory", arguments = "{"product":"XYZ"}" }
        // User then feeds two tool execution result messages:
        // - { id = null toolName = "getStockInventory" text = "10" }
        // - { id = null toolName = "getStockInventory" text = "42" }
        // - AiMessage { text = "There is more stock of XYZ.", toolExecutionRequests = null }

        assertThat(chatMemory.messages().get(2).type()).isEqualTo(ChatMessageType.AI);

        AiMessage aiMsg = (AiMessage) chatMemory.messages().get(2);
        assertThat(aiMsg.hasToolExecutionRequests()).isTrue();
        assertThat(aiMsg.toolExecutionRequests()).hasSize(2);
        assertThat(aiMsg.toolExecutionRequests().get(0).name()).isEqualTo("getStockInventory");
        assertThat(aiMsg.toolExecutionRequests().get(0).arguments()).isEqualTo("{\"product\":\"ABC\"}");
        assertThat(aiMsg.toolExecutionRequests().get(1).name()).isEqualTo("getStockInventory");
        assertThat(aiMsg.toolExecutionRequests().get(1).arguments()).isEqualTo("{\"product\":\"XYZ\"}");
    }

    @Test
    void should_use_google_search() {

        // given
        VertexAiGeminiStreamingChatModel modelWithSearch = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-1.5-flash-001")
                .useGoogleSearch(true)
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        // when
        modelWithSearch.chat("Why is the sky blue?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).containsIgnoringCase("scatter");
    }

    @Test
    void should_support_json_response_mime_type() {

        // given
        VertexAiGeminiStreamingChatModel modelWithResponseMimeType = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-1.5-flash-001")
                .responseMimeType("application/json")
                .build();

        String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler. "
                + "Before returning, tell me a joke."; // nudging it to say something additionally to json

        String expectedJson = "{\"name\": \"Klaus\", \"surname\": \"Heisler\"}";

        StringBuilder accumulatedResponse = new StringBuilder();
        model.chat(userMessage, onPartialResponse(accumulatedResponse::append));
        assertThat(accumulatedResponse.toString()).isNotEqualToIgnoringWhitespace(expectedJson);

        // when
        accumulatedResponse = new StringBuilder();
        modelWithResponseMimeType.chat(userMessage, onPartialResponse(accumulatedResponse::append));

        // then
        assertThat(accumulatedResponse.toString()).isEqualToIgnoringWhitespace(expectedJson);
    }

    @RetryingTest(2)
    void should_allow_defining_safety_settings() {
        // given
        HashMap<HarmCategory, SafetyThreshold> safetySettings = new HashMap<>();
        safetySettings.put(HARM_CATEGORY_HARASSMENT, BLOCK_LOW_AND_ABOVE);
        safetySettings.put(HARM_CATEGORY_DANGEROUS_CONTENT, BLOCK_ONLY_HIGH);
        safetySettings.put(HARM_CATEGORY_HATE_SPEECH, BLOCK_NONE);
        safetySettings.put(HARM_CATEGORY_SEXUALLY_EXPLICIT, BLOCK_MEDIUM_AND_ABOVE);

        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-1.5-flash-001")
                .safetySettings(safetySettings)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        Exception exception = assertThrows(
                RuntimeException.class, () -> model.chat("You're a dumb bastard!!!", onPartialResponse(System.out::println)));

        // then
        assertThat(exception.getMessage()).contains("The response is blocked due to safety reason");
    }

    class Artist {
        public String artistName;
        int artistAge;
        protected boolean artistAdult;
        private String artistAddress;
        public VertexAiGeminiChatModelIT.Pet[] pets;
    }

    class Pet {
        public String name;
    }

    @Test
    void should_accept_response_schema() {
        // given
        Schema schema = SchemaHelper.fromClass(VertexAiGeminiChatModelIT.Artist.class);

        // when
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
                .logRequests(true)
                .logResponses(true)
                .responseMimeType("application/json")
                .responseSchema(schema)
                .build();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("Return a JSON object, as defined in the JSON schema."));
        messages.add(UserMessage.from("Anna is a 23 year old artist from New York City. She's got a dog and a cat."));

        StringBuilder accumulatedResponse = new StringBuilder();
        model.chat(messages, onPartialResponse(accumulatedResponse::append));
        String response = accumulatedResponse.toString();

        // then
        Artist artist = new Gson().fromJson(response, Artist.class);
        assertThat(artist.artistName).contains("Anna");
        assertThat(artist.artistAge).isEqualTo(23);
        assertThat(artist.artistAdult).isTrue();
        assertThat(artist.artistAddress).contains("New York");
        assertThat(artist.pets).hasSize(2);
    }

    @Test
    void should_honor_subset_of_function_calls() {
        // given
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
                .logRequests(true)
                .logResponses(true)
                .toolCallingMode(ToolCallingMode.ANY)
                .allowedFunctionNames(Arrays.asList("add"))
                .build();

        ToolSpecification adder = ToolSpecification.builder()
                .description("adds two numbers")
                .name("add")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("a")
                        .addIntegerProperty("b")
                        .required("a", "b")
                        .build())
                .build();

        UserMessage msg = UserMessage.from("How much is 1 + 2?");
        
        ChatRequest request = ChatRequest.builder()
                .messages(msg)
                .toolSpecifications(adder)
                .build();
        
        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(request, handler);

        // then
        assertThat(handler.get().aiMessage().hasToolExecutionRequests()).isEqualTo(true);
        assertThat(handler.get().aiMessage().toolExecutionRequests().get(0).name())
                .isEqualTo("add");
        assertThat(handler.get().aiMessage().toolExecutionRequests().get(0).arguments())
                .isEqualTo("{\"a\":1.0,\"b\":2.0}");
    }

    @Test
    void should_forbid_function_calls() {
        // given
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
                .logRequests(true)
                .logResponses(true)
                .toolCallingMode(ToolCallingMode.NONE)
                .build();

        ToolSpecification adder = ToolSpecification.builder()
                .description("adds two numbers")
                .name("add")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("a")
                        .addIntegerProperty("b")
                        .required("a", "b")
                        .build())
                .build();

        UserMessage msg = UserMessage.from("How much is 1 + 2?");

        ChatRequest request = ChatRequest.builder()
                .messages(msg)
                .toolSpecifications(adder)
                .build();
        
        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(request, handler);

        // then
        assertThat(handler.get().aiMessage().hasToolExecutionRequests()).isEqualTo(false);
    }

    @Test
    void should_accept_audio() {
        // given
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        UserMessage msg = UserMessage.from(
                AudioContent.from("gs://cloud-samples-data/generative-ai/audio/pixel.mp3"),
                TextContent.from("Give a summary of the audio"));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(singletonList(msg), handler);

        // then
        assertThat(handler.get().aiMessage().text()).containsIgnoringCase("Pixel");
    }

    @Test
    void should_accept_video() {
        // given
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
                .logRequests(false) // videos are huge in logs
                .logResponses(true)
                .build();

        // when
        UserMessage msg = UserMessage.from(
                AudioContent.from("https://storage.googleapis.com/cloud-samples-data/video/animals.mp4"),
                TextContent.from("What's in this video?"));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(singletonList(msg), handler);

        // then
        assertThat(handler.get().aiMessage().text()).containsIgnoringCase("animal");
    }

    @Test
    void should_accept_local_file() {
        // given
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
                .logRequests(false) // videos are huge in logs
                .logResponses(true)
                .build();

        // when
        File file = new File("src/test/resources/fingers.mp4");
        assertThat(file).exists();

        UserMessage msg = UserMessage.from(
                AudioContent.from(Paths.get("src/test/resources/fingers.mp4").toUri()),
                TextContent.from("What's in this video?"));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(singletonList(msg), handler);

        // then
        assertThat(handler.get().aiMessage().text()).containsAnyOf("finger", "hand");
    }

    @Test
    void should_accept_PDF_documents() {
        // given
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        UserMessage msg = UserMessage.from(
                PdfFileContent.from(
                        Paths.get("src/test/resources/gemini-doc-snapshot.pdf").toUri()),
                TextContent.from("Provide a summary of the document"));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(singletonList(msg), handler);

        // then
        assertThat(handler.get().aiMessage().text()).containsIgnoringCase("Gemini");
    }

    @Test
    void should_support_enum_structured_output() {
        // given
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
                .logRequests(true)
                .logResponses(true)
                .responseSchema(Schema.newBuilder()
                        .setType(Type.STRING)
                        .addAllEnum(Arrays.asList("POSITIVE", "NEUTRAL", "NEGATIVE"))
                        .build())
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        String instruction = "What is the sentiment expressed in the following sentence: ";
        model.chat(instruction + "This is super exciting news, congratulations!", handler);

        // then
        assertThat(handler.get().aiMessage().text()).isEqualTo("POSITIVE");

        // when
        handler = new TestStreamingChatResponseHandler();
        model.chat(instruction + "The sky is blue.", handler);

        // then
        assertThat(handler.get().aiMessage().text()).isEqualTo("NEUTRAL");

        // when
        handler = new TestStreamingChatResponseHandler();
        model.chat(instruction + "This is the worst movie I've ever watched! Boring!", handler);

        // then
        assertThat(handler.get().aiMessage().text()).isEqualTo("NEGATIVE");
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
