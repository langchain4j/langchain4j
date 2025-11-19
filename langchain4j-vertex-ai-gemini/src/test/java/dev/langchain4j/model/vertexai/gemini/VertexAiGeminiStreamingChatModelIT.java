package dev.langchain4j.model.vertexai.gemini;

import static dev.langchain4j.model.LambdaStreamingResponseHandler.onPartialResponse;
import static dev.langchain4j.model.vertexai.gemini.HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT;
import static dev.langchain4j.model.vertexai.gemini.HarmCategory.HARM_CATEGORY_HARASSMENT;
import static dev.langchain4j.model.vertexai.gemini.HarmCategory.HARM_CATEGORY_HATE_SPEECH;
import static dev.langchain4j.model.vertexai.gemini.HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT;
import static dev.langchain4j.model.vertexai.gemini.SafetyThreshold.BLOCK_LOW_AND_ABOVE;
import static dev.langchain4j.model.vertexai.gemini.SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE;
import static dev.langchain4j.model.vertexai.gemini.SafetyThreshold.BLOCK_NONE;
import static dev.langchain4j.model.vertexai.gemini.SafetyThreshold.BLOCK_ONLY_HIGH;
import static dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModelIT.GSON;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Type;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;

class VertexAiGeminiStreamingChatModelIT {

    public static final String MODEL_NAME = "gemini-2.5-flash";

    StreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID"))
            .location(System.getenv("GCP_LOCATION"))
            .modelName(MODEL_NAME)
            .build();

    @Test
    void should_stream_answer_with_custom_credentials() throws IOException {
        StreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(MODEL_NAME)
                .credentials(GoogleCredentials.getApplicationDefault())
                .build();

        // given
        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).contains("Berlin");
    }

    @Test
    void should_allow_custom_generativeModel_and_generationConfig() {

        // given
        VertexAI vertexAi = new VertexAI(System.getenv("GCP_PROJECT_ID"), System.getenv("GCP_LOCATION"));
        GenerativeModel generativeModel = new GenerativeModel(MODEL_NAME, vertexAi);
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
    void should_accept_text_and_image_from_google_storage_url() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from("gs://langchain4j-test/cat.png"),
                TextContent.from("What do you see?"));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text().toLowerCase()).containsAnyOf("cat", "lynx", "feline", "animal");
    }

    @Test
    void should_use_google_search() {

        // given
        VertexAiGeminiStreamingChatModel modelWithSearch = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName("gemini-2.0-flash-lite")
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
                .modelName("gemini-2.0-flash-lite")
                .responseMimeType("application/json")
                .build();

        String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler. "
                + "Before returning, tell me a joke."; // nudging it to say something additionally to json

        String expectedJson = "{\"name\": \"Klaus\", \"surname\": \"Heisler\"}";

        TestStreamingChatResponseHandler handler1 = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler1);
        assertThat(handler1.get().aiMessage().text()).isNotEqualToIgnoringWhitespace(expectedJson);

        // when
        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        modelWithResponseMimeType.chat(userMessage, handler2);

        // then
        ChatResponse chatResponse = handler2.get();
        assertThat(chatResponse.aiMessage().text()).isEqualToIgnoringWhitespace("[" + expectedJson + "]");
    }

    @Disabled("TODO fix")
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
                .modelName("gemini-2.0-flash-lite")
                .safetySettings(safetySettings)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        Exception exception = assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> model.chat("You're a dumb bastard!!!", onPartialResponse(System.out::println)))
                .actual();

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

    @Test
    void should_accept_response_schema() {
        // given
        Schema schema = SchemaHelper.fromClass(VertexAiGeminiChatModelIT.Artist.class);

        // when
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(MODEL_NAME)
                .logRequests(true)
                .logResponses(true)
                .responseMimeType("application/json")
                .responseSchema(schema)
                .build();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("Return a JSON object, as defined in the JSON schema."));
        messages.add(UserMessage.from("Anna is a 23 year old artist from New York City. She's got a dog and a cat."));

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(messages, handler);

        // then
        ChatResponse chatResponse = handler.get();
        Artist artist = GSON.fromJson(chatResponse.aiMessage().text(), Artist.class);
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
                .modelName(MODEL_NAME)
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
                .modelName(MODEL_NAME)
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
                .modelName(MODEL_NAME)
                .logRequests(true)
                .logResponses(true)
                .responseSchema(Schema.newBuilder()
                        .setType(Type.STRING)
                        .addAllEnum(Arrays.asList("POSITIVE", "NEGATIVE"))
                        .build())
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        String instruction = "What is the sentiment expressed in the following sentence: ";

        // when
        model.chat(instruction + "This is super exciting news, congratulations!", handler);

        // then
        assertThat(handler.get().aiMessage().text()).isEqualTo("POSITIVE");
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
