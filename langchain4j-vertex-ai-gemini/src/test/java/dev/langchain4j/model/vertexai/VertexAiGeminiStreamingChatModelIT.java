package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
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

import java.util.ArrayList;
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

        assertThat(response.finishReason()).isEqualTo(LENGTH);
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
        System.out.println("Question: " + weatherQuestion.text());
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
        System.out.println("Answer: " + weatherResponse.content().text());
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
            .temperature(0f)
            .topK(1)
            .build();

        StreamingStockAssistant assistant = AiServices.builder(StreamingStockAssistant.class)
            .streamingChatLanguageModel(model)
            .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
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
            .project("genai-java-demos")
            .location("us-central1")
            .modelName("gemini-1.5-flash-001")
            .responseMimeType("application/json")
            .build();

        // when
        StringBuilder accumulatedResponse = new StringBuilder();
        modelWithResponseMimeType.generate("Run a dice roll", onNext(accumulatedResponse::append));
        String response = accumulatedResponse.toString();

        // then
        System.out.println("streamed response = " + response);
        assertThat(response).containsAnyOf("1", "2", "3", "4", "5", "6");
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
            .project("genai-java-demos")
            .location("us-central1")
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
}