package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.googleai.FinishReasonMapper.fromGFinishReasonToFinishReason;
import static dev.langchain4j.model.googleai.GeminiFinishReason.*;
import static dev.langchain4j.model.googleai.GeminiHarmBlockThreshold.BLOCK_LOW_AND_ABOVE;
import static dev.langchain4j.model.googleai.GeminiHarmCategory.HARM_CATEGORY_HARASSMENT;
import static dev.langchain4j.model.googleai.GeminiHarmCategory.HARM_CATEGORY_HATE_SPEECH;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.output.JsonSchemas;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    void should_answer_simple_question() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .build();

        // when
        ChatResponse response = gemini.chat(ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of France?"))
                .build());

        // then
        String text = response.aiMessage().text();
        assertThat(text).containsIgnoringCase("Paris");

        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_configure_generation() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .temperature(2.0)
                .topP(0.5)
                .topK(10)
                .maxOutputTokens(10)
                .build();

        // when
        String response = gemini.chat("How much is 3+4? Reply with just the answer");

        // then
        assertThat(response.trim()).isEqualTo("7");
    }

    @Test
    void should_answer_in_json() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-pro")
                .responseFormat(ResponseFormat.JSON)
                .logRequestsAndResponses(true)
                .build();

        // when
        ChatResponse response = gemini.chat(UserMessage.from("What is the firstname of the John Doe?\n"
                + "Reply in JSON following with the following format: {\"firstname\": string}"));

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

    @Test
    void should_support_sending_images_as_base64() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .build();

        // when
        ChatResponse response = gemini.chat(UserMessage.from(
                //            ImageContent.fromToolExecReqToGFunCall(CAT_IMAGE_URL),
                ImageContent.from(new String(Base64.getEncoder().encode(readBytes(CAT_IMAGE_URL))), "image/png"),
                TextContent.from("Describe this image in a single word")));

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
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
                .modelName("gemini-1.5-flash")
                .build();

        // when
        ChatResponse response = gemini.chat(UserMessage.from(
                AudioContent.from(
                        new String(
                                Base64.getEncoder()
                                        .encode( // TODO use local file
                                                readBytes(
                                                        "https://storage.googleapis.com/cloud-samples-data/generative-ai/audio/pixel.mp3"))),
                        "audio/mp3"),
                TextContent.from("Give a summary of the audio")));

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Pixel");
    }

    @Test
    void should_support_video_file() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .build();

        // when
        ChatResponse response = gemini.chat(UserMessage.from(
                VideoContent.from(
                        new String(
                                Base64.getEncoder()
                                        .encode( // TODO use local file
                                                readBytes(
                                                        "https://www.sample-videos.com/video321/mp4/480/big_buck_bunny_480p_1mb.mp4"))),
                        "video/mp4"),
                TextContent.from("Give a summary of the video")));

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("rabbit");
    }

    @Test
    void should_respect_system_instruction() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .build();

        // when
        ChatResponse response =
                gemini.chat(SystemMessage.from("Translate from English into French"), UserMessage.from("apple"));

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("pomme");
    }

    @Test
    void should_execute_python_code() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .logRequestsAndResponses(true)
                .allowCodeExecution(true)
                .includeCodeExecutionOutput(true)
                .build();

        // when
        ChatResponse response = gemini.chat(singletonList(UserMessage.from(
                "Calculate `fibonacci(13)`. " + "Write code in Python and execute it to get the result.")));

        // then
        String text = response.aiMessage().text();
        System.out.println("text = " + text);

        assertThat(text).containsIgnoringCase("233");
    }

    @Test
    void should_request_to_call_function() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.0-flash")
                .temperature(0.0)
                .logRequestsAndResponses(true)
                .build();

        List<ChatMessage> allMessages = new ArrayList<>();
        allMessages.add(UserMessage.from("What is the weather forecast in Paris?"));

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("getWeatherForecast")
                .description("Get the weather forecast for a given city")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
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
        assertThat(response.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("getWeatherForecast");
        assertThat(response.aiMessage().toolExecutionRequests().get(0).arguments())
                .isEqualTo("{\"city\":\"Paris\"}");

        allMessages.add(response.aiMessage());

        ToolExecutionResultMessage forecastResult =
                ToolExecutionResultMessage.from(null, "getWeatherForecast", "{\"forecast\":\"sunny\"}");
        allMessages.add(forecastResult);

        // when
        response = gemini.chat(allMessages);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("sunny");
    }

    @Test
    void should_support_JSON_array_in_tools() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
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

    @Test
    void should_support_parallel_tool_execution() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.0-flash")
                .temperature(0.0)
                .logRequestsAndResponses(true)
                .build();

        // when
        List<ChatMessage> allMessages = new ArrayList<>();
        allMessages.add(UserMessage.from("Which warehouse has more stock, ABC or XYZ?"));

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("getWarehouseStock")
                .description("Retrieve the amount of stock available in a warehouse designated by its name")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("name", "The name of the warehouse")
                        .required("name")
                        .build())
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(allMessages)
                .toolSpecifications(toolSpecification)
                .build();

        ChatResponse response = gemini.chat(request);

        // then
        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();

        List<ToolExecutionRequest> executionRequests = response.aiMessage().toolExecutionRequests();
        assertThat(executionRequests).hasSize(2);

        String allArgs =
                executionRequests.stream().map(ToolExecutionRequest::arguments).collect(Collectors.joining(" "));
        assertThat(allArgs).contains("ABC");
        assertThat(allArgs).contains("XYZ");
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
                .logRequestsAndResponses(true)
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
        ChatResponse response = gemini.chat(ChatRequest.builder()
                .messages(
                        SystemMessage.from("Your role is to extract information related to a person,"
                                + "like their name, their address, the city the live in."),
                        UserMessage.from("In the town of Liverpool, lived Tommy Skybridge, a young little boy."))
                .build());

        System.out.println("response = " + response);

        // then
        assertThat(response.aiMessage().text().trim())
                .isEqualTo("{\"address\": {\"city\": \"Liverpool\"}, \"name\": \"Tommy Skybridge\"}");
    }

    @Test
    void should_handle_enum_type() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
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
        ChatResponse response = gemini.chat(ChatRequest.builder()
                .messages(
                        SystemMessage.from("Your role is to analyze the sentiment of the text you receive."),
                        UserMessage.from("This is super exciting news, congratulations!"))
                .build());

        System.out.println("response = " + response);

        // then
        assertThat(response.aiMessage().text().trim()).isEqualTo("{\"sentiment\": \"POSITIVE\"}");
    }

    @Test
    void should_handle_enum_output_mode() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
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
        ChatResponse response = gemini.chat(ChatRequest.builder()
                .messages(
                        SystemMessage.from("Your role is to return a list of 6-faces dice rolls"),
                        UserMessage.from("Give me 3 dice rolls"))
                .build());

        System.out.println("response = " + response);

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
                .modelName("gemini-1.5-flash")
                .logRequestsAndResponses(true)
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchemas.jsonSchemaFrom(Color.class).get())
                        .build())
                //             Equivalent to:
                //            .responseFormat(ResponseFormat.builder()
                //                .type(JSON)
                //                .jsonSchema(JsonSchema.builder()
                //                    .schema(JsonObjectSchema.builder()
                //                        .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                //                            put("name", JSON_STRING_SCHEMA);
                //                            put("red", JSON_INTEGER_SCHEMA);
                //                            put("green", JSON_INTEGER_SCHEMA);
                //                            put("blue", JSON_INTEGER_SCHEMA);
                //                            put("muted", JSON_BOOLEAN_SCHEMA);
                //                        }})
                //                        .required("name", "red", "green", "blue", "muted")
                //                        .additionalProperties(false)
                //                        .build())
                //                    .build())
                //                .build())
                .build();

        // when
        ChatResponse response = gemini.chat(ChatRequest.builder()
                .messages(
                        SystemMessage.from("Your role is to extract information from the description of a color"),
                        UserMessage.from("Cobalt blue is a blend of a lot of blue, a bit of green, and almost no red."))
                .build());

        System.out.println("response = " + response);

        Color color = new ObjectMapper().readValue(response.aiMessage().text(), Color.class);

        // then
        assertThat(color.name).isEqualToIgnoringCase("Cobalt blue");
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
                .logRequestsAndResponses(true)
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
                .logRequestsAndResponses(true)
                .toolConfig(GeminiMode.ANY, "toolTwo")
                .build();

        // when
        ChatResponse chatResponse = gemini.chat(
                ChatRequest.builder().messages(UserMessage.from("Call toolOne")).build());

        // then
        assertThat(chatResponse.aiMessage().hasToolExecutionRequests()).isFalse();
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

    interface Assistant {
        String chat(String userMessage);
    }

    @RetryingTest(10)
    void should_work_with_tools_with_AiServices() {
        // given
        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
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
        Assistant assistant = AiServices.builder(Assistant.class)
                .tools(spyTransactions)
                .chatMemory(chatMemory)
                .chatModel(gemini)
                .build();

        // then
        String response = "";

        response = assistant.chat("What is the amount of transaction T001?");
        assertThat(response).containsIgnoringCase("11.1");
        verify(spyTransactions).getTransactionAmount("T001");

        response = assistant.chat("What is the amount of transaction T002?");
        assertThat(response).containsIgnoringCase("22.2");
        verify(spyTransactions).getTransactionAmount("T002");

        verifyNoMoreInteractions(spyTransactions);
    }

    @Test
    void should_use_thinking_config() {

        // given
        GeminiThinkingConfig thinkingConfig = GeminiThinkingConfig.builder()
                .includeThoughts(true)
                .thinkingBudget(20)
                .build();

        GoogleAiGeminiChatModel gemini = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-2.5-flash-preview-04-17")
                .temperature(0.0)
                .logRequestsAndResponses(true)
                .thinkingConfig(thinkingConfig)
                .build();

        String prompt = "What is the area of a rectangle with length 5 and width 4?";
        ChatResponse response = gemini.chat(UserMessage.from(prompt));

        String reply = response.aiMessage().text();
        assertThat(reply).contains("20");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void should_handle_timeout(int millis) {

        // given
        Duration timeout = Duration.ofMillis(millis);

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName("gemini-1.5-flash")
                .logRequestsAndResponses(true)
                .maxRetries(0)
                .timeout(timeout)
                .build();

        // when
        assertThatThrownBy(() -> model.chat("hi"))
                .isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
