package dev.langchain4j.service;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.service.AiServicesWithToolsIT.TransactionService.EXPECTED_SPECIFICATION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AiServicesWithToolsIT {

    static Stream<ChatModel> models() {
        return Stream.of(
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build(),
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .strictTools(true)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build());
    }

    static Stream<ChatModel> modelsWithoutParallelToolCalling() {
        return Stream.of(OpenAiChatModel.builder()
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

        Result<String> chat(String userMessage);
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
        double getTransactionAmount(@P("ID of a transaction") String id) {
            threads.add(Thread.currentThread());
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

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_a_tool_then_answer(ChatModel chatModel) {

        TransactionService transactionService = spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModel spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(chatMemory)
                .tools(transactionService)
                .build();

        String userMessage = "What is the amounts of transaction T001?";

        Result<String> result = assistant.chat(userMessage);

        assertThat(result.content()).contains("11.1");

        assertThat(result.finishReason()).isEqualTo(STOP);

        verify(transactionService).getTransactionAmount("T001");
        verifyNoMoreInteractions(transactionService);

        assertThat(transactionService.threads).hasSize(1);
        assertThat(transactionService.threads.poll()).isEqualTo(Thread.currentThread());

        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(4);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("getTransactionAmount");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"arg0\": \"T001\"}");

        ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(toolExecutionResultMessage.id()).isEqualTo(toolExecutionRequest.id());
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("getTransactionAmount");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("11.1");

        AiMessage secondAiMessage = (AiMessage) messages.get(3);
        assertThat(secondAiMessage.text()).contains("11.1");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().get(0).request()).isEqualTo(toolExecutionRequest);
        assertThat(result.toolExecutions().get(0).result()).isEqualTo("11.1");
        assertThat(result.toolExecutions().get(0).resultObject()).isEqualTo(11.1);

        assertThat(result.intermediateResponses()).hasSize(1);
        ChatResponse intermediateResponse = result.intermediateResponses().get(0);
        assertThat(intermediateResponse.aiMessage()).isEqualTo(aiMessage);

        assertThat(result.finalResponse().aiMessage()).isEqualTo(secondAiMessage);

        TokenUsage tokenUsage = result.tokenUsage();
        assertThat(tokenUsage.inputTokenCount())
                .isEqualTo(intermediateResponse.tokenUsage().inputTokenCount()
                        + result.finalResponse().tokenUsage().inputTokenCount());
        assertThat(tokenUsage.outputTokenCount())
                .isEqualTo(intermediateResponse.tokenUsage().outputTokenCount()
                        + result.finalResponse().tokenUsage().outputTokenCount());
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        verify(spyChatModel)
                .chat(ChatRequest.builder()
                        .messages(messages.get(0))
                        .toolSpecifications(EXPECTED_SPECIFICATION)
                        .build());

        verify(spyChatModel)
                .chat(ChatRequest.builder()
                        .messages(messages.get(0), messages.get(1), messages.get(2))
                        .toolSpecifications(EXPECTED_SPECIFICATION)
                        .build());
    }

    @ParameterizedTest
    @MethodSource("modelsWithoutParallelToolCalling")
    void should_execute_multiple_tools_sequentially_then_answer(ChatModel chatModel) {

        TransactionService transactionService = spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModel spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(chatMemory)
                .tools(transactionService)
                .build();

        String userMessage = "What are the amounts of transactions T001 and T002?";

        Result<String> result = assistant.chat(userMessage);

        assertThat(result.content()).contains("11.1", "22.2");

        TokenUsage tokenUsage = result.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(result.finishReason()).isEqualTo(STOP);

        verify(transactionService).getTransactionAmount("T001");
        verify(transactionService).getTransactionAmount("T002");
        verifyNoMoreInteractions(transactionService);

        assertThat(transactionService.threads).hasSize(2);
        assertThat(transactionService.threads.poll()).isEqualTo(Thread.currentThread());
        assertThat(transactionService.threads.poll()).isEqualTo(Thread.currentThread());

        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(6);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("getTransactionAmount");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"arg0\": \"T001\"}");

        ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(toolExecutionResultMessage.id()).isEqualTo(toolExecutionRequest.id());
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("getTransactionAmount");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("11.1");

        AiMessage secondAiMessage = (AiMessage) messages.get(3);
        assertThat(secondAiMessage.text()).isNull();
        assertThat(secondAiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest secondToolExecutionRequest =
                secondAiMessage.toolExecutionRequests().get(0);
        assertThat(secondToolExecutionRequest.name()).isEqualTo("getTransactionAmount");
        assertThat(secondToolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"arg0\": \"T002\"}");

        ToolExecutionResultMessage secondToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(4);
        assertThat(secondToolExecutionResultMessage.id()).isEqualTo(secondToolExecutionRequest.id());
        assertThat(secondToolExecutionResultMessage.toolName()).isEqualTo("getTransactionAmount");
        assertThat(secondToolExecutionResultMessage.text()).isEqualTo("22.2");

        assertThat(messages.get(5)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) messages.get(5)).text()).contains("11.1", "22.2");

        verify(spyChatModel)
                .chat(ChatRequest.builder()
                        .messages(messages.get(0))
                        .toolSpecifications(EXPECTED_SPECIFICATION)
                        .build());

        verify(spyChatModel)
                .chat(ChatRequest.builder()
                        .messages(messages.get(0), messages.get(1), messages.get(2))
                        .toolSpecifications(EXPECTED_SPECIFICATION)
                        .build());

        verify(spyChatModel)
                .chat(ChatRequest.builder()
                        .messages(messages.get(0), messages.get(1), messages.get(2), messages.get(3), messages.get(4))
                        .toolSpecifications(EXPECTED_SPECIFICATION)
                        .build());
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_multiple_tools_in_parallel_then_answer(ChatModel chatModel) {

        TransactionService transactionService = spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModel spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(chatMemory)
                .tools(transactionService)
                .build();

        String userMessage = "What are the amounts of transactions T001 and T002? Call tools in parallel!";

        Result<String> result = assistant.chat(userMessage);

        assertThat(result.content()).contains("11.1", "22.2");

        TokenUsage tokenUsage = result.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(result.finishReason()).isEqualTo(STOP);

        verify(transactionService).getTransactionAmount("T001");
        verify(transactionService).getTransactionAmount("T002");
        verifyNoMoreInteractions(transactionService);

        assertThat(transactionService.threads).hasSize(2);
        assertThat(transactionService.threads.poll()).isEqualTo(Thread.currentThread());
        assertThat(transactionService.threads.poll()).isEqualTo(Thread.currentThread());

        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(5);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest firstToolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(firstToolExecutionRequest.name()).isEqualTo("getTransactionAmount");
        assertThat(firstToolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"arg0\": \"T001\"}");

        ToolExecutionRequest secondToolExecutionRequest =
                aiMessage.toolExecutionRequests().get(1);
        assertThat(secondToolExecutionRequest.name()).isEqualTo("getTransactionAmount");
        assertThat(secondToolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"arg0\": \"T002\"}");

        ToolExecutionResultMessage firstToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(firstToolExecutionResultMessage.id()).isEqualTo(firstToolExecutionRequest.id());
        assertThat(firstToolExecutionResultMessage.toolName()).isEqualTo("getTransactionAmount");
        assertThat(firstToolExecutionResultMessage.text()).isEqualTo("11.1");

        ToolExecutionResultMessage secondToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(3);
        assertThat(secondToolExecutionResultMessage.id()).isEqualTo(secondToolExecutionRequest.id());
        assertThat(secondToolExecutionResultMessage.toolName()).isEqualTo("getTransactionAmount");
        assertThat(secondToolExecutionResultMessage.text()).isEqualTo("22.2");

        assertThat(messages.get(4)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) messages.get(4)).text()).contains("11.1", "22.2");

        verify(spyChatModel)
                .chat(ChatRequest.builder()
                        .messages(messages.get(0))
                        .toolSpecifications(EXPECTED_SPECIFICATION)
                        .build());

        verify(spyChatModel)
                .chat(ChatRequest.builder()
                        .messages(messages.get(0), messages.get(1), messages.get(2), messages.get(3))
                        .toolSpecifications(EXPECTED_SPECIFICATION)
                        .build());
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("executors")
    void should_execute_multiple_tools_in_parallel_concurrently_then_answer(Executor executor) {

        // given
        class Tools {

            static final String CURRENT_TIME = "16:28";
            static final String CURRENT_TEMPERATURE = "17";

            final Queue<Thread> getCurrentTimeThreads = new ConcurrentLinkedQueue<>();
            final Queue<Thread> getCurrentTemperatureThreads = new ConcurrentLinkedQueue<>();

            @Tool
            String getCurrentTime(String city) {
                getCurrentTimeThreads.add(Thread.currentThread());
                return CURRENT_TIME;
            }

            @Tool
            String getCurrentTemperature(String city) {
                getCurrentTemperatureThreads.add(Thread.currentThread());
                return CURRENT_TEMPERATURE;
            }
        }

        Tools spyTools = spy(new Tools());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(models().findFirst().get())
                .chatMemory(chatMemory)
                .tools(spyTools)
                .executeToolsConcurrently(executor)
                .build();

        String userMessage = "What is the current time and temperature in Munich?";

        // when
        Result<String> result = assistant.chat(userMessage);

        // then
        assertThat(result.content()).contains(Tools.CURRENT_TIME, Tools.CURRENT_TEMPERATURE);

        verify(spyTools).getCurrentTime("Munich");
        verify(spyTools).getCurrentTemperature("Munich");
        verifyNoMoreInteractions(spyTools);

        assertThat(spyTools.getCurrentTimeThreads).hasSize(1);
        Thread getCurrentTimeThread = spyTools.getCurrentTimeThreads.poll();
        assertThat(getCurrentTimeThread).isNotEqualTo(Thread.currentThread());

        assertThat(spyTools.getCurrentTemperatureThreads).hasSize(1);
        Thread getCurrentTemperatureThread = spyTools.getCurrentTemperatureThreads.poll();
        assertThat(getCurrentTemperatureThread).isNotEqualTo(Thread.currentThread());

        assertThat(getCurrentTimeThread).isNotEqualTo(getCurrentTemperatureThread);
    }

    static List<Executor> executors() {
        return List.of(Executors.newFixedThreadPool(2));
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("executors")
    void should_execute_single_tool_in_the_same_thread(Executor executor) {

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
                .chatModel(models().findFirst().get())
                .chatMemory(chatMemory)
                .tools(spyTools)
                .executeToolsConcurrently(executor)
                .build();

        String userMessage = "What is the current temperature in Munich?";

        // when
        Result<String> result = assistant.chat(userMessage);

        // then
        assertThat(result.content()).contains(Tools.CURRENT_TEMPERATURE);

        verify(spyTools).getCurrentTemperature("Munich");
        verifyNoMoreInteractions(spyTools);

        assertThat(spyTools.getCurrentTemperatureThreads).hasSize(1);
        assertThat(spyTools.getCurrentTemperatureThreads.poll()).isEqualTo(Thread.currentThread());
    }

    static class IntegerListProcessor {

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("processIntegers")
                .description("Processes list of integers")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(singletonMap(
                                "arg0",
                                JsonArraySchema.builder()
                                        .description("List of integers to process")
                                        .items(new JsonIntegerSchema())
                                        .build()))
                        .required("arg0")
                        .build())
                .build();

        @Tool("Processes list of integers")
        void processIntegers(@P("List of integers to process") List<Integer> integers) {
            System.out.printf("called processIntegers(%s)%n", integers);
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_tool_with_List_of_Integers_parameter(ChatModel chatModel) {

        IntegerListProcessor integerListProcessor = spy(new IntegerListProcessor());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModel spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(chatMemory)
                .tools(integerListProcessor)
                .build();

        String userMessage = "Process integers 1 and 2 together, do not separate them!";

        // when
        assistant.chat(userMessage);

        // then
        verify(integerListProcessor).processIntegers(asList(1, 2));
        verifyNoMoreInteractions(integerListProcessor);

        List<ChatMessage> messages = chatMemory.messages();
        verify(spyChatModel)
                .chat(ChatRequest.builder()
                        .messages(messages.get(0))
                        .toolSpecifications(IntegerListProcessor.EXPECTED_SPECIFICATION)
                        .build());
        verify(spyChatModel)
                .chat(ChatRequest.builder()
                        .messages(messages.get(0), messages.get(1), messages.get(2))
                        .toolSpecifications(IntegerListProcessor.EXPECTED_SPECIFICATION)
                        .build());
    }

    static class StringArrayProcessor {

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("processStrings")
                .description("Processes array of strings")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(singletonMap(
                                "arg0",
                                JsonArraySchema.builder()
                                        .description("Array of strings to process")
                                        .items(new JsonStringSchema())
                                        .build()))
                        .required("arg0")
                        .build())
                .build();

        @Tool("Processes array of strings")
        void processStrings(@P("Array of strings to process") String[] ids) {
            System.out.printf("called processStrings(%s)%n", Arrays.toString(ids));
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_tool_with_Array_of_Strings_parameter(ChatModel chatModel) {

        StringArrayProcessor stringArrayProcessor = spy(new StringArrayProcessor());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModel spyChatModel = spy(chatModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyChatModel)
                .chatMemory(chatMemory)
                .tools(stringArrayProcessor)
                .build();

        String userMessage = "Process strings 'cat' and 'dog' together, do not separate them!";

        // when
        assistant.chat(userMessage);

        // then
        verify(stringArrayProcessor).processStrings(new String[] {"cat", "dog"});
        verifyNoMoreInteractions(stringArrayProcessor);

        List<ChatMessage> messages = chatMemory.messages();
        verify(spyChatModel)
                .chat(ChatRequest.builder()
                        .messages(messages.get(0))
                        .toolSpecifications(StringArrayProcessor.EXPECTED_SPECIFICATION)
                        .build());
        verify(spyChatModel)
                .chat(ChatRequest.builder()
                        .messages(messages.get(0), messages.get(1), messages.get(2))
                        .toolSpecifications(StringArrayProcessor.EXPECTED_SPECIFICATION)
                        .build());
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_programmatically_configured_tools(ChatModel chatModel) {

        // given
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("get_booking_details")
                .description("Returns booking details")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(singletonMap("bookingNumber", new JsonStringSchema()))
                        .build())
                .build();

        ToolExecutor toolExecutor = (toolExecutionRequest, memoryId) -> {
            Map<String, Object> arguments = toMap(toolExecutionRequest.arguments());
            assertThat(arguments).containsExactly(entry("bookingNumber", "123-456"));
            return "Booking period: from 1 July 2027 to 10 July 2027";
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(singletonMap(toolSpecification, toolExecutor))
                .build();

        // when
        Result<String> result = assistant.chat("When does my booking 123-456 starts?");

        // then
        assertThat(result.content()).contains("2027");
    }

    static class BookingToolExecutor implements ToolExecutor {

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            Map<String, Object> arguments = toMap(request.arguments());
            assertThat(arguments).containsExactly(entry("bookingNumber", "123-456"));
            return "Booking period: from 1 July 2027 to 10 July 2027";
        }
    }

    @Test
    void should_use_tool_provider() {

        ToolExecutor toolExecutor = spy(new BookingToolExecutor());

        ToolProvider toolProvider = (toolProviderRequest) -> {
            if (toolProviderRequest.userMessage().singleText().contains("booking")) {
                ToolSpecification toolSpecification = ToolSpecification.builder()
                        .name("get_booking_details")
                        .description("Returns booking details")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("bookingNumber")
                                .build())
                        .build();
                return ToolProviderResult.builder()
                        .add(toolSpecification, toolExecutor)
                        .build();
            } else {
                return null;
            }
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(models().findFirst().get())
                .toolProvider(toolProvider)
                .build();

        assistant.chat("When does my holiday 123-456 starts?");
        verifyNoInteractions(toolExecutor); // user message does not contain word "booking"

        Result<String> result = assistant.chat("When does my booking 123-456 starts?");
        assertThat(result.content()).contains("2027");
        verify(toolExecutor).executeWithContext(any(), any(InvocationContext.class));
        verify(toolExecutor).execute(any(), any(Object.class));
        verifyNoMoreInteractions(toolExecutor);
    }

    static class Calculator {

        @Tool("applies the function xyz on the provided number")
        int xyz(@P("number to operate on") int number) {
            System.out.printf("called xyz(%s)%n", number);
            return number + 1;
        }
    }

    @Test
    void should_allow_configuring_tools_and_tool_provider_simultaneously() {

        ToolExecutor toolExecutor = spy(new BookingToolExecutor());

        Calculator calculator = spy(new Calculator());

        ToolProvider toolProvider = request -> {
            ToolSpecification toolSpecification = ToolSpecification.builder()
                    .name("get_booking_details")
                    .description("Returns booking details")
                    .parameters(JsonObjectSchema.builder()
                            .addStringProperty("bookingNumber")
                            .build())
                    .build();
            return ToolProviderResult.builder()
                    .add(toolSpecification, toolExecutor)
                    .build();
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(models().findFirst().get())
                .toolProvider(toolProvider)
                .tools(calculator)
                .build();

        Result<String> result =
                assistant.chat("Apply the function xyz on the number of the year when my booking 123-456 starts");
        assertThat(result.content()).contains("2028");

        verify(calculator).xyz(2027);
        verifyNoMoreInteractions(calculator);

        verify(toolExecutor).executeWithContext(any(), any(InvocationContext.class));
        verify(toolExecutor).execute(any(), any(Object.class));
        verifyNoMoreInteractions(toolExecutor);
    }

    @Test
    void should_throw_if_static_and_dynamic_tools_are_duplicated() {
        Calculator calculator = spy(new Calculator());

        ToolProvider toolProvider = request -> {
            ToolSpecification toolSpecification = ToolSpecification.builder()
                    .name("xyz")
                    .description("applies the function xyz on the provided number")
                    .parameters(JsonObjectSchema.builder()
                            .addIntegerProperty("number")
                            .build())
                    .build();
            return ToolProviderResult.builder()
                    .add(toolSpecification, (ToolExecutionRequest toolExecutionRequest, Object memoryId) -> {
                        Map<String, Object> arguments = toMap(toolExecutionRequest.arguments());
                        assertThat(arguments).containsExactly(entry("number", 2027));
                        return "3000";
                    })
                    .build();
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(models().findFirst().get())
                .toolProvider(toolProvider)
                .tools(calculator)
                .build();

        assertThat(assertThrows(
                        IllegalConfigurationException.class,
                        () -> assistant.chat("Apply the function xyz on the number 2027")))
                .hasMessageContaining("xyz");
    }

    @Test
    void should_propagate_invocation_parameters_into_tool() {

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

            String chat(
                    @dev.langchain4j.service.UserMessage String userMessage, InvocationParameters invocationParameters);
        }

        Tools spyTools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(models().findFirst().get())
                .tools(spyTools)
                .build();

        InvocationParameters invocationParameters1 = InvocationParameters.from("city", "Munich");

        // when
        String answer1 = assistant.chat("What is the weather?", invocationParameters1);

        // then
        assertThat(answer1).contains("rain");
        verify(spyTools).getWeather(invocationParameters1);

        // given
        InvocationParameters invocationParameters2 = InvocationParameters.from("city", "Paris");

        // when
        String answer2 = assistant.chat("What is the weather?", invocationParameters2);

        // then
        assertThat(answer2).contains("sun");
        verify(spyTools).getWeather(invocationParameters2);
    }

    @Test
    void should_propagate_custom_invocation_parameters_into_tool() {

        // given
        class CustomInvocationParameters extends InvocationParameters {

            public CustomInvocationParameters(Map<String, Object> map) {
                super(map);
            }
        }

        class Tools {

            @Tool
            String getWeather(CustomInvocationParameters invocationParameters) {
                String city = invocationParameters.get("city");
                return switch (city) {
                    case "Munich" -> "rainy";
                    default -> "sunny";
                };
            }
        }

        interface Assistant {

            String chat(
                    @dev.langchain4j.service.UserMessage String userMessage,
                    CustomInvocationParameters invocationParameters);
        }

        Tools spyTools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(models().findFirst().get())
                .tools(spyTools)
                .build();

        CustomInvocationParameters invocationParameters = new CustomInvocationParameters(Map.of("city", "Munich"));

        // when
        String answer = assistant.chat("What is the weather?", invocationParameters);

        // then
        assertThat(answer).contains("rain");
        verify(spyTools).getWeather(invocationParameters);
    }

    @Test
    void should_propagate_invocation_context_into_tool() {

        // given
        class Tools {

            @Tool
            String getWeather(InvocationContext invocationContext) {
                String city = invocationContext.invocationParameters().get("city");
                return switch (city) {
                    case "Munich" -> "rainy";
                    default -> "sunny";
                };
            }
        }

        interface Assistant {

            String chat(
                    @dev.langchain4j.service.UserMessage String userMessage, InvocationParameters invocationParameters);
        }

        Tools spyTools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(models().findFirst().get())
                .tools(spyTools)
                .build();

        InvocationParameters invocationParameters1 = InvocationParameters.from("city", "Munich");

        // when
        String answer1 = assistant.chat("What is the weather?", invocationParameters1);

        // then
        assertThat(answer1).contains("rain");
        verify(spyTools).getWeather(argThat(ctx -> ctx.invocationParameters().equals(invocationParameters1)));

        // given
        InvocationParameters invocationParameters2 = InvocationParameters.from("city", "Paris");

        // when
        String answer2 = assistant.chat("What is the weather?", invocationParameters2);

        // then
        assertThat(answer2).contains("sun");
        verify(spyTools).getWeather(argThat(ctx -> ctx.invocationParameters().equals(invocationParameters2)));
    }

    @Test
    void should_propagate_invocation_parameters_between_tools() {

        // given
        class Tools {

            static final LocalTime CURRENT_TIME = LocalTime.of(12, 34, 56);

            @Tool
            String getWeather(InvocationParameters invocationParameters) {
                assertThat(invocationParameters.asMap()).isEmpty();
                invocationParameters.put("calledGetWeather", true);

                return "sunny";
            }

            @Tool
            LocalTime getTime(InvocationParameters invocationParameters) {
                assertThat(invocationParameters.asMap()).containsOnly(Map.entry("calledGetWeather", true));
                return CURRENT_TIME;
            }
        }

        Tools spyTools = spy(new Tools());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(models().findFirst().get())
                .tools(spyTools)
                .build();

        // when
        Result<String> result = assistant.chat("What is the weather and time?");

        // then
        assertThat(result.content()).contains("sun", "12", "34");
        assertThat(result.toolExecutions()).hasSize(2);
        assertThat(result.toolExecutions().get(0).result()).isEqualTo("sunny");
        assertThat(result.toolExecutions().get(0).resultObject()).isEqualTo("sunny");
        assertThat(result.toolExecutions().get(1).result()).isEqualTo(Json.toJson(Tools.CURRENT_TIME));
        assertThat(result.toolExecutions().get(1).resultObject()).isEqualTo(Tools.CURRENT_TIME);

        verify(spyTools).getWeather(any());
        verify(spyTools).getTime(any());
        verifyNoMoreInteractions(spyTools);
    }

    @Test
    void should_propagate_invocation_parameters_into_tool_provider() {

        // given
        interface Assistant {

            String chat(
                    @dev.langchain4j.service.UserMessage String userMessage, InvocationParameters invocationParameters);
        }

        String includeToolsKey = "includeTools";

        ToolProvider toolProvider = request -> {
            if (request.invocationContext().invocationParameters().get(includeToolsKey)) {
                ToolSpecification toolSpecification = ToolSpecification.builder()
                        .name("xyz")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("number")
                                .build())
                        .build();

                return ToolProviderResult.builder()
                        .add(toolSpecification, new ToolExecutor() {

                            @Override
                            public ToolExecutionResult executeWithContext(
                                    ToolExecutionRequest request, InvocationContext context) {
                                assertThat((boolean)
                                                context.invocationParameters().get(includeToolsKey))
                                        .isEqualTo(true);
                                Map<String, Object> arguments = toMap(request.arguments());
                                assertThat(arguments).containsExactly(entry("number", 2027));
                                return ToolExecutionResult.builder()
                                        .resultText("3000")
                                        .build();
                            }

                            @Override
                            public String execute(ToolExecutionRequest request, Object memoryId) {
                                throw new RuntimeException("should not be called");
                            }
                        })
                        .build();
            }

            return ToolProviderResult.builder().build();
        };

        ChatModel spyModel = spy(ChatModelMock.thatAlwaysResponds("does not matter"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(spyModel)
                .toolProvider(toolProvider)
                .build();

        InvocationParameters invocationParameters1 = new InvocationParameters();
        invocationParameters1.put(includeToolsKey, false);

        // when
        assistant.chat("does not matter", invocationParameters1);

        // then
        verify(spyModel)
                .chat(argThat((ChatRequest chatRequest) ->
                        chatRequest.toolSpecifications().isEmpty()));

        // given
        InvocationParameters invocationParameters2 = new InvocationParameters();
        invocationParameters2.put(includeToolsKey, true);

        // when
        assistant.chat("does not matter", invocationParameters2);

        // then
        verify(spyModel)
                .chat(argThat((ChatRequest chatRequest) ->
                        chatRequest.toolSpecifications().size() == 1));
    }

    private static Map<String, Object> toMap(String arguments) {
        try {
            return new ObjectMapper().readValue(arguments, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    interface AssistantReturningResult {

        Result<AiMessage> chat(String userMessage);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_a_tool_and_context_included_in_result(ChatModel chatModel) {

        // given
        TransactionService transactionService = spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModel spyChatModel = spy(chatModel);

        AssistantReturningResult assistant = AiServices.builder(AssistantReturningResult.class)
                .chatModel(spyChatModel)
                .chatMemory(chatMemory)
                .tools(transactionService)
                .build();

        String userMessage = "What are the amount of transactions T001?";

        // when
        Result<AiMessage> result = assistant.chat(userMessage);

        // then
        assertThat(result.toolExecutions()).hasSize(1);
        ToolExecution toolExecution = result.toolExecutions().get(0);
        assertThat(toolExecution.request().name()).isEqualTo("getTransactionAmount");
        assertThat(toolExecution.request().arguments()).isEqualToIgnoringWhitespace("{\"arg0\": \"T001\"}");
        assertThat(toolExecution.result()).isEqualTo("11.1");

        verify(spyChatModel, times(2)).chat(any(ChatRequest.class));
    }

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void should_execute_multi_tool_in_parallel_and_context_included_in_result(ChatModel chatModel) {

        // given
        TransactionService transactionService = spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModel spyChatModel = spy(chatModel);

        AssistantReturningResult assistant = AiServices.builder(AssistantReturningResult.class)
                .chatModel(spyChatModel)
                .chatMemory(chatMemory)
                .tools(transactionService)
                .build();

        String userMessage = "What are the amounts of transactions T001 and T002? "
                + "First call getTransactionAmount for T001, then for T002, in parallel. Do not answer before you know all amounts!";

        // when
        Result<AiMessage> result = assistant.chat(userMessage);

        // then
        assertThat(result.toolExecutions()).hasSize(2);

        ToolExecution firstToolExecution = result.toolExecutions().get(0);
        assertThat(firstToolExecution.request().name()).isEqualTo("getTransactionAmount");
        assertThat(firstToolExecution.request().arguments()).isEqualToIgnoringWhitespace("{\"arg0\": \"T001\"}");
        assertThat(firstToolExecution.result()).isEqualTo("11.1");

        ToolExecution secondToolExecution = result.toolExecutions().get(1);
        assertThat(secondToolExecution.request().name()).isEqualTo("getTransactionAmount");
        assertThat(secondToolExecution.request().arguments()).isEqualToIgnoringWhitespace("{\"arg0\": \"T002\"}");
        assertThat(secondToolExecution.result()).contains("22.2");

        verify(spyChatModel, times(2)).chat(any(ChatRequest.class));
    }

    @ParameterizedTest
    @MethodSource("modelsWithoutParallelToolCalling")
    void should_execute_multiple_tools_sequentially_and_context_included_in_result(ChatModel chatModel) {

        // given
        TransactionService transactionService = spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModel spyChatModel = spy(chatModel);

        AssistantReturningResult assistant = AiServices.builder(AssistantReturningResult.class)
                .chatModel(spyChatModel)
                .chatMemory(chatMemory)
                .tools(transactionService)
                .build();

        String userMessage = "What are the amounts of transactions T001 and T002?";

        // when
        Result<AiMessage> result = assistant.chat(userMessage);

        // then
        assertThat(result.toolExecutions()).hasSize(2);

        ToolExecution firstToolExecution = result.toolExecutions().get(0);
        assertThat(firstToolExecution.request().name()).isEqualTo("getTransactionAmount");
        assertThat(firstToolExecution.request().arguments()).isEqualToIgnoringWhitespace("{\"arg0\": \"T001\"}");
        assertThat(firstToolExecution.result()).isEqualTo("11.1");

        ToolExecution secondToolExecution = result.toolExecutions().get(1);
        assertThat(secondToolExecution.request().name()).isEqualTo("getTransactionAmount");
        assertThat(secondToolExecution.request().arguments()).isEqualToIgnoringWhitespace("{\"arg0\": \"T002\"}");
        assertThat(secondToolExecution.result()).contains("22.2");

        verify(spyChatModel, times(3)).chat(any(ChatRequest.class));
    }

    @ParameterizedTest
    @MethodSource("modelsWithoutParallelToolCalling")
    void should_not_execute_multiple_tools_sequentially_when_maxSequentialToolsInvocations_is_exceeded(
            ChatModel chatModel) {

        // given
        int maxSequentialToolsInvocations = 1; // only one sequential tool call allowed, the test makes 3

        AssistantReturningResult assistant = AiServices.builder(AssistantReturningResult.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(new TransactionService())
                .maxSequentialToolsInvocations(maxSequentialToolsInvocations)
                .build();

        String userMessage = "What are the amounts of transactions T001 and T002?";

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> assistant.chat(userMessage))
                .withMessage("Something is wrong, exceeded 1 sequential tool invocations");
    }

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void should_execute_tool_with_custom_return_type(ChatModel chatModel) {

        LocalDate now = LocalDate.of(2025, 2, 24);

        record ToolResult(LocalDate localDate) {}

        class Tools {

            @Tool
            ToolResult currentDate() {
                return new ToolResult(now);
            }
        }

        Tools tools = spy(new Tools());

        ChatModel spyChatModel = spy(chatModel);

        AssistantReturningResult assistant = AiServices.builder(AssistantReturningResult.class)
                .chatModel(spyChatModel)
                .tools(tools)
                .build();

        String userMessage = "What is the current date?";

        // when
        Result<AiMessage> result = assistant.chat(userMessage);

        // then
        assertThat(result.content().text())
                .contains(String.valueOf(now.getYear()), String.valueOf(now.getDayOfMonth()));

        verify(tools).currentDate();
        verifyNoMoreInteractions(tools);
    }

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void should_execute_tool_with_custom_name(ChatModel chatModel) {

        LocalDate now = LocalDate.of(2025, 2, 24);

        class Tools {

            private static final String CUSTOM_TOOL_NAME = "get_current_date";

            @Tool(name = CUSTOM_TOOL_NAME)
            LocalDate currentDate() {
                return now;
            }
        }

        Tools tools = spy(new Tools());

        ChatModel spyChatModel = spy(chatModel);

        AssistantReturningResult assistant = AiServices.builder(AssistantReturningResult.class)
                .chatModel(spyChatModel)
                .tools(tools)
                .build();

        String userMessage = "What is the current date?";

        // when
        Result<AiMessage> result = assistant.chat(userMessage);

        // then
        assertThat(result.content().text())
                .contains(String.valueOf(now.getYear()), String.valueOf(now.getDayOfMonth()));
        assertThat(result.toolExecutions().get(0).request().name()).isEqualTo(Tools.CUSTOM_TOOL_NAME);

        verify(tools).currentDate();
        verifyNoMoreInteractions(tools);
    }

    interface RouterAgent {

        @dev.langchain4j.service.UserMessage(
                """
            Analyze the following user request and categorize it as 'legal', 'medical' or 'technical',
            then forward the request as it is to the corresponding expert provided as a tool.
            Finally return the answer that you received from the expert without any modification.

            The user request is: '{{it}}'.
            """)
        String askToExpert(String request);
    }

    interface MedicalExpert {

        @dev.langchain4j.service.UserMessage(
                """
            You are a medical expert.
            Analyze the following user request under a medical point of view and provide the best possible answer.
            The user request is {{it}}.
            """)
        @Tool("A medical expert")
        String medicalRequest(String request);
    }

    interface LegalExpert {

        @dev.langchain4j.service.UserMessage(
                """
            You are a legal expert.
            Analyze the following user request under a legal point of view and provide the best possible answer.
            The user request is {{it}}.
            """)
        @Tool("A legal expert")
        String legalRequest(String request);
    }

    interface TechnicalExpert {

        @dev.langchain4j.service.UserMessage(
                """
            You are a technical expert.
            Analyze the following user request under a technical point of view and provide the best possible answer.
            The user request is {{it}}.
            """)
        @Tool("A technical expert")
        String technicalRequest(String request);
    }

    @ParameterizedTest
    @MethodSource("models")
    void tools_as_agents_tests(ChatModel model) {
        MedicalExpert medicalExpert =
                spy(AiServices.builder(MedicalExpert.class).chatModel(model).build());
        LegalExpert legalExpert =
                spy(AiServices.builder(LegalExpert.class).chatModel(model).build());
        TechnicalExpert technicalExpert =
                spy(AiServices.builder(TechnicalExpert.class).chatModel(model).build());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        RouterAgent routerAgent = AiServices.builder(RouterAgent.class)
                .chatModel(model)
                .chatMemory(chatMemory)
                .tools(medicalExpert, legalExpert, technicalExpert)
                .build();

        routerAgent.askToExpert("I broke my leg what should I do");
        verify(medicalExpert).medicalRequest("I broke my leg what should I do");
        verifyNoInteractions(legalExpert, technicalExpert);

        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class); // user prompt
        assertThat(messages.get(1)).isInstanceOf(AiMessage.class); // ai message to invoke the tool
        assertThat(messages.get(2)).isInstanceOf(ToolExecutionResultMessage.class); // tool response
        assertThat(messages.get(3)).isInstanceOf(AiMessage.class); // final ai message
    }

    interface VoidAssistant {

        void chat(String userMessage);
    }

    @ParameterizedTest
    @MethodSource("modelsWithoutParallelToolCalling")
    void should_allow_void_return(ChatModel chatModel) {

        LocalDate now = LocalDate.of(2025, 2, 24);

        record ToolResult(LocalDate localDate) {}

        class Tools {

            @Tool
            ToolResult currentDate() {
                return new ToolResult(now);
            }
        }

        Tools tools = spy(new Tools());

        ChatModel spyChatModel = spy(chatModel);

        VoidAssistant assistant = AiServices.builder(VoidAssistant.class)
                .chatModel(spyChatModel)
                .tools(tools)
                .build();

        String userMessage = "What is the current date?";

        assistant.chat(userMessage);

        verify(tools).currentDate();
        verifyNoMoreInteractions(tools);
    }

    interface ResultOfVoidAssistant {

        Result<Void> chat(String userMessage);
    }

    @ParameterizedTest
    @MethodSource("modelsWithoutParallelToolCalling")
    void should_allow_result_of_void_return(ChatModel chatModel) {

        LocalDate now = LocalDate.of(2025, 2, 24);

        record ToolResult(LocalDate localDate) {}

        class Tools {

            @Tool
            ToolResult currentDate() {
                return new ToolResult(now);
            }
        }

        Tools tools = spy(new Tools());

        ChatModel spyChatModel = spy(chatModel);

        ResultOfVoidAssistant assistant = AiServices.builder(ResultOfVoidAssistant.class)
                .chatModel(spyChatModel)
                .tools(tools)
                .build();

        String userMessage = "What is the current date?";

        Result<Void> result = assistant.chat(userMessage);
        assertThat(result.content()).isNull();
        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().get(0).resultObject()).isEqualTo(new ToolResult(now));

        verify(tools).currentDate();
        verifyNoMoreInteractions(tools);
    }

    interface Counter {

        @dev.langchain4j.service.UserMessage("Calculate the result of the following expression:\n|||{{it}}|||")
        int doMath(String sentence);
    }

    @ParameterizedTest
    @MethodSource("modelsWithoutParallelToolCalling")
    void should_rewrite_chat_request_using_tool(ChatModel chatModel) {
        AtomicInteger result = new AtomicInteger();
        AiServicesIT.UserMessageTransformer requestTransformer = userMessage -> userMessage.replace("three", "four");

        Counter counter = AiServices.builder(Counter.class)
                .chatModel(chatModel)
                .tools(new CalculatorTool(result))
                .chatRequestTransformer(requestTransformer)
                .build();

        String sentence = "Add ten to three then multiply the result by three.";

        int count = counter.doMath(sentence);
        assertThat(count).isEqualTo(56);
        // check that the tools have been correctly invoked
        assertThat(result.get()).isEqualTo(56);
    }

    public static class CalculatorTool {
        private final AtomicInteger result;

        public CalculatorTool(final AtomicInteger result) {
            this.result = result;
        }

        @Tool("calculates the sum of two integers")
        public int sum(int first, int second) {
            return first + second;
        }

        @Tool("calculates the multiplication of two integers")
        public int multiply(int first, int second) {
            int prod = first * second;
            result.set(prod);
            return prod;
        }
    }
}
