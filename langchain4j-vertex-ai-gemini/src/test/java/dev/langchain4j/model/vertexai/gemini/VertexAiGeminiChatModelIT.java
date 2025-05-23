package dev.langchain4j.model.vertexai.gemini;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.vertexai.gemini.HarmCategory.*;
import static dev.langchain4j.model.vertexai.gemini.SafetyThreshold.*;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

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
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.RetryingTest;

class VertexAiGeminiChatModelIT {

    static final String CAT_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";
    static final String DICE_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png";

    public static final String GEMINI_1_5_PRO = "gemini-1.5-pro-001";

    ChatModel model = VertexAiGeminiChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName(GEMINI_1_5_PRO)
            .logRequests(true)
            .logResponses(true)
            .build();

    ChatModel imageModel = VertexAiGeminiChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName(GEMINI_1_5_PRO)
            .logRequests(false) // images are huge in logs
            .logResponses(true)
            .build();

    @Test
    void should_generate_response() {

        // given
        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).contains("Berlin");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest
    @MethodSource
    void should_support_system_instructions(List<ChatMessage> messages) {

        // when
        ChatResponse response = model.chat(messages);

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
                        UserMessage.from("I see apples"),
                        AiMessage.from("Ich sehe Äpfel"),
                        UserMessage.from("I love apples"))))
                .build();
    }

    @Test
    void should_respect_maxOutputTokens() {

        // given
        ChatModel model = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
                .maxOutputTokens(1)
                .build();

        UserMessage userMessage = UserMessage.from("Tell me a joke");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(4);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(1);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isIn(LENGTH, STOP);
    }

    @Test
    void should_allow_custom_generativeModel_and_generationConfig() {

        // given
        VertexAI vertexAi = new VertexAI(System.getenv("GCP_PROJECT_ID"), System.getenv("GCP_LOCATION"));
        GenerativeModel generativeModel = new GenerativeModel(GEMINI_1_5_PRO, vertexAi);
        GenerationConfig generationConfig = GenerationConfig.getDefaultInstance();

        ChatModel model = new VertexAiGeminiChatModel(generativeModel, generationConfig);

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).contains("Berlin");
    }

    @Test
    void should_accept_text_and_image_from_public_url() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL), TextContent.from("What do you see? Reply in one word."));

        // when
        ChatResponse response = imageModel.chat(userMessage);

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
        ChatResponse response = imageModel.chat(userMessage);

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
        ChatResponse response = imageModel.chat(userMessage);

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
        ChatResponse response = imageModel.chat(userMessage);

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
        ChatResponse response = imageModel.chat(userMessage);

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
        ChatResponse response = imageModel.chat(userMessage);

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
        ChatResponse response = imageModel.chat(userMessage);

        // then
        assertThat(response.aiMessage().text())
                .containsIgnoringCase("cat")
                //                .containsIgnoringCase("dog")  // sometimes the model replies "puppy" instead of "dog"
                .containsIgnoringCase("dice");
    }

    @Test
    void should_accept_tools_for_function_calling() {

        // given
        ChatModel model = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
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
        ChatResponse messageResponse = model.chat(request);

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

        ChatResponse weatherResponse = model.chat(allMessages);

        // then
        assertThat(weatherResponse.aiMessage().text()).containsIgnoringCase("sunny");
    }

    @Test
    void should_handle_parallel_function_calls() {

        // given
        ChatModel model = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-2.0-flash")
                .temperature(0.0f)
                .topK(1)
                .logRequests(true)
                .logResponses(true)
                .build();

        ToolSpecification stockInventoryToolSpec = ToolSpecification.builder()
                .name("getProductInventory")
                .description("Get the product inventory for a particular product ID")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("product_id", "the ID of the product")
                        .required("product_id")
                        .build())
                .build();

        List<ChatMessage> allMessages = new ArrayList<>();

        UserMessage inventoryQuestion = UserMessage.from("Is there more stock of product ABC123 or of XYZ789? " +
                "Output just a (single) name of the product with more stock.");
        allMessages.add(inventoryQuestion);

        ChatRequest request = ChatRequest.builder()
                .messages(allMessages)
                .toolSpecifications(stockInventoryToolSpec)
                .build();

        // when
        ChatResponse messageResponse = model.chat(request);

        // then
        assertThat(messageResponse.aiMessage().hasToolExecutionRequests()).isTrue();

        List<ToolExecutionRequest> executionRequests = messageResponse.aiMessage().toolExecutionRequests();
        assertThat(executionRequests).hasSize(2); // ie. parallel function execution requests

        String inventoryStock =
                executionRequests.stream().map(ToolExecutionRequest::arguments).collect(Collectors.joining(","));

        assertThat(inventoryStock).containsIgnoringCase("ABC123");
        assertThat(inventoryStock).containsIgnoringCase("XYZ789");

        // when
        allMessages.add(messageResponse.aiMessage());

        allMessages.add(ToolExecutionResultMessage.toolExecutionResultMessage(
                null, "getProductInventory", "{\"product_id\":\"ABC123\", \"stock\": 10}"));
        allMessages.add(ToolExecutionResultMessage.toolExecutionResultMessage(
                null, "getProductInventory", "{\"product_id\":\"XYZ789\", \"stock\": 5}"));

        messageResponse = model.chat(allMessages);

        // then
        assertThat(messageResponse.aiMessage().text())
                .contains("ABC123")
                .doesNotContain("XYZ789");
    }

    static class Calculator {

        @Tool("Adds two given numbers")
        double add(double a, double b) {
            System.out.printf("Called add(%s, %s)%n", a, b);
            return a + b;
        }

        @Tool("Multiplies two given numbers")
        String multiply(double a, double b) {
            System.out.printf("Called multiply(%s, %s)%n", a, b);
            return String.valueOf(a * b);
        }
    }

    interface Assistant {

        String chat(String userMessage);
    }

    @Test
    void should_use_tools_with_AiService() {

        // given
        Calculator calculator = spy(new Calculator());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(calculator)
                .build();

        // when
        String answer = assistant.chat("How much is 74589613588 + 4786521789?");

        // then
        assertThat(answer).contains("79376135377");

        verify(calculator).add(74589613588.0, 4786521789.0);
        verifyNoMoreInteractions(calculator);
    }

    @Test
    void should_use_tools_with_AiService_2() {

        // given
        Calculator calculator = spy(new Calculator());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(calculator)
                .build();

        // when
        String answer = assistant.chat("How much is 257 * 467?");

        // then
        // assertThat(answer).contains("120019"); TODO

        verify(calculator).multiply(257, 467);
        verifyNoMoreInteractions(calculator);
    }

    static class PetName {
        @Tool("gives the name of the pet")
        String getPetName() {
            return "Felicette";
        }
    }

    @Test
    void should_support_noarg_fn() {

        // given
        PetName petName = new PetName();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(petName)
                .build();

        // when
        String answer = assistant.chat("What is the name of the pet?");

        // then
        assertThat(answer).contains("Felicette");
    }

    static class Transaction {
        private final String id;

        Transaction(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Transaction { id = '" + id + '\'' + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Transaction that = (Transaction) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }

    static class FunctionCallingService {
        private static final Map<Transaction, String> DATASET = new HashMap<Transaction, String>() {
            {
                put(new Transaction("001"), "pending");
                put(new Transaction("002"), "approved");
                put(new Transaction("003"), "rejected");
            }
        };

        @Tool("Get the status of a payment transaction identified by its transaction ID.")
        public String paymentStatus(@P("The ID of the payment transaction") String transactionId) {
            return "Transaction " + transactionId + " is " + DATASET.get(new Transaction(transactionId));
        }
    }

    interface FunctionCallingAssistant {
        @dev.langchain4j.service.SystemMessage(
                "You MUST call the `paymentStatus()` function if you're asked about a payment transaction.")
        String chat(String userMessage);
    }

    @Test
    void should_work_with_interspersed_function_execution_results() {
        // given
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-1.5-flash-001")
                .temperature(0.0f)
                .topK(1)
                .logRequests(true)
                .logResponses(true)
                .build();

        FunctionCallingService service = new FunctionCallingService();

        FunctionCallingAssistant assistant = AiServices.builder(FunctionCallingAssistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
                .tools(service)
                .build();

        // when
        String response = assistant.chat("What is the status of my payment transactions 001 and 002?");

        // then
        assertThat(response).contains("001");
        assertThat(response).contains("002");
        assertThat(response).contains("pending");
        assertThat(response).contains("approved");
        assertThat(response).doesNotContain("003");
        assertThat(response).doesNotContain("rejected");

        // when
        response = assistant.chat("What is the status of transactions 003?");

        // then
        assertThat(response).doesNotContain("001");
        assertThat(response).doesNotContain("002");
        assertThat(response).doesNotContain("pending");
        assertThat(response).doesNotContain("approved");
        assertThat(response).contains("003");
        assertThat(response).contains("rejected");
    }

    @Test
    void should_use_google_search() {

        // given
        VertexAiGeminiChatModel modelWithSearch = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-1.5-flash-001")
                .useGoogleSearch(true)
                .build();

        // when
        String resp = modelWithSearch.chat("Why is the sky blue?");

        // then
        assertThat(resp).contains("scatter");
    }

    @Test
    void should_support_json_response_mime_type() {

        // given
        VertexAiGeminiChatModel modelWithResponseMimeType = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-1.5-flash-001")
                .responseMimeType("application/json")
                .build();

        String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler. "
                + "Before returning, tell me a joke."; // nudging it to say something additionally to json

        String expectedJson = "{\"name\": \"Klaus\", \"surname\": \"Heisler\"}";

        assertThat(model.chat(userMessage)).isNotEqualToIgnoringWhitespace(expectedJson);

        // when
        String json = modelWithResponseMimeType.chat(userMessage);

        // then
        assertThat(json).isEqualToIgnoringWhitespace(expectedJson);
    }

    @RetryingTest(10)
    void should_allow_defining_safety_settings() {
        // given
        HashMap<HarmCategory, SafetyThreshold> safetySettings = new HashMap<>();
        safetySettings.put(HARM_CATEGORY_HARASSMENT, BLOCK_LOW_AND_ABOVE);
        safetySettings.put(HARM_CATEGORY_DANGEROUS_CONTENT, BLOCK_ONLY_HIGH);
        //        safetySettings.put(HARM_CATEGORY_HATE_SPEECH, BLOCK_NONE);
        safetySettings.put(HARM_CATEGORY_SEXUALLY_EXPLICIT, BLOCK_MEDIUM_AND_ABOVE);

        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-1.5-flash-001")
                .safetySettings(safetySettings)
                .temperature(0.0f)
                .topP(0.0f)
                .topK(1)
                .seed(1234)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        Exception exception = assertThrows(
                RuntimeException.class, () -> model.chat("You're a dumb fucking bastard!!! I'm gonna kill you!"));

        // then
        assertThat(exception.getMessage()).contains("The response is blocked due to safety reason");
    }

    class Artist {
        public String artistName;
        int artistAge;
        protected boolean artistAdult;
        private String artistAddress;
        public Pet[] pets;
    }

    class Pet {
        public String name;
    }

    @Test
    void should_accept_response_schema() {
        // given
        Schema schema = SchemaHelper.fromClass(Artist.class);

        // when
        ChatModel model = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
                .logRequests(true)
                .logResponses(true)
                .responseMimeType("application/json")
                .responseSchema(schema)
                .build();

        List<ChatMessage> messages = new ArrayList<>();
        //        messages.add(SystemMessage.from("Return a JSON object, with keys: `artist-name`, `artist-age`,
        // `artist-address`, `artist-pets`, `artist-adult`."));
        messages.add(SystemMessage.from("Return a JSON object, as defined in the JSON schema."));
        messages.add(UserMessage.from("Anna is a 23 year old artist from New York City. She's got a dog and a cat."));

        ChatResponse response = model.chat(messages);

        // then
        Artist artist = new Gson().fromJson(response.aiMessage().text(), Artist.class);
        assertThat(artist.artistName).contains("Anna");
        assertThat(artist.artistAge).isEqualTo(23);
        assertThat(artist.artistAdult).isTrue();
        assertThat(artist.artistAddress).contains("New York");
        assertThat(artist.pets).hasSize(2);
    }

    @Test
    void should_honor_subset_of_function_calls() {
        // given
        ChatModel model = VertexAiGeminiChatModel.builder()
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
        ChatResponse answer = model.chat(request);

        // then
        assertThat(answer.aiMessage().hasToolExecutionRequests()).isEqualTo(true);
        assertThat(answer.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("add");
        assertThat(answer.aiMessage().toolExecutionRequests().get(0).arguments()).isEqualTo("{\"a\":1.0,\"b\":2.0}");
    }

    @Test
    void should_forbid_function_calls() {
        // given
        ChatModel model = VertexAiGeminiChatModel.builder()
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
        ChatResponse answer = model.chat(request);

        // then
        assertThat(answer.aiMessage().hasToolExecutionRequests()).isEqualTo(false);
    }

    @Test
    void should_accept_audio_input() {
        // given
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        UserMessage msg = UserMessage.from(
                AudioContent.from("https://storage.googleapis.com/cloud-samples-data/generative-ai/audio/pixel.mp3"),
                TextContent.from("Give a summary of the audio"));

        // when
        ChatResponse response = model.chat(msg);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Pixel");
    }

    @Test
    void should_accept_video_input() {
        // given
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(GEMINI_1_5_PRO)
                .logRequests(false) // videos are huge in logs
                .logResponses(true)
                .build();

        // when
        UserMessage msg = UserMessage.from(
                VideoContent.from("gs://cloud-samples-data/video/animals.mp4"),
                TextContent.from("What is in this video?"));

        // when
        ChatResponse response = model.chat(msg);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("animal");
    }

    @Test
    void should_accept_local_file() {
        // given
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
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
        ChatResponse response = model.chat(msg);

        // then
        assertThat(response.aiMessage().text()).containsAnyOf("finger", "hand");
    }

    @Test
    void should_accept_PDF_documents() {
        // given
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
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
        ChatResponse response = model.chat(msg);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Gemini");
    }

    @Test
    void should_support_enum_structured_output() {
        // given
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
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
        String instruction = "What is the sentiment expressed in the following sentence: ";
        String response = model.chat(instruction + "This is super exciting news, congratulations!");

        // then
        assertThat(response).isEqualTo("POSITIVE");

        // when
        response = model.chat(instruction + "The sky is blue.");

        // then
        assertThat(response).isEqualTo("NEUTRAL");

        // when
        response = model.chat(instruction + "This is the worst movie I've ever watched! Boring!");

        // then
        assertThat(response).isEqualTo("NEGATIVE");
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
