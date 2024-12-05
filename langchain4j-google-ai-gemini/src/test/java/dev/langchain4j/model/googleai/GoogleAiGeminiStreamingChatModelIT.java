package dev.langchain4j.model.googleai;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.TextFileContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.output.JsonSchemas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.googleai.GeminiHarmBlockThreshold.BLOCK_LOW_AND_ABOVE;
import static dev.langchain4j.model.googleai.GeminiHarmCategory.HARM_CATEGORY_HARASSMENT;
import static dev.langchain4j.model.googleai.GeminiHarmCategory.HARM_CATEGORY_HATE_SPEECH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class GoogleAiGeminiStreamingChatModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    private static final String CAT_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";
    private static final String MD_FILE_URL = "https://raw.githubusercontent.com/langchain4j/langchain4j/main/docs/docs/intro.md";

    @Test
    void should_answer_simple_question() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .build();

        UserMessage userMessage = UserMessage.from("What is the capital of France?");

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        String text = response.content().text();
        assertThat(text).containsIgnoringCase("Paris");

        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_configure_generation() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .temperature(2.0)
            .topP(0.5)
            .topK(10)
            .maxOutputTokens(10)
            .build();

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate("How much is 3+4? Reply with just the answer", handler);
        Response<AiMessage> response = handler.get();

        // then
        String text = response.content().text();
        assertThat(text.trim()).isEqualTo("7");
    }

    @Test
    void should_answer_in_json() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-pro")
            .responseFormat(ResponseFormat.JSON)
            .logRequestsAndResponses(true)
            .build();

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate(UserMessage.from("What is the firstname of the John Doe?\n" +
            "Reply in JSON following with the following format: {\"firstname\": string}"), handler);
        Response<AiMessage> response = handler.get();

        // then
        String jsonText = response.content().text();

        assertThat(jsonText).contains("\"firstname\"");
        assertThat(jsonText).contains("\"John\"");

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(25);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(7);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(32);
    }

    @Test
    void should_support_multiturn_chatting() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .build();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from("Hello, my name is Guillaume"));
        messages.add(AiMessage.from("Hi, how can I assist you today?"));
        messages.add(UserMessage.from("What is my name? Reply with just my name."));

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate(messages, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).contains("Guillaume");
    }

    @Test
    void should_support_sending_images_as_base64() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .build();

        UserMessage userMessage = UserMessage.userMessage(
            ImageContent.from(new String(Base64.getEncoder().encode(readBytes(CAT_IMAGE_URL))), "image/png"),
            TextContent.from("Describe this image in a single word")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_support_text_file() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .build();

        UserMessage userMessage = UserMessage.userMessage(
            TextFileContent.from(new String(Base64.getEncoder().encode(readBytes(MD_FILE_URL))), "text/markdown"),
            TextContent.from("What project does this markdown file mention?")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("LangChain4j");
    }

    @Test
    void should_support_audio_file() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .build();

        UserMessage userMessage = UserMessage.from(
            AudioContent.from(
                new String(Base64.getEncoder().encode( //TODO use local file
                    readBytes("https://storage.googleapis.com/cloud-samples-data/generative-ai/audio/pixel.mp3"))),
                "audio/mp3"),
            TextContent.from("Give a summary of the audio")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate(userMessage, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("Pixel");
    }

    @Test
    @Disabled
    void should_support_video_file() {
        // ToDo waiting for the normal GoogleAiGeminiChatModel to implement the test
    }

    @Test
    void should_respect_system_instruction() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .build();

        List<ChatMessage> chatMessages = List.of(
            SystemMessage.from("Translate from English into French"),
            UserMessage.from("apple")
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate(chatMessages, handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text()).containsIgnoringCase("pomme");
    }

    @Test
    void should_execute_python_code() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .logRequestsAndResponses(true)
            .allowCodeExecution(true)
            .includeCodeExecutionOutput(true)
            .build();

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate(
            UserMessage.from("Calculate `fibonacci(13)`. Write code in Python and execute it to get the result."),
            handler
        );
        Response<AiMessage> response = handler.get();

        // then
        String text = response.content().text();
        System.out.println("text = " + text);

        assertThat(text).containsIgnoringCase("233");
    }

    @Test
    void should_support_JSON_array_in_tools() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .logRequestsAndResponses(true)
            .build();

        // when
        List<ChatMessage> allMessages = new ArrayList<>();
        allMessages.add(UserMessage.from("Return a JSON list containing the first 10 fibonacci numbers."));


        TestStreamingResponseHandler<AiMessage> handler1 = new TestStreamingResponseHandler<>();
        gemini.generate(
            allMessages,
            List.of(ToolSpecification.builder()
                .name("getFirstNFibonacciNumbers")
                .description("Get the first n fibonacci numbers")
                .parameters(JsonObjectSchema.builder()
                    .addNumberProperty("n")
                    .build())
                .build()),
            handler1
        );
        Response<AiMessage> response1 = handler1.get();

        // then
        assertThat(response1.content().hasToolExecutionRequests()).isTrue();
        assertThat(response1.content().toolExecutionRequests().get(0).name()).isEqualTo("getFirstNFibonacciNumbers");
        assertThat(response1.content().toolExecutionRequests().get(0).arguments()).contains("\"n\":10");

        allMessages.add(response1.content());

        // when
        ToolExecutionResultMessage forecastResult =
            ToolExecutionResultMessage.from(null, "getFirstNFibonacciNumbers", "[0, 1, 1, 2, 3, 5, 8, 13, 21, 34]");
        allMessages.add(forecastResult);

        // then
        TestStreamingResponseHandler<AiMessage> handler2 = new TestStreamingResponseHandler<>();
        gemini.generate(allMessages, handler2);
        Response<AiMessage> response2 = handler2.get();

        // then
        assertThat(response2.content().text()).contains("[0, 1, 1, 2, 3, 5, 8, 13, 21, 34]");
    }

    // Test is flaky, because Gemini doesn't 100% always ask for parallel tool calls
    // and sometimes requests more information
    @RetryingTest(5)
    void should_support_parallel_tool_execution() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .logRequestsAndResponses(true)
            .build();

        // when
        List<ChatMessage> allMessages = new ArrayList<>();
        allMessages.add(UserMessage.from("Which warehouse has more stock, ABC or XYZ?"));

        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate(
            allMessages,
            List.of(ToolSpecification.builder()
                .name("getWarehouseStock")
                .description("Retrieve the amount of stock available in a warehouse designated by its name")
                .parameters(JsonObjectSchema.builder()
                    .addStringProperty("name", "The name of the warehouse")
                    .build())
                .build()),
            handler
        );
        Response<AiMessage> response = handler.get();


        // then
        assertThat(response.content().hasToolExecutionRequests()).isTrue();

        List<ToolExecutionRequest> executionRequests = response.content().toolExecutionRequests();
        assertThat(executionRequests).hasSize(2);

        String allArgs = executionRequests.stream()
            .map(ToolExecutionRequest::arguments)
            .collect(Collectors.joining(" "));
        assertThat(allArgs).contains("ABC");
        assertThat(allArgs).contains("XYZ");
    }

    @RetryingTest(5)
    void should_support_safety_settings() {
        // given
        Map<GeminiHarmCategory, GeminiHarmBlockThreshold> mapSafetySettings = new HashMap<>();
        mapSafetySettings.put(HARM_CATEGORY_HATE_SPEECH, BLOCK_LOW_AND_ABOVE);
        mapSafetySettings.put(HARM_CATEGORY_HARASSMENT, BLOCK_LOW_AND_ABOVE);

        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .logRequestsAndResponses(true)
            .safetySettings(mapSafetySettings)
            .build();

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate("You're a dumb f*cking idiot bastard!", handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.finishReason()).isEqualTo(FinishReasonMapper.fromGFinishReasonToFinishReason(GeminiFinishReason.SAFETY));
    }

    @Test
    void should_comply_to_response_schema() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .logRequestsAndResponses(true)
            .responseFormat(ResponseFormat.builder()
                .type(JSON)
                .jsonSchema(JsonSchema.builder()
                    .rootElement(JsonObjectSchema.builder()
                        .addStringProperty("name")
                        .addProperty("address", JsonObjectSchema.builder()
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
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate(List.of(
            SystemMessage.from(
                "Your role is to extract information related to a person," +
                    "like their name, their address, the city the live in."),
            UserMessage.from(
                "In the town of Liverpool, lived Tommy Skybridge, a young little boy."
            )
        ), handler);
        Response<AiMessage> response = handler.get();

        System.out.println("response = " + response);

        // then
        assertThat(response.content().text().trim())
            .isEqualTo("{\"address\": {\"city\": \"Liverpool\"}, \"name\": \"Tommy Skybridge\"}");
    }

    @Test
    void should_handle_enum_type() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .logRequestsAndResponses(true)
            .responseFormat(ResponseFormat.builder()
                .type(JSON)
                .jsonSchema(JsonSchema.builder()
                    .rootElement(JsonObjectSchema.builder()
                        .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                            put("sentiment", JsonEnumSchema.builder()
                                .enumValues("POSITIVE", "NEGATIVE")
                                .build());
                        }})
                        .required("sentiment")
                        .additionalProperties(false)
                        .build())
                    .build())
                .build())
            .build();

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate(List.of(
            SystemMessage.from(
                "Your role is to analyze the sentiment of the text you receive."),
            UserMessage.from(
                "This is super exciting news, congratulations!"
            )
        ), handler);
        Response<AiMessage> response = handler.get();

        System.out.println("response = " + response);

        // then
        assertThat(response.content().text().trim())
            .isEqualTo("{\"sentiment\": \"POSITIVE\"}");
    }

    @Test
    void should_handle_enum_output_mode() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .logRequestsAndResponses(true)
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
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate(List.of(
            SystemMessage.from(
                "Your role is to analyze the sentiment of the text you receive."),
            UserMessage.from(
                "This is super exciting news, congratulations!"
            )
        ), handler);
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response.content().text().trim())
            .isEqualTo("POSITIVE");
    }

    @Test
    void should_allow_array_as_response_schema() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .logRequestsAndResponses(true)
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
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate(List.of(
            SystemMessage.from(
                "Your role is to return a list of 6-faces dice rolls"),
            UserMessage.from(
                "Give me 3 dice rolls"
            )
        ), handler);
        Response<AiMessage> response = handler.get();

        System.out.println("response = " + response);

        // then
        Integer[] diceRolls = new Gson().fromJson(response.content().text(), Integer[].class);
        assertThat(diceRolls.length).isEqualTo(3);
    }

    private class Color {
        private String name;
        private int red;
        private int green;
        private int blue;
        private boolean muted;
    }

    @Test
    void should_deserialize_to_POJO() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .logRequestsAndResponses(true)
            .responseFormat(ResponseFormat.builder()
                .type(JSON)
                .jsonSchema(JsonSchemas.jsonSchemaFrom(Color.class).get())
                .build())
            .build();

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        gemini.generate(List.of(
            SystemMessage.from(
                "Your role is to extract information from the description of a color"),
            UserMessage.from(
                "Cobalt blue is a blend of a lot of blue, a bit of green, and almost no red."
            )
        ), handler);
        Response<AiMessage> response = handler.get();

        System.out.println("response = " + response);

        Color color = new Gson().fromJson(response.content().text(), Color.class);

        // then
        assertThat(color.name).isEqualToIgnoringCase("Cobalt blue");
        assertThat(color.muted).isFalse();
        assertThat(color.red).isLessThanOrEqualTo(color.green);
        assertThat(color.green).isLessThanOrEqualTo(color.blue);
    }

    @Test
    void should_support_tool_config() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .logRequestsAndResponses(true)
            .build();

        List<ChatMessage> chatMessages = List.of(UserMessage.from("Call toolOne"));
        List<ToolSpecification> listOfTools = Arrays.asList(
            ToolSpecification.builder().name("toolOne").build(),
            ToolSpecification.builder().name("toolTwo").build()
        );

        // when
        TestStreamingResponseHandler<AiMessage> handler1 = new TestStreamingResponseHandler<>();
        gemini.generate(chatMessages, listOfTools, handler1);
        Response<AiMessage> response1 = handler1.get();

        // then
        assertThat(response1.content().hasToolExecutionRequests()).isTrue();
        assertThat(response1.content().toolExecutionRequests().get(0).name()).isEqualTo("toolOne");

        // given
        gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-flash")
            .logRequestsAndResponses(true)
            .toolConfig(GeminiMode.ANY, "toolTwo")
            .build();

        // when
        TestStreamingResponseHandler<AiMessage> handler2 = new TestStreamingResponseHandler<>();
        gemini.generate("Call toolOne", handler2);
        Response<AiMessage> response2 = handler2.get();

        // then
        assertThat(response2.content().hasToolExecutionRequests()).isFalse();
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


    interface StreamingAssistant {
        TokenStream chat(String userMessage);
    }

    @RetryingTest(10)
    void should_work_with_tools_with_AiServices() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(GOOGLE_AI_GEMINI_API_KEY)
            .modelName("gemini-1.5-pro")
            .logRequestsAndResponses(true)
            .timeout(Duration.ofMinutes(2))
            .temperature(0.0)
            .topP(0.0)
            .topK(1)
            .build();

        // when
        Transactions spyTransactions = spy(new Transactions());

        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
            .tools(spyTransactions)
            .chatMemory(chatMemory)
            .streamingChatLanguageModel(gemini)
            .build();

        // then
        CompletableFuture<Response<AiMessage>> future1 = new CompletableFuture<>();
        assistant.chat("What is the amount of transaction T001?")
            .onNext(System.out::println)
            .onComplete(future1::complete)
            .onError(future1::completeExceptionally)
            .start();
        Response<AiMessage> response1 = future1.get(30, TimeUnit.SECONDS);

        assertThat(response1.content().text()).containsIgnoringCase("11.1");
        verify(spyTransactions).getTransactionAmount("T001");

        CompletableFuture<Response<AiMessage>> future2 = new CompletableFuture<>();
        assistant.chat("What is the amount of transaction T002?")
            .onNext(System.out::println)
            .onComplete(future2::complete)
            .onError(future2::completeExceptionally)
            .start();
        Response<AiMessage> response2 = future2.get(30, TimeUnit.SECONDS);

        assertThat(response2.content().text()).containsIgnoringCase("22.2");
        verify(spyTransactions).getTransactionAmount("T002");

        verifyNoMoreInteractions(spyTransactions);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
