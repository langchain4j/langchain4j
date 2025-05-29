package dev.langchain4j.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.StreamingAiServicesWithToolsIT.TemperatureUnit.CELSIUS;
import static dev.langchain4j.service.StreamingAiServicesWithToolsIT.TransactionService.EXPECTED_SPECIFICATION;
import static dev.langchain4j.service.StreamingAiServicesWithToolsIT.WeatherService.TEMPERATURE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class StreamingAiServicesWithToolsIT {

    static Stream<StreamingChatModel> models() {
        return Stream.of(
                OpenAiStreamingChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build()
        );
    }

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    static class TransactionService {

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("getTransactionAmount")
                .description("returns amount of a given transaction")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("arg0", "ID of a transaction")
                        .required("arg0")
                        .build())
                .build();

        @Tool("returns amount of a given transaction")
        Double getTransactionAmount(@P("ID of a transaction") String id) {
            System.out.printf("called getTransactionAmount(%s)%n", id);
            return switch (id) {
                case "T001" -> 11.1;
                case "T002" -> 22.2;
                default -> throw new IllegalArgumentException("Unknown transaction ID: " + id);
            };
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_a_tool_then_answer(StreamingChatModel model) throws Exception {

        // given
        TransactionService transactionService = spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        StreamingChatModel spyModel = spy(model);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyModel)
                .chatMemory(chatMemory)
                .tools(transactionService)
                .build();

        String userMessage = "What is the amounts of transaction T001?";

        // when
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        assistant.chat(userMessage)
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();
        ChatResponse response = futureResponse.get(60, SECONDS);

        // then
        assertThat(response.aiMessage().text()).contains("11.1");

        // then
        verify(transactionService).getTransactionAmount("T001");
        verifyNoMoreInteractions(transactionService);

        // then
        List<ChatMessage> messages = chatMemory.messages();
        verify(spyModel).chat(
                eq(
                        ChatRequest.builder()
                                .messages(messages.get(0))
                                .toolSpecifications(EXPECTED_SPECIFICATION)
                                .build()
                ),
                any()
        );
        verify(spyModel).chat(
                eq(
                        ChatRequest.builder()
                                .messages(messages.get(0), messages.get(1), messages.get(2))
                                .toolSpecifications(EXPECTED_SPECIFICATION)
                                .build()
                ),
                any()
        );
    }

    static class WeatherService {

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("currentTemperature")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("arg0")
                        .addEnumProperty("arg1", List.of("CELSIUS", "fahrenheit", "Kelvin"))
                        .required("arg0", "arg1")
                        .build())
                .build();

        static final int TEMPERATURE = 19;

        @Tool
        int currentTemperature(String city, TemperatureUnit unit) {
            System.out.printf("called currentTemperature(%s, %s)%n", city, unit);
            return TEMPERATURE;
        }
    }

    enum TemperatureUnit {
        CELSIUS, fahrenheit, Kelvin
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_tool_with_enum_parameter(StreamingChatModel model) throws Exception {

        // given
        WeatherService weatherService = spy(new WeatherService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        StreamingChatModel spyModel = spy(model);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyModel)
                .chatMemory(chatMemory)
                .tools(weatherService)
                .build();

        String userMessage = "What is the temperature in Munich now, in Celsius?";

        // when
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant.chat(userMessage)
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();
        ChatResponse response = future.get(60, SECONDS);

        // then
        assertThat(response.aiMessage().text()).contains(String.valueOf(TEMPERATURE));

        verify(weatherService).currentTemperature("Munich", CELSIUS);
        verifyNoMoreInteractions(weatherService);

        List<ChatMessage> messages = chatMemory.messages();
        verify(spyModel).chat(
                eq(
                        ChatRequest.builder()
                                .messages(messages.get(0))
                                .toolSpecifications(WeatherService.EXPECTED_SPECIFICATION)
                                .build()
                ),
                any()
        );
        verify(spyModel).chat(
                eq(
                        ChatRequest.builder()
                                .messages(messages.get(0), messages.get(1), messages.get(2))
                                .toolSpecifications(WeatherService.EXPECTED_SPECIFICATION)
                                .build()
                ),
                any()
        );
    }

    @Test
    void should_use_tool_provider() throws Exception {

        // given
        ToolExecutor toolExecutor = spy(new TransactionServiceExecutor());

        ToolProvider toolProvider = (toolProviderRequest) -> ToolProviderResult.builder()
                .add(EXPECTED_SPECIFICATION, toolExecutor)
                .build();

        StreamingChatModel spyModel = spy(models().findFirst().get());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyModel)
                .chatMemory(chatMemory)
                .toolProvider(toolProvider)
                .build();

        String userMessage = "What is the amounts of transactions T001?";

        // when
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant.chat(userMessage)
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();
        ChatResponse response = future.get(60, SECONDS);

        // then
        assertThat(response.aiMessage().text()).contains("11.1");

        // then
        verify(toolExecutor).execute(any(), any());
        verifyNoMoreInteractions(toolExecutor);

        // then
        List<ChatMessage> messages = chatMemory.messages();
        verify(spyModel).chat(
                eq(
                        ChatRequest.builder()
                                .messages(messages.get(0))
                                .toolSpecifications(EXPECTED_SPECIFICATION)
                                .build()
                ),
                any()
        );
        verify(spyModel).chat(
                eq(
                        ChatRequest.builder()
                                .messages(messages.get(0), messages.get(1), messages.get(2))
                                .toolSpecifications(EXPECTED_SPECIFICATION)
                                .build()
                ),
                any()
        );
        verifyNoMoreInteractionsFor(spyModel);
    }

    static class TransactionServiceExecutor implements ToolExecutor {

        private final TransactionService transactionService = new TransactionService();

        @Override
        public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {

            Map<String, Object> arguments = toMap(toolExecutionRequest.arguments());
            String transactionId = arguments.get("arg0").toString();

            Double transactionAmount = transactionService.getTransactionAmount(transactionId);

            return transactionAmount.toString();
        }
    }

    private static Map<String, Object> toMap(String arguments) {
        try {
            return new ObjectMapper().readValue(arguments, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void should_invoke_tool_execution_handler() throws Exception {

        // given
        WeatherService weatherService = spy(new WeatherService());

        StreamingChatModel spyModel = spy(models().findFirst().get());

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(weatherService)
                .build();

        String userMessage = "What is the temperature in Munich and London, in Celsius?";

        List<ToolExecution> toolExecutions = new ArrayList<>();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        // when
        assistant.chat(userMessage)
                .onPartialResponse(ignored -> {})
                .onToolExecuted(toolExecutions::add)
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();
        ChatResponse response = future.get(60, SECONDS);

        // then
        assertThat(response.aiMessage().text()).contains(String.valueOf(WeatherService.TEMPERATURE));

        // then
        verify(weatherService).currentTemperature("Munich", CELSIUS);
        verify(weatherService).currentTemperature("London", CELSIUS);
        verifyNoMoreInteractions(weatherService);

        // then
        assertThat(toolExecutions).hasSize(2);

        assertThat(toolExecutions.get(0).request().name()).isEqualTo("currentTemperature");
        assertThat(toolExecutions.get(0).request().arguments()).isEqualToIgnoringWhitespace("{\"arg0\":\"Munich\", \"arg1\": \"CELSIUS\"}");
        assertThat(toolExecutions.get(0).result()).isEqualTo(String.valueOf(WeatherService.TEMPERATURE));

        assertThat(toolExecutions.get(1).request().name()).isEqualTo("currentTemperature");
        assertThat(toolExecutions.get(1).request().arguments()).isEqualToIgnoringWhitespace("{\"arg0\":\"London\", \"arg1\":\"CELSIUS\"}");
        assertThat(toolExecutions.get(1).result()).isEqualTo(String.valueOf(WeatherService.TEMPERATURE));
    }

    public static void verifyNoMoreInteractionsFor(StreamingChatModel model) {
        try {
            verify(model, atLeastOnce()).doChat(any(), any());
        } catch (Throwable ignored) {
            // don't care if it was called or not
        }
        try {
            verify(model, atLeastOnce()).defaultRequestParameters();
        } catch (Throwable ignored) {
            // don't care if it was called or not
        }
        try {
            verify(model, atLeastOnce()).supportedCapabilities();
        } catch (Throwable ignored) {
            // don't care if it was called or not
        }
        try {
            verify(model, atLeastOnce()).listeners();
        } catch (Throwable ignored) {
            // don't care if it was called or not
        }
        try {
            verify(model, atLeastOnce()).provider();
        } catch (Throwable ignored) {
            // don't care if it was called or not
        }
        verifyNoMoreInteractions(model);
    }

    // TODO all other tests from sync version
}
