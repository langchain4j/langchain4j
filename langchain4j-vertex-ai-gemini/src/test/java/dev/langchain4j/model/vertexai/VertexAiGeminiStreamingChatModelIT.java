package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.LambdaStreamingResponseHandler.onNext;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.vertexai.HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT;
import static dev.langchain4j.model.vertexai.HarmCategory.HARM_CATEGORY_HARASSMENT;
import static dev.langchain4j.model.vertexai.HarmCategory.HARM_CATEGORY_HATE_SPEECH;
import static dev.langchain4j.model.vertexai.HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT;
import static dev.langchain4j.model.vertexai.SafetyThreshold.BLOCK_LOW_AND_ABOVE;
import static dev.langchain4j.model.vertexai.SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE;
import static dev.langchain4j.model.vertexai.SafetyThreshold.BLOCK_NONE;
import static dev.langchain4j.model.vertexai.SafetyThreshold.BLOCK_ONLY_HIGH;
import static dev.langchain4j.model.vertexai.VertexAiGeminiChatModelIT.CAT_IMAGE_URL;
import static dev.langchain4j.model.vertexai.VertexAiGeminiChatModelIT.DICE_IMAGE_URL;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VertexAiGeminiStreamingChatModelIT {

    public static final String GEMINI_1_5_PRO = "gemini-1.5-pro-001";

    StreamingChatLanguageModel model = VertexAiGeminiStreamingChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName(GEMINI_1_5_PRO)
            .build();

    @Test
    void should_stream_answer() {

        // given
        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).contains("Berlin");

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(7);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount())
                .isEqualTo(response.tokenUsage().inputTokenCount() + response.tokenUsage().outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest
    @MethodSource
    void should_support_system_instructions(List<ChatMessage> messages) {

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(messages, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("lieb");
    }

    static Stream<Arguments> should_support_system_instructions() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(
                        asList(
                                SystemMessage.from("Translate in German"),
                                UserMessage.from("I love apples")
                        )
                ))
                .add(Arguments.of(
                        asList(
                                UserMessage.from("I love apples"),
                                SystemMessage.from("Translate in German")
                        )
                ))
                .add(Arguments.of(
                        asList(
                                SystemMessage.from("Translate in Italian"),
                                UserMessage.from("I love apples"),
                                SystemMessage.from("No, translate in German!")
                        )
                ))
                .add(Arguments.of(
                        asList(
                                SystemMessage.from("Translate in German"),
                                UserMessage.from(asList(
                                        TextContent.from("I love apples"),
                                        TextContent.from("I see apples")
                                ))
                        )
                ))
                .add(Arguments.of(
                        asList(
                                SystemMessage.from("Translate in German"),
                                UserMessage.from(asList(
                                        TextContent.from("I see apples"),
                                        TextContent.from("I love apples")
                                ))
                        )
                ))
                .add(Arguments.of(
                        asList(
                                SystemMessage.from("Translate in German"),
                                UserMessage.from("I see appels"),
                                AiMessage.from("Ich sehe Äpfel"),
                                UserMessage.from("I love apples")
                        )
                ))
                .build();
    }

    @Test
    void should_respect_maxOutputTokens() {

        // given
        StreamingChatLanguageModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
                .maxOutputTokens(1)
                .build();

        String userMessage = "Tell me a joke";

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).isNotBlank();

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(4);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(1);
        assertThat(response.tokenUsage().totalTokenCount())
                .isEqualTo(response.tokenUsage().inputTokenCount() + response.tokenUsage().outputTokenCount());

        assertThat(response.finishReason()).isIn(LENGTH, STOP);
    }

    @Test
    void should_allow_custom_generativeModel_and_generationConfig() {

        // given
        VertexAI vertexAi = new VertexAI(System.getenv("GCP_PROJECT_ID"), System.getenv("GCP_LOCATION"));
        GenerativeModel generativeModel = new GenerativeModel(GEMINI_1_5_PRO, vertexAi);
        GenerationConfig generationConfig = GenerationConfig.getDefaultInstance();

        StreamingChatLanguageModel model = new VertexAiGeminiStreamingChatModel(generativeModel, generationConfig);

        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).contains("Berlin");
    }

    @Test
    void should_accept_text_and_image_from_public_url() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                TextContent.from("What do you see? Reply in one word.")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_image_from_google_storage_url() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from("gs://langchain4j-test/cat.png"),
                TextContent.from("What do you see? Reply in one word.")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_base64_image() {

        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(base64Data, "image/png"),
                TextContent.from("What do you see? Reply in one word.")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_multiple_images_from_public_urls() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from(DICE_IMAGE_URL),
                TextContent.from("What do you see? Reply with one word per image.")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_images_from_google_storage_urls() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from("gs://langchain4j-test/cat.png"),
                ImageContent.from("gs://langchain4j-test/dice.png"),
                TextContent.from("What do you see? Reply with one word per image.")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_base64_images() {

        // given
        String catBase64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        String diceBase64Data = Base64.getEncoder().encodeToString(readBytes(DICE_IMAGE_URL));
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(catBase64Data, "image/png"),
                ImageContent.from(diceBase64Data, "image/png"),
                TextContent.from("What do you see? Reply with one word per image.")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_images_from_different_sources() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from("gs://langchain4j-test/dog.jpg"),
                ImageContent.from(Base64.getEncoder().encodeToString(readBytes(DICE_IMAGE_URL)), "image/png"),
                TextContent.from("What do you see? Reply with one word per image.")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text())
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
                .modelName(GEMINI_1_5_PRO)
                .build();

        ToolSpecification weatherToolSpec = ToolSpecification.builder()
                .name("getWeatherForecast")
                .description("Get the weather forecast for a location")
                .addParameter("location", JsonSchemaProperty.STRING,
                        JsonSchemaProperty.description("the location to get the weather forecast for"))
                .build();

        List<ChatMessage> allMessages = new ArrayList<>();

        UserMessage weatherQuestion = UserMessage.from("What is the weather in Paris?");
        allMessages.add(weatherQuestion);

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(allMessages, weatherToolSpec, handler);
        Response<AiMessage> messageResponse = handler.get();

        // then
        assertThat(messageResponse.content().hasToolExecutionRequests()).isTrue();
        ToolExecutionRequest toolExecutionRequest = messageResponse.content().toolExecutionRequests().get(0);

        assertThat(toolExecutionRequest.arguments()).contains("Paris");
        assertThat(toolExecutionRequest.name()).isEqualTo("getWeatherForecast");

        allMessages.add(messageResponse.content());

        // when (feeding the function return value back)
        ToolExecutionResultMessage toolExecResMsg = ToolExecutionResultMessage.from(toolExecutionRequest,
                "{\"location\":\"Paris\",\"forecast\":\"sunny\", \"temperature\": 20}");
        allMessages.add(toolExecResMsg);

        handler = new TestStreamingResponseHandler<>();
        model.generate(allMessages, handler);
        Response<AiMessage> weatherResponse = handler.get();

        // then
        assertThat(weatherResponse.content().text()).containsIgnoringCase("sunny");
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
        @dev.langchain4j.service.SystemMessage(
            "You MUST call `getStockInventory()` for stock inventory requests.")
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
            .streamingChatLanguageModel(model)
            .chatMemory(chatMemory)
            .tools(new StockInventory())
            .build();

        // when
        CompletableFuture<Response<AiMessage>> future = new CompletableFuture<>();
        assistant.chat("Is there more stock of ABC or of XYZ?")
            .onNext(System.out::println)
            .onComplete(future::complete)
            .onError(future::completeExceptionally)
            .start();
        Response<AiMessage> response = future.get(30, TimeUnit.SECONDS);

        // then
        assertThat(response.content().toString()).contains("XYZ");

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
        assertThat(aiMsg.toolExecutionRequests().size()).isEqualTo(2);
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

        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();

        // when
        modelWithSearch.generate("Why is the sky blue?", handler);

        // then
        assertThat(handler.get().content().text()).containsIgnoringCase("scatter");
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

        String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler. " +
            "Before returning, tell me a joke."; // nudging it to say something additionally to json

        String expectedJson = "{\"name\": \"Klaus\", \"surname\": \"Heisler\"}";

        StringBuilder accumulatedResponse = new StringBuilder();
        model.generate(userMessage, onNext(accumulatedResponse::append));
        assertThat(accumulatedResponse.toString()).isNotEqualToIgnoringWhitespace(expectedJson);

        // when
        accumulatedResponse = new StringBuilder();
        modelWithResponseMimeType.generate(userMessage, onNext(accumulatedResponse::append));

        // then
        assertThat(accumulatedResponse.toString()).isEqualToIgnoringWhitespace(expectedJson);
    }

    @Test
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
        Exception exception = assertThrows(RuntimeException.class, () -> {
            model.generate("You're a dumb bastard!!!", onNext(System.out::println));
        });

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
        model.generate(messages, onNext(accumulatedResponse::append));
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
            .addParameter("a", JsonSchemaProperty.INTEGER)
            .addParameter("b", JsonSchemaProperty.INTEGER)
            .build();

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        UserMessage msg = UserMessage.from("How much is 1 + 2?");
        model.generate(Arrays.asList(msg), adder, handler);

        // then
        assertThat(handler.get().content().hasToolExecutionRequests()).isEqualTo(true);
        assertThat(handler.get().content().toolExecutionRequests().get(0).name()).isEqualTo("add");
        assertThat(handler.get().content().toolExecutionRequests().get(0).arguments()).isEqualTo("{\"a\":1.0,\"b\":2.0}");
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
            .addParameter("a", JsonSchemaProperty.INTEGER)
            .addParameter("b", JsonSchemaProperty.INTEGER)
            .build();

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        UserMessage msg = UserMessage.from("How much is 1 + 2?");
        model.generate(Arrays.asList(msg), adder, handler);

        // then
        assertThat(handler.get().content().hasToolExecutionRequests()).isEqualTo(false);
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
            TextContent.from("Give a summary of the audio")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(msg), handler);

        // then
        assertThat(handler.get().content().text()).containsIgnoringCase("Pixel");
    }

    @Test
    void should_accept_video() {
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
            AudioContent.from("https://storage.googleapis.com/cloud-samples-data/video/animals.mp4"),
            TextContent.from("What's in this video?")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(msg), handler);

        // then
        assertThat(handler.get().content().text()).containsIgnoringCase("animal");
    }

    @Test
    void should_accept_local_file() {
        // given
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName(GEMINI_1_5_PRO)
            .logRequests(true)
            .logResponses(true)
            .build();

        // when
        File file = new File("src/test/resources/fingers.mp4");
        assertThat(file).exists();

        UserMessage msg = UserMessage.from(
            AudioContent.from(Paths.get("src/test/resources/fingers.mp4").toUri()),
            TextContent.from("What's in this video?")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(msg), handler);

        // then
        assertThat(handler.get().content().text()).containsAnyOf("finger", "hand");
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
            PdfFileContent.from(Paths.get("src/test/resources/gemini-doc-snapshot.pdf").toUri()),
            TextContent.from("Provide a summary of the document")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(msg), handler);

        // then
        assertThat(handler.get().content().text()).containsIgnoringCase("Gemini");
    }
}