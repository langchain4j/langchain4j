package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.googleai.GeminiHarmBlockThreshold.BLOCK_LOW_AND_ABOVE;
import static dev.langchain4j.model.googleai.GeminiHarmCategory.HARM_CATEGORY_HARASSMENT;
import static dev.langchain4j.model.googleai.GeminiHarmCategory.HARM_CATEGORY_HATE_SPEECH;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.output.JsonSchemas;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.RetryingTest;

class GoogleAiGeminiStreamingChatModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    private static final String CAT_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";

    @Test
    void should_answer_simple_question() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of France?");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        String text = response.aiMessage().text();
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
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat("How much is 3+4? Reply with just the answer", handler);
        ChatResponse response = handler.get();

        // then
        String text = response.aiMessage().text();
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
        
        UserMessage userMessage = UserMessage.from("What is the firstname of the John Doe?\n"
                + "Reply in JSON following with the following format: {\"firstname\": string}");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        String jsonText = response.aiMessage().text();

        assertThat(jsonText).contains("\"firstname\"");
        assertThat(jsonText).contains("\"John\"");

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
                .modelName("gemini-1.5-flash")
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
    void should_support_sending_images_as_base64() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .build();

        UserMessage userMessage = UserMessage.userMessage(
                ImageContent.from(new String(Base64.getEncoder().encode(readBytes(CAT_IMAGE_URL))), "image/png"),
                TextContent.from("Describe this image in a single word"));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
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

    @Test
    void should_respect_system_instruction() {
        // given
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .build();

        List<ChatMessage> chatMessages =
                List.of(SystemMessage.from("Translate from English into French"), UserMessage.from("apple"));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(chatMessages, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("pomme");
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

        UserMessage userMessage = UserMessage.from("Calculate `fibonacci(13)`. Write code in Python and execute it to get the result.");
        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        String text = response.aiMessage().text();
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

        List<ChatMessage> allMessages = new ArrayList<>();
        allMessages.add(UserMessage.from("Return a JSON list containing the first 10 fibonacci numbers."));

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("getFirstNFibonacciNumbers")
                .description("Get the first n fibonacci numbers")
                .parameters(JsonObjectSchema.builder()
                        .addNumberProperty("n")
                        .build())
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(allMessages)
                .toolSpecifications(toolSpecification)
                .build();
        
        // when
        TestStreamingChatResponseHandler handler1 = new TestStreamingChatResponseHandler();
        gemini.chat(request, handler1);
        ChatResponse response1 = handler1.get();

        // then
        assertThat(response1.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response1.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("getFirstNFibonacciNumbers");
        assertThat(response1.aiMessage().toolExecutionRequests().get(0).arguments())
                .contains("\"n\":10");

        allMessages.add(response1.aiMessage());

        // when
        ToolExecutionResultMessage forecastResult =
                ToolExecutionResultMessage.from(null, "getFirstNFibonacciNumbers", "[0, 1, 1, 2, 3, 5, 8, 13, 21, 34]");
        allMessages.add(forecastResult);

        // then
        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        gemini.chat(allMessages, handler2);
        ChatResponse response2 = handler2.get();

        // then
        assertThat(response2.aiMessage().text()).contains("[0, 1, 1, 2, 3, 5, 8, 13, 21, 34]");
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

        List<ChatMessage> allMessages = new ArrayList<>();
        allMessages.add(UserMessage.from("Which warehouse has more stock, ABC or XYZ?"));

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("getWarehouseStock")
                .description("Retrieve the amount of stock available in a warehouse designated by its name")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("name", "The name of the warehouse")
                        .build())
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(allMessages)
                .toolSpecifications(toolSpecification)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(request, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();

        List<ToolExecutionRequest> executionRequests = response.aiMessage().toolExecutionRequests();
        assertThat(executionRequests).hasSize(2);

        String allArgs =
                executionRequests.stream().map(ToolExecutionRequest::arguments).collect(Collectors.joining(" "));
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
                .modelName("gemini-1.5-flash")
                .logRequestsAndResponses(true)
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
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(
                List.of(
                        SystemMessage.from("Your role is to analyze the sentiment of the text you receive."),
                        UserMessage.from("This is super exciting news, congratulations!")),
                handler);
        ChatResponse response = handler.get();

        System.out.println("response = " + response);

        // then
        assertThat(response.aiMessage().text().trim()).isEqualTo("{\"sentiment\": \"POSITIVE\"}");
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
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        gemini.chat(
                List.of(
                        SystemMessage.from("Your role is to return a list of 6-faces dice rolls"),
                        UserMessage.from("Give me 3 dice rolls")),
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
                .logRequestsAndResponses(true)
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
        GoogleAiGeminiStreamingChatModel gemini = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .logRequestsAndResponses(true)
                .build();

        List<ChatMessage> chatMessages = List.of(UserMessage.from("Call toolOne"));
        List<ToolSpecification> listOfTools = Arrays.asList(
                ToolSpecification.builder().name("toolOne").build(),
                ToolSpecification.builder().name("toolTwo").build());

        ChatRequest request = ChatRequest.builder()
                .messages(chatMessages)
                .toolSpecifications(listOfTools)
                .build();

        // when
        TestStreamingChatResponseHandler handler1 = new TestStreamingChatResponseHandler();
        gemini.chat(request, handler1);
        ChatResponse response1 = handler1.get();

        // then
        assertThat(response1.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response1.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("toolOne");

        // given
        gemini = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .logRequestsAndResponses(true)
                .toolConfig(GeminiMode.ANY, "toolTwo")
                .build();

        // when
        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        gemini.chat("Call toolOne", handler2);
        ChatResponse response2 = handler2.get();

        // then
        assertThat(response2.aiMessage().hasToolExecutionRequests()).isFalse();
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
                .streamingChatModel(gemini)
                .build();

        // then
        CompletableFuture<ChatResponse> future1 = new CompletableFuture<>();
        assistant
                .chat("What is the amount of transaction T001?")
                .onPartialResponse(System.out::println)
                .onCompleteResponse(future1::complete)
                .onError(future1::completeExceptionally)
                .start();
        ChatResponse response1 = future1.get(30, TimeUnit.SECONDS);

        assertThat(response1.aiMessage().text()).containsIgnoringCase("11.1");
        verify(spyTransactions).getTransactionAmount("T001");

        CompletableFuture<ChatResponse> future2 = new CompletableFuture<>();
        assistant
                .chat("What is the amount of transaction T002?")
                .onPartialResponse(System.out::println)
                .onCompleteResponse(future2::complete)
                .onError(future2::completeExceptionally)
                .start();
        ChatResponse response2 = future2.get(30, TimeUnit.SECONDS);

        assertThat(response2.aiMessage().text()).containsIgnoringCase("22.2");
        verify(spyTransactions).getTransactionAmount("T002");

        verifyNoMoreInteractions(spyTransactions);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void should_handle_timeout(int millis) throws Exception {

        // given
        Duration timeout = Duration.ofMillis(millis);

        GoogleAiGeminiStreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .logRequestsAndResponses(true)
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

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
