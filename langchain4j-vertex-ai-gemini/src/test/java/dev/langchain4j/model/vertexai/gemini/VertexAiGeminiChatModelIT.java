package dev.langchain4j.model.vertexai.gemini;

import static dev.langchain4j.model.vertexai.gemini.HarmCategory.*;
import static dev.langchain4j.model.vertexai.gemini.SafetyThreshold.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Type;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.gson.Gson;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;

class VertexAiGeminiChatModelIT {

    public static final Gson GSON = new Gson();
    public static final String MODEL_NAME = "gemini-2.5-flash";

    ChatModel model = VertexAiGeminiChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName(MODEL_NAME)
            .logRequests(true)
            .logResponses(true)
            .build();

    ChatModel imageModel = VertexAiGeminiChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName(MODEL_NAME)
            .logRequests(false) // images are huge in logs
            .logResponses(true)
            .build();

    @Test
    void should_generate_response_with_custom_credentials() throws IOException {

        // given
        ChatModel model = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(MODEL_NAME)
                .logRequests(true)
                .logResponses(true)
                .credentials(GoogleCredentials.getApplicationDefault())
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).contains("Berlin");
    }

    @Test
    void should_allow_custom_generativeModel_and_generationConfig() {

        // given
        VertexAI vertexAi = new VertexAI(System.getenv("GCP_PROJECT_ID"), System.getenv("GCP_LOCATION"));
        GenerativeModel generativeModel = new GenerativeModel(MODEL_NAME, vertexAi);
        GenerationConfig generationConfig = GenerationConfig.getDefaultInstance();

        ChatModel model = new VertexAiGeminiChatModel(generativeModel, generationConfig);

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).contains("Berlin");
    }

    @Test
    void should_accept_text_and_image_from_google_storage_url() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from("gs://langchain4j-test/cat.png"),
                TextContent.from("What do you see?"));

        // when
        ChatResponse response = imageModel.chat(userMessage);

        // then
        assertThat(response.aiMessage().text().toLowerCase()).containsAnyOf("cat", "lynx", "feline", "animal");
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
        private static final Map<Transaction, String> DATASET = new HashMap<>() {
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
                .modelName(MODEL_NAME)
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
        assertThat(response)
                .contains("001")
                .contains("002")
                .contains("pending")
                .contains("approved")
                .doesNotContain("003")
                .doesNotContain("rejected");

        // when
        response = assistant.chat("What is the status of transactions 003?");

        // then
        assertThat(response)
                .doesNotContain("001")
                .doesNotContain("002")
                .doesNotContain("pending")
                .doesNotContain("approved")
                .contains("003")
                .contains("rejected");
    }

    @Test
    void should_use_google_search() {

        // given
        VertexAiGeminiChatModel modelWithSearch = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-2.0-flash-lite")
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
                .modelName(MODEL_NAME)
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

    @Disabled("TODO fix")
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
                .modelName(MODEL_NAME)
                .safetySettings(safetySettings)
                .temperature(0.0f)
                .topP(0.0f)
                .topK(1)
                .seed(1234)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        Exception exception = assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> model.chat("You're a dumb fucking bastard!!! I'm gonna kill you!"))
                .actual();

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
                .modelName(MODEL_NAME)
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
        Artist artist = GSON.fromJson(response.aiMessage().text(), Artist.class);
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
                .modelName(MODEL_NAME)
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

        ChatRequest request =
                ChatRequest.builder().messages(msg).toolSpecifications(adder).build();

        // when
        ChatResponse answer = model.chat(request);

        // then
        assertThat(answer.aiMessage().hasToolExecutionRequests()).isEqualTo(true);
        assertThat(answer.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("add");
        assertThat(answer.aiMessage().toolExecutionRequests().get(0).arguments())
                .isEqualTo("{\"a\":1.0,\"b\":2.0}");
    }

    @Test
    void should_forbid_function_calls() {
        // given
        ChatModel model = VertexAiGeminiChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(MODEL_NAME)
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

        ChatRequest request =
                ChatRequest.builder().messages(msg).toolSpecifications(adder).build();

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
                .modelName(MODEL_NAME)
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
                .modelName(MODEL_NAME)
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
                .modelName(MODEL_NAME)
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
                .modelName(MODEL_NAME)
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
                .modelName(MODEL_NAME)
                .logRequests(true)
                .logResponses(true)
                .responseSchema(Schema.newBuilder()
                        .setType(Type.STRING)
                        .addAllEnum(Arrays.asList("POSITIVE", "NEGATIVE"))
                        .build())
                .build();

        String instruction = "What is the sentiment expressed in the following sentence: ";

        // when
        String response = model.chat(instruction + "This is super exciting news, congratulations!");

        // then
        assertThat(response).isEqualTo("POSITIVE");
    }

    record CountryCapitals(List<CountryCapital> countries) {}

    record CountryCapital(String country, String capital) {}

    @Test
    void should_support_text_response_format() {
        // given
        UserMessage userMessage = UserMessage.from("List the capitals of Germany, France, and Italy");

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(userMessage))
                .responseFormat(ResponseFormat.TEXT)
                .build();

        // when
        ChatResponse response = model.chat(request);

        // then
        String textResponse = response.aiMessage().text();
        assertThat(textResponse).contains("Berlin").contains("Paris").contains("Rome");
    }

    @Test
    void should_support_json_response_format() {
        // given
        UserMessage userMessage = UserMessage.from(
                "List the capitals of Germany, France, and Italy as JSON with this format: {\"countries\": [{\"country\": \"name\", \"capital\": \"capital\"}]}");

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(userMessage))
                .responseFormat(ResponseFormat.JSON)
                .build();

        // when
        ChatResponse response = model.chat(request);

        // then
        String jsonResponse = response.aiMessage().text();

        // Verify it's valid JSON by parsing it with Gson

        // Parse the JSON response into our POJO
        CountryCapitals capitals = GSON.fromJson(jsonResponse, CountryCapitals.class);

        // Verify we have the expected countries and capitals
        assertThat(capitals.countries()).isNotNull();
        assertThat(capitals.countries()).hasSize(3);

        // Verify the expected countries and capitals
        assertThat(capitals.countries())
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .isEqualTo(List.of(
                        new CountryCapital("Germany", "Berlin"),
                        new CountryCapital("France", "Paris"),
                        new CountryCapital("Italy", "Rome")));
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
