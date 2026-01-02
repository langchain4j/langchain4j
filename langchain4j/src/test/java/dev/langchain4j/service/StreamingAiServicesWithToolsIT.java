package dev.langchain4j.service;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.StreamingAiServicesWithToolsIT.TemperatureUnit.CELSIUS;
import static dev.langchain4j.service.StreamingAiServicesWithToolsIT.TransactionService.EXPECTED_SPECIFICATION;
import static dev.langchain4j.service.StreamingAiServicesWithToolsIT.WeatherService.TEMPERATURE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class StreamingAiServicesWithToolsIT {

    Logger log = LoggerFactory.getLogger(StreamingAiServicesWithToolsIT.class);

    static Stream<StreamingChatModel> models() {
        return Stream.of(OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());
    }

    static Stream<StreamingChatModel> modelsWithoutParallelToolCalling() {
        return Stream.of(OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .parallelToolCalls(false) // to force the model to call tools sequentially
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());
    }

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    static class TransactionService {

        final Queue<Thread> threads = new ConcurrentLinkedQueue<>();

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
            threads.add(Thread.currentThread());
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

        TestTokenStreamHandler handler = spy(new TestTokenStreamHandler());
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        assistant
                .chat(userMessage)
                .onPartialResponse(handler::onPartialResponse)
                .onPartialThinking(handler::onPartialThinking)
                .onIntermediateResponse(handler::onIntermediateResponse)
                .beforeToolExecution(handler::beforeToolExecution)
                .onToolExecuted(handler::onToolExecuted)
                .onCompleteResponse(completeResponse -> {
                    handler.onCompleteResponse(completeResponse);
                    futureResponse.complete(completeResponse);
                })
                .onError(error -> {
                    handler.onError(error);
                    futureResponse.completeExceptionally(error);
                })
                .start();
        ChatResponse response = futureResponse.get(60, SECONDS);

        // then
        assertThat(response.aiMessage().text()).contains("11.1");

        // then
        verify(transactionService).getTransactionAmount("T001");
        verifyNoMoreInteractions(transactionService);

        // then
        assertThat(handler.allThreads).hasSize(1);
        assertThat(handler.allThreads.iterator().next()).isNotEqualTo(Thread.currentThread());
        assertThat(transactionService.threads).hasSize(1);
        assertThat(transactionService.threads.poll())
                .isEqualTo(handler.allThreads.iterator().next());

        // then
        List<ChatMessage> messages = chatMemory.messages();
        verify(spyModel)
                .chat(
                        eq(ChatRequest.builder()
                                .messages(messages.get(0))
                                .toolSpecifications(EXPECTED_SPECIFICATION)
                                .build()),
                        any());
        verify(spyModel)
                .chat(
                        eq(ChatRequest.builder()
                                .messages(messages.get(0), messages.get(1), messages.get(2))
                                .toolSpecifications(EXPECTED_SPECIFICATION)
                                .build()),
                        any());
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("executors")
    void should_execute_multiple_tools_in_parallel_concurrently_then_answer(Executor executor) throws Exception {

        // given
        class Tools {

            static final String CURRENT_TIME = "16:28";
            static final String CURRENT_TEMPERATURE = "17";

            final Queue<Thread> getCurrentTimeThreads = new ConcurrentLinkedQueue<>();
            final Queue<Thread> getCurrentTemperatureThreads = new ConcurrentLinkedQueue<>();

            final CountDownLatch latch = new CountDownLatch(2);

            @Tool
            String getCurrentTime(String city) throws InterruptedException {
                getCurrentTimeThreads.add(Thread.currentThread());
                latch.countDown();
                latch.await(); // to make sure both tools overlap in time and are executed in different threads
                return CURRENT_TIME;
            }

            @Tool
            String getCurrentTemperature(String city) throws InterruptedException {
                getCurrentTemperatureThreads.add(Thread.currentThread());
                latch.countDown();
                latch.await(); // to make sure both tools overlap in time and are executed in different threads
                return CURRENT_TEMPERATURE;
            }
        }

        Tools spyTools = spy(new Tools());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(models().findFirst().get())
                .chatMemory(chatMemory)
                .tools(spyTools)
                .executeToolsConcurrently(executor)
                .build();

        String userMessage = "What is the current time and temperature in Munich?";

        TestTokenStreamHandler handler = spy(new TestTokenStreamHandler());
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        assistant
                .chat(userMessage)
                .onPartialResponse(handler::onPartialResponse)
                .onPartialThinking(handler::onPartialThinking)
                .onIntermediateResponse(handler::onIntermediateResponse)
                .beforeToolExecution(handler::beforeToolExecution)
                .onToolExecuted(handler::onToolExecuted)
                .onCompleteResponse(completeResponse -> {
                    handler.onCompleteResponse(completeResponse);
                    futureResponse.complete(completeResponse);
                })
                .onError(error -> {
                    handler.onError(error);
                    futureResponse.completeExceptionally(error);
                })
                .start();
        ChatResponse response = futureResponse.get(60, SECONDS);

        // then
        assertThat(response.aiMessage().text()).contains(Tools.CURRENT_TIME, Tools.CURRENT_TEMPERATURE);

        // then
        InOrder inOrder = inOrder(handler, spyTools);
        inOrder.verify(handler)
                .beforeToolExecution(argThat(bte -> bte.request().name().equals("getCurrentTime")));
        inOrder.verify(spyTools).getCurrentTime("Munich");
        inOrder.verify(handler).onToolExecuted(argThat(te -> te.request().name().equals("getCurrentTime")));

        InOrder inOrder2 = inOrder(handler, spyTools);
        inOrder2.verify(handler)
                .beforeToolExecution(argThat(bte -> bte.request().name().equals("getCurrentTemperature")));
        inOrder2.verify(spyTools).getCurrentTemperature("Munich");
        inOrder2.verify(handler)
                .onToolExecuted(argThat(te -> te.request().name().equals("getCurrentTemperature")));

        // then
        log.info(
                "should_execute_multiple_tools_in_parallel_concurrently_then_answer({}) allThreadsByMethod: {}",
                executor,
                handler.allThreadsByMethod);
        log.info(
                "should_execute_multiple_tools_in_parallel_concurrently_then_answer({}) beforeToolExecutionThreads: {}",
                executor,
                handler.beforeToolExecutionThreads);
        log.info(
                "should_execute_multiple_tools_in_parallel_concurrently_then_answer({}) onToolExecutedThreads: {}",
                executor,
                handler.onToolExecutedThreads);
        assertThat(handler.allThreads).hasSizeBetween(3, 4); // 1-2 for handler, 2 for tools
        // default JDK HttpClient executor can allocate different threads for the first and second streaming response

        assertThat(handler.beforeToolExecutionThreads).hasSize(2);
        assertThat(handler.beforeToolExecutionThreads.get("getCurrentTime")).hasSize(1);
        assertThat(handler.beforeToolExecutionThreads.get("getCurrentTemperature"))
                .hasSize(1);

        assertThat(handler.onToolExecutedThreads).hasSize(2);
        assertThat(handler.onToolExecutedThreads.get("getCurrentTime")).hasSize(1);
        assertThat(handler.onToolExecutedThreads.get("getCurrentTemperature")).hasSize(1);

        assertThat(spyTools.getCurrentTimeThreads).hasSize(1);
        Thread getCurrentTimeThread = spyTools.getCurrentTimeThreads.poll();
        assertThat(getCurrentTimeThread)
                .isEqualTo(handler.beforeToolExecutionThreads
                        .get("getCurrentTime")
                        .iterator()
                        .next());
        assertThat(getCurrentTimeThread)
                .isEqualTo(handler.onToolExecutedThreads
                        .get("getCurrentTime")
                        .iterator()
                        .next());

        assertThat(spyTools.getCurrentTemperatureThreads).hasSize(1);
        Thread getCurrentTemperatureThread = spyTools.getCurrentTemperatureThreads.poll();
        assertThat(getCurrentTemperatureThread)
                .isEqualTo(handler.beforeToolExecutionThreads
                        .get("getCurrentTemperature")
                        .iterator()
                        .next());
        assertThat(getCurrentTemperatureThread)
                .isEqualTo(handler.onToolExecutedThreads
                        .get("getCurrentTemperature")
                        .iterator()
                        .next());

        assertThat(getCurrentTimeThread).isNotEqualTo(getCurrentTemperatureThread);
    }

    static List<Executor> executors() {
        return List.of(Executors.newFixedThreadPool(2));
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("executors")
    void should_execute_single_tool_concurrently(Executor executor) throws Exception {

        // given
        class Tools {

            static final String CURRENT_TEMPERATURE = "17";

            final Queue<Thread> getCurrentTemperatureThreads = new ConcurrentLinkedQueue<>();

            @Tool
            String getCurrentTemperature(String city) {
                getCurrentTemperatureThreads.add(Thread.currentThread());
                return CURRENT_TEMPERATURE;
            }
        }

        Tools spyTools = spy(new Tools());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(models().findFirst().get())
                .chatMemory(chatMemory)
                .tools(spyTools)
                .executeToolsConcurrently(executor)
                .build();

        String userMessage = "What is the current temperature in Munich?";

        TestTokenStreamHandler handler = spy(new TestTokenStreamHandler());
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        assistant
                .chat(userMessage)
                .onPartialResponse(handler::onPartialResponse)
                .onPartialThinking(handler::onPartialThinking)
                .onIntermediateResponse(handler::onIntermediateResponse)
                .beforeToolExecution(handler::beforeToolExecution)
                .onToolExecuted(handler::onToolExecuted)
                .onCompleteResponse(completeResponse -> {
                    handler.onCompleteResponse(completeResponse);
                    futureResponse.complete(completeResponse);
                })
                .onError(error -> {
                    handler.onError(error);
                    futureResponse.completeExceptionally(error);
                })
                .start();
        ChatResponse response = futureResponse.get(60, SECONDS);

        // then
        assertThat(response.aiMessage().text()).contains(Tools.CURRENT_TEMPERATURE);

        // then
        verify(spyTools).getCurrentTemperature("Munich");
        verifyNoMoreInteractions(spyTools);

        // then
        assertThat(handler.allThreads).hasSize(2); // 1 for handler, 1 for tool

        assertThat(handler.beforeToolExecutionThreads).hasSize(1);
        assertThat(handler.beforeToolExecutionThreads.get("getCurrentTemperature"))
                .hasSize(1);

        assertThat(handler.onToolExecutedThreads).hasSize(1);
        assertThat(handler.onToolExecutedThreads.get("getCurrentTemperature")).hasSize(1);

        assertThat(spyTools.getCurrentTemperatureThreads).hasSize(1);
        Thread thread = spyTools.getCurrentTemperatureThreads.poll();
        assertThat(thread)
                .isEqualTo(handler.beforeToolExecutionThreads
                        .get("getCurrentTemperature")
                        .iterator()
                        .next());
        assertThat(thread)
                .isEqualTo(handler.onToolExecutedThreads
                        .get("getCurrentTemperature")
                        .iterator()
                        .next());
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
        CELSIUS,
        fahrenheit,
        Kelvin
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
        assistant
                .chat(userMessage)
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
        verify(spyModel)
                .chat(
                        eq(ChatRequest.builder()
                                .messages(messages.get(0))
                                .toolSpecifications(WeatherService.EXPECTED_SPECIFICATION)
                                .build()),
                        any());
        verify(spyModel)
                .chat(
                        eq(ChatRequest.builder()
                                .messages(messages.get(0), messages.get(1), messages.get(2))
                                .toolSpecifications(WeatherService.EXPECTED_SPECIFICATION)
                                .build()),
                        any());
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
        assistant
                .chat(userMessage)
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();
        ChatResponse response = future.get(60, SECONDS);

        // then
        assertThat(response.aiMessage().text()).contains("11.1");

        // then
        verify(toolExecutor).executeWithContext(any(), any(InvocationContext.class));
        verify(toolExecutor).execute(any(), any(Object.class));
        verifyNoMoreInteractions(toolExecutor);

        // then
        List<ChatMessage> messages = chatMemory.messages();
        verify(spyModel)
                .chat(
                        eq(ChatRequest.builder()
                                .messages(messages.get(0))
                                .toolSpecifications(EXPECTED_SPECIFICATION)
                                .build()),
                        any());
        verify(spyModel)
                .chat(
                        eq(ChatRequest.builder()
                                .messages(messages.get(0), messages.get(1), messages.get(2))
                                .toolSpecifications(EXPECTED_SPECIFICATION)
                                .build()),
                        any());
        verifyNoMoreInteractionsFor(spyModel);
    }

    static class TransactionServiceExecutor implements ToolExecutor {

        private final TransactionService transactionService = new TransactionService();

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {

            Map<String, Object> arguments = toMap(request.arguments());
            String transactionId = arguments.get("arg0").toString();

            Double transactionAmount = transactionService.getTransactionAmount(transactionId);

            return transactionAmount.toString();
        }
    }

    private static Map<String, Object> toMap(String arguments) {
        try {
            return new ObjectMapper().readValue(arguments, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void should_invoke_tool_before_execution_handler() throws Exception {

        // given
        WeatherService weatherService = spy(new WeatherService());

        StreamingChatModel spyModel = spy(models().findFirst().get());

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(weatherService)
                .build();

        String userMessage = "What is the temperature in Munich and London, in Celsius?";

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        assistant
                .chat(userMessage)
                .onPartialResponse(handler::onPartialResponse)
                .beforeToolExecution(handler::beforeToolExecution)
                .onToolExecuted(handler::onToolExecuted)
                .onError(error -> {
                    handler.onError(error);
                    futureResponse.completeExceptionally(error);
                })
                .onCompleteResponse(completeResponse -> {
                    handler.onCompleteResponse(completeResponse);
                    futureResponse.complete(completeResponse);
                })
                .start();

        // then
        ChatResponse response = futureResponse.get(60, SECONDS);

        // then
        assertThat(response.aiMessage().text()).contains(String.valueOf(WeatherService.TEMPERATURE));

        // then
        verify(weatherService).currentTemperature("Munich", CELSIUS);
        verify(weatherService).currentTemperature("London", CELSIUS);
        verifyNoMoreInteractions(weatherService);

        // then
        InOrder inOrder = inOrder(handler);

        inOrder.verify(handler)
                .beforeToolExecution(argThat(bfe -> bfe.request().arguments().contains("Munich")));
        inOrder.verify(handler)
                .onToolExecuted(argThat(
                        toolExecution -> toolExecution.request().arguments().contains("Munich")));
        inOrder.verify(handler)
                .beforeToolExecution(argThat(bfe -> bfe.request().arguments().contains("London")));
        inOrder.verify(handler)
                .onToolExecuted(argThat(
                        toolExecution -> toolExecution.request().arguments().contains("London")));

        inOrder.verify(handler, atLeastOnce()).onPartialResponse(any());
        inOrder.verify(handler).onCompleteResponse(any());

        inOrder.verifyNoMoreInteractions();
        verifyNoMoreInteractions(handler);
    }

    @Test
    void should_invoke_partial_tool_call_handler() throws Exception {

        // given
        WeatherService weatherService = spy(new WeatherService());

        StreamingChatModel spyModel = spy(models().findFirst().get());

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(spyModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(weatherService)
                .build();

        String userMessage = "What is the temperature in Munich, in Celsius?";

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        assistant
                .chat(userMessage)
                .onPartialResponse(handler::onPartialResponse)
                .onPartialToolCall(handler::onPartialToolCall)
                .onError(error -> {
                    handler.onError(error);
                    futureResponse.completeExceptionally(error);
                })
                .onCompleteResponse(completeResponse -> {
                    handler.onCompleteResponse(completeResponse);
                    futureResponse.complete(completeResponse);
                })
                .start();

        // then
        ChatResponse response = futureResponse.get(60, SECONDS);

        // then
        assertThat(response.aiMessage().text()).contains(String.valueOf(WeatherService.TEMPERATURE));

        // then
        verify(weatherService).currentTemperature("Munich", CELSIUS);
        verifyNoMoreInteractions(weatherService);

        // then - verify onPartialToolCall was invoked
        verify(handler, atLeastOnce()).onPartialToolCall(any());
        assertThat(handler.onPartialToolCallThreads).containsKey("currentTemperature");

        // then - verify callback order
        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler, atLeastOnce()).onPartialToolCall(any());
        inOrder.verify(handler, atLeastOnce()).onPartialResponse(any());
        inOrder.verify(handler).onCompleteResponse(any());

        inOrder.verifyNoMoreInteractions();
        verifyNoMoreInteractions(handler);
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
        assistant
                .chat(userMessage)
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
        assertThat(toolExecutions.get(0).request().arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\":\"Munich\", \"arg1\": \"CELSIUS\"}");
        assertThat(toolExecutions.get(0).result()).isEqualTo(String.valueOf(WeatherService.TEMPERATURE));
        assertThat(toolExecutions.get(0).resultObject()).isEqualTo(WeatherService.TEMPERATURE);

        assertThat(toolExecutions.get(1).request().name()).isEqualTo("currentTemperature");
        assertThat(toolExecutions.get(1).request().arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\":\"London\", \"arg1\":\"CELSIUS\"}");
        assertThat(toolExecutions.get(1).result()).isEqualTo(String.valueOf(WeatherService.TEMPERATURE));
    }

    // Error Handling: Tool Error

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_propagate_error_message_thrown_from_tool_to_LLM_by_default(boolean executeToolsConcurrently)
            throws Exception {

        // given
        String errorMessage = "Weather service is unavailable";

        class FailingTool {

            @Tool
            String getWeather(String ignored) {
                throw new RuntimeException(errorMessage);
            }
        }

        StreamingChatModel spyModel = spy(models().findFirst().get());

        FailingTool spyTool = spy(new FailingTool());

        AiServices<Assistant> assistantBuilder =
                AiServices.builder(Assistant.class).streamingChatModel(spyModel).tools(spyTool);
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        assistant
                .chat("What is the weather in Munich?")
                .onPartialResponse(handler::onPartialResponse)
                .beforeToolExecution(handler::beforeToolExecution)
                .onToolExecuted(handler::onToolExecuted)
                .onCompleteResponse(completeResponse -> {
                    handler.onCompleteResponse(completeResponse);
                    futureResponse.complete(completeResponse);
                })
                .onError(error -> {
                    handler.onError(error);
                    futureResponse.completeExceptionally(error);
                })
                .start();

        futureResponse.get(30, SECONDS);

        // then
        verify(spyTool).getWeather("Munich");
        verifyNoMoreInteractions(spyTool);

        verify(spyModel)
                .chat(
                        argThat((ChatRequest chatRequest) ->
                                chatRequest.messages().size() == 1),
                        any());
        verify(spyModel)
                .chat(
                        argThat((ChatRequest chatRequest) ->
                                chatRequest.messages().size() == 3
                                        && chatRequest.messages().get(2)
                                                instanceof ToolExecutionResultMessage toolResult
                                        && toolResult.text().equals(errorMessage)),
                        any());
        verifyNoMoreInteractionsFor(spyModel);

        verify(handler).beforeToolExecution(any());
        verify(handler)
                .onToolExecuted(argThat(toolExecution -> toolExecution.result().equals(errorMessage)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_propagate_exception_type_to_LLM_when_exception_without_message_is_thrown_from_tool(
            boolean executeToolsConcurrently) throws Exception {

        // given
        RuntimeException exceptionWithoutMessage = new RuntimeException();

        class FailingTool {

            @Tool
            String getWeather(String ignored) {
                throw exceptionWithoutMessage;
            }
        }

        StreamingChatModel spyModel = spy(models().findFirst().get());

        FailingTool spyTool = spy(new FailingTool());

        AiServices<Assistant> assistantBuilder =
                AiServices.builder(Assistant.class).streamingChatModel(spyModel).tools(spyTool);
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        assistant
                .chat("What is the weather in Munich? Do not retry in case of errors.")
                .onPartialResponse(handler::onPartialResponse)
                .beforeToolExecution(handler::beforeToolExecution)
                .onToolExecuted(handler::onToolExecuted)
                .onCompleteResponse(completeResponse -> {
                    handler.onCompleteResponse(completeResponse);
                    futureResponse.complete(completeResponse);
                })
                .onError(e -> {
                    handler.onError(e);
                    futureResponse.completeExceptionally(e);
                })
                .start();

        futureResponse.get(30, SECONDS);

        // then
        verify(spyTool).getWeather("Munich");
        verifyNoMoreInteractions(spyTool);

        verify(spyModel)
                .chat(
                        argThat((ChatRequest chatRequest) ->
                                chatRequest.messages().size() == 1),
                        any());
        verify(spyModel)
                .chat(
                        argThat((ChatRequest chatRequest) ->
                                chatRequest.messages().size() == 3
                                        && chatRequest.messages().get(2)
                                                instanceof ToolExecutionResultMessage toolResult
                                        && toolResult.text().equals("java.lang.RuntimeException")),
                        any());
        verifyNoMoreInteractionsFor(spyModel);

        verify(handler).beforeToolExecution(any());
        verify(handler)
                .onToolExecuted(argThat(toolExecution -> toolExecution.result().equals("java.lang.RuntimeException")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_customize_error_returned_from_tool_before_sending_to_LLM(boolean executeToolsConcurrently)
            throws Exception {

        // given
        RuntimeException toolError = new RuntimeException("Can't connect to the reliable-weather.com");

        class FailingTool {

            @Tool
            String getWeather(String ignored) {
                throw toolError;
            }
        }

        String customizedErrorMessage = "Weather service is unavailable";

        ToolExecutionErrorHandler toolExecutionErrorHandler = (error, context) -> {
            assertThat(error).isSameAs(toolError);

            assertThat(context.toolExecutionRequest().name()).isEqualTo("getWeather");
            assertThat(context.toolExecutionRequest().arguments()).contains("Munich");
            assertThat(context.memoryId()).isEqualTo("default");

            return ToolErrorHandlerResult.text(customizedErrorMessage);
        };

        StreamingChatModel spyModel = spy(models().findFirst().get());

        FailingTool spyTool = spy(new FailingTool());

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .streamingChatModel(spyModel)
                .tools(spyTool)
                .toolExecutionErrorHandler(toolExecutionErrorHandler);
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        assistant
                .chat("What is the weather in Munich?")
                .onPartialResponse(handler::onPartialResponse)
                .beforeToolExecution(handler::beforeToolExecution)
                .onToolExecuted(handler::onToolExecuted)
                .onCompleteResponse(completeResponse -> {
                    handler.onCompleteResponse(completeResponse);
                    futureResponse.complete(completeResponse);
                })
                .onError(error -> {
                    handler.onError(error);
                    futureResponse.completeExceptionally(error);
                })
                .start();

        futureResponse.get(30, SECONDS);

        // then
        verify(spyTool).getWeather("Munich");
        verifyNoMoreInteractions(spyTool);

        verify(spyModel)
                .chat(
                        argThat((ChatRequest chatRequest) ->
                                chatRequest.messages().size() == 1),
                        any());
        verify(spyModel)
                .chat(
                        argThat((ChatRequest chatRequest) ->
                                chatRequest.messages().size() == 3
                                        && chatRequest.messages().get(2)
                                                instanceof ToolExecutionResultMessage toolResult
                                        && toolResult.text().equals(customizedErrorMessage)),
                        any());
        verifyNoMoreInteractionsFor(spyModel);

        verify(handler).beforeToolExecution(any());
        verify(handler)
                .onToolExecuted(argThat(toolExecution -> toolExecution.result().equals(customizedErrorMessage)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_fail_when_tool_throws_error(boolean executeToolsConcurrently) throws Exception {

        // given
        RuntimeException toolError = new RuntimeException("Weather service is unavailable");

        class FailingTool {

            @Tool
            String getWeather(String ignored) {
                throw toolError;
            }
        }

        ToolExecutionErrorHandler toolExecutionErrorHandler = (error, context) -> {
            assertThat(error).isSameAs(toolError);

            assertThat(context.toolExecutionRequest().name()).isEqualTo("getWeather");
            assertThat(context.toolExecutionRequest().arguments()).contains("Munich");
            assertThat(context.memoryId()).isEqualTo("default");

            throw toolError;
        };

        StreamingChatModel spyModel = spy(models().findFirst().get());

        FailingTool spyTool = spy(new FailingTool());

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .streamingChatModel(spyModel)
                .tools(spyTool)
                .toolExecutionErrorHandler(toolExecutionErrorHandler);
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);
        CompletableFuture<Throwable> futureError = new CompletableFuture<>();

        // when
        assistant
                .chat("What is the weather in Munich?")
                .onPartialResponse(handler::onPartialResponse)
                .beforeToolExecution(handler::beforeToolExecution)
                .onToolExecuted(handler::onToolExecuted)
                .onCompleteResponse(completeResponse -> {
                    handler.onCompleteResponse(completeResponse);
                    futureError.completeExceptionally(
                            new IllegalStateException("onCompleteResponse must not be called"));
                })
                .onError(error -> {
                    handler.onError(error);
                    futureError.complete(error);
                })
                .start();

        // then
        assertThat(futureError.get(30, SECONDS)).isSameAs(toolError);

        verify(spyTool).getWeather("Munich");
        verifyNoMoreInteractions(spyTool);

        verify(spyModel)
                .chat(
                        argThat((ChatRequest chatRequest) ->
                                chatRequest.messages().size() == 1),
                        any());
        verifyNoMoreInteractionsFor(spyModel);

        verify(handler).beforeToolExecution(any());
        verify(handler, never()).onToolExecuted(any());
    }

    // Error Handling: Argument Error

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_fail_when_cannot_parse_tool_arguments_by_default(boolean executeToolsConcurrently) throws Exception {

        // given
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("getWeather")
                .arguments("{ invalid json }")
                .build();

        StreamingChatModel spyModel =
                spy(StreamingChatModelMock.thatAlwaysStreams(AiMessage.from(toolExecutionRequest)));

        class WeatherTool {

            @Tool
            String getWeather(String ignored) {
                return "sunny";
            }
        }

        WeatherTool spyTool = spy(new WeatherTool());

        AiServices<Assistant> assistantBuilder =
                AiServices.builder(Assistant.class).streamingChatModel(spyModel).tools(spyTool);
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);
        CompletableFuture<Throwable> futureError = new CompletableFuture<>();

        // when
        assistant
                .chat("What is the weather in Munich?")
                .onPartialResponse(handler::onPartialResponse)
                .beforeToolExecution(handler::beforeToolExecution)
                .onToolExecuted(handler::onToolExecuted)
                .onCompleteResponse(completeResponse -> {
                    handler.onCompleteResponse(completeResponse);
                    futureError.completeExceptionally(
                            new IllegalStateException("onCompleteResponse must not be called"));
                })
                .onError(error -> {
                    handler.onError(error);
                    futureError.complete(error);
                })
                .start();

        // then
        Throwable error = futureError.get(60, SECONDS);

        assertThat(error)
                .isExactlyInstanceOf(RuntimeException.class)
                .hasCauseExactlyInstanceOf(JsonParseException.class)
                .hasMessageContaining("Unexpected character");

        verifyNoInteractions(spyTool);

        verify(spyModel).chat(any(ChatRequest.class), any());
        verifyNoMoreInteractionsFor(spyModel);

        verify(handler).beforeToolExecution(any());
        verify(handler, never()).onToolExecuted(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_customize_argument_parsing_error_before_sending_to_LLM(boolean executeToolsConcurrently)
            throws Exception {

        // given
        ToolExecutionRequest toolExecutionRequest1 = ToolExecutionRequest.builder()
                .name("getWeather")
                .arguments("{ invalid json }")
                .build();

        ToolExecutionRequest toolExecutionRequest2 = ToolExecutionRequest.builder()
                .name("getWeather")
                .arguments("{\"arg0\":\"Munich\"}")
                .build();

        StreamingChatModel spyModel = spy(StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(toolExecutionRequest1), AiMessage.from(toolExecutionRequest2), AiMessage.from("sunny")));

        class WeatherTool {

            @Tool
            String getWeather(String ignored) {
                return "sunny";
            }
        }

        WeatherTool spyTool = spy(new WeatherTool());

        String customizedErrorMessage = "Invalid JSON, try again";

        ToolArgumentsErrorHandler toolArgumentsErrorHandler = (error, context) -> {
            assertThat(error)
                    .isExactlyInstanceOf(JsonParseException.class)
                    .hasMessageContaining("Unexpected character");

            assertThat(context.toolExecutionRequest()).isEqualTo(toolExecutionRequest1);
            assertThat(context.memoryId()).isEqualTo("default");

            return ToolErrorHandlerResult.text(customizedErrorMessage);
        };

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .streamingChatModel(spyModel)
                .tools(spyTool)
                .toolArgumentsErrorHandler(toolArgumentsErrorHandler);
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        assistant
                .chat("What is the weather in Munich?")
                .onPartialResponse(handler::onPartialResponse)
                .beforeToolExecution(handler::beforeToolExecution)
                .onToolExecuted(handler::onToolExecuted)
                .onCompleteResponse(completeResponse -> {
                    handler.onCompleteResponse(completeResponse);
                    futureResponse.complete(completeResponse);
                })
                .onError(error -> {
                    handler.onError(error);
                    futureResponse.completeExceptionally(error);
                })
                .start();

        // then
        futureResponse.get(60, SECONDS);

        // then
        verify(spyTool).getWeather("Munich");
        verifyNoMoreInteractions(spyTool);

        verify(spyModel)
                .chat(
                        argThat((ChatRequest chatRequest) ->
                                chatRequest.messages().size() == 1),
                        any());
        verify(spyModel)
                .chat(
                        argThat((ChatRequest chatRequest) ->
                                chatRequest.messages().size() == 3
                                        && chatRequest.messages().get(2)
                                                instanceof ToolExecutionResultMessage toolResult
                                        && toolResult.text().equals(customizedErrorMessage)),
                        any());
        verify(spyModel)
                .chat(
                        argThat((ChatRequest chatRequest) ->
                                chatRequest.messages().size() == 5),
                        any());
        verifyNoMoreInteractionsFor(spyModel);

        verify(handler, times(2)).beforeToolExecution(any());
        verify(handler)
                .onToolExecuted(argThat(toolExecution -> toolExecution.result().equals(customizedErrorMessage)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_fail_with_custom_exception_when_tool_arguments_cannot_be_parsed(boolean executeToolsConcurrently)
            throws Exception {

        // given
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("getWeather")
                .arguments("{ invalid json }")
                .build();

        StreamingChatModel spyModel =
                spy(StreamingChatModelMock.thatAlwaysStreams(AiMessage.from(toolExecutionRequest)));

        class WeatherTool {

            @Tool
            String getWeather(String ignored) {
                return "sunny";
            }
        }

        WeatherTool spyTool = spy(new WeatherTool());

        RuntimeException customException = new RuntimeException("Can't parse JSON arguments");

        ToolArgumentsErrorHandler toolArgumentsErrorHandler = (error, context) -> {
            assertThat(error)
                    .isExactlyInstanceOf(JsonParseException.class)
                    .hasMessageContaining("Unexpected character");

            assertThat(context.toolExecutionRequest()).isEqualTo(toolExecutionRequest);
            assertThat(context.memoryId()).isEqualTo("default");

            throw customException;
        };

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .streamingChatModel(spyModel)
                .tools(spyTool)
                .toolArgumentsErrorHandler(toolArgumentsErrorHandler);
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        TestTokenStreamHandler handler = spy(TestTokenStreamHandler.class);
        CompletableFuture<Throwable> futureError = new CompletableFuture<>();

        // when
        assistant
                .chat("What is the weather in Munich?")
                .onPartialResponse(handler::onPartialResponse)
                .beforeToolExecution(handler::beforeToolExecution)
                .onToolExecuted(handler::onToolExecuted)
                .onCompleteResponse(completeResponse -> {
                    handler.onCompleteResponse(completeResponse);
                    futureError.completeExceptionally(
                            new IllegalStateException("onCompleteResponse must not be called"));
                })
                .onError(error -> {
                    handler.onError(error);
                    futureError.complete(error);
                })
                .start();

        Throwable error = futureError.get(60, SECONDS);

        assertThat(error).isSameAs(customException);

        // then
        verifyNoInteractions(spyTool);

        verify(spyModel).chat(any(ChatRequest.class), any());
        verifyNoMoreInteractionsFor(spyModel);

        verify(handler).beforeToolExecution(any());
        verify(handler, never()).onToolExecuted(any());
    }

    @Test
    void should_propagate_invocation_parameters_into_tool() throws Exception {

        // given
        class Tools {

            @Tool
            String getWeather(InvocationParameters invocationParameters) {
                String city = invocationParameters.get("city");
                return switch (city) {
                    case "Munich" -> "rainy";
                    default -> "sunny";
                };
            }
        }

        interface Assistant {

            TokenStream chat(
                    @dev.langchain4j.service.UserMessage String userMessage, InvocationParameters invocationParameters);
        }

        Tools spyTools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(models().findFirst().get())
                .tools(spyTools)
                .build();

        InvocationParameters invocationParameters1 = InvocationParameters.from("city", "Munich");
        CompletableFuture<ChatResponse> futureResponse1 = new CompletableFuture<>();

        // when
        assistant
                .chat("What is the weather?", invocationParameters1)
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(futureResponse1::complete)
                .onError(futureResponse1::completeExceptionally)
                .start();

        // then
        assertThat(futureResponse1.get(30, SECONDS).aiMessage().text()).contains("rain");
        verify(spyTools).getWeather(invocationParameters1);

        // given
        InvocationParameters invocationParameters2 = InvocationParameters.from("city", "Paris");
        CompletableFuture<ChatResponse> futureResponse2 = new CompletableFuture<>();

        // when
        assistant
                .chat("What is the weather?", invocationParameters2)
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(futureResponse2::complete)
                .onError(futureResponse2::completeExceptionally)
                .start();

        // then
        assertThat(futureResponse2.get(30, SECONDS).aiMessage().text()).contains("sun");
        verify(spyTools).getWeather(invocationParameters2);
    }

    @ParameterizedTest
    @MethodSource("modelsWithoutParallelToolCalling")
    public void should_not_execute_multiple_tools_sequentially_when_maxSequentialToolsInvocations_is_exceeded(
            StreamingChatModel model) throws Exception {

        // given
        int maxSequentialToolsInvocations = 1; // only one sequential tool call allowed, the test makes 3

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .tools(new TransactionService())
                .maxSequentialToolsInvocations(maxSequentialToolsInvocations)
                .build();

        AtomicInteger n = new AtomicInteger(0);
        CompletableFuture<Throwable> future = new CompletableFuture<>();

        // when
        assistant
                .chat("What are the amounts of transactions T001 and T002?")
                .beforeToolExecution(toolExecutionRequest -> {
                    final int index = n.incrementAndGet();
                    assertThat(index).isLessThanOrEqualTo(maxSequentialToolsInvocations);
                })
                .onToolExecuted(toolExecutionResult -> {
                    final int index = n.get();
                    assertThat(index).isLessThanOrEqualTo(maxSequentialToolsInvocations);
                })
                .onError(future::complete)
                .onCompleteResponse(ignored -> {
                    future.completeExceptionally(new IllegalStateException("onCompleteResponse must not be called"));
                })
                .start();

        // then
        assertThat(future.get(30, SECONDS))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("Something is wrong, exceeded 1 sequential tool invocations");
    }

    // TODO all other tests from sync version

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
}
