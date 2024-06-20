package dev.langchain4j.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.agent.tool.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.output.structured.Description;
import lombok.Data;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.description;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.*;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_LARGE_LATEST;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_3_5_TURBO_0613;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.service.AiServicesWithToolsIT.TemperatureUnit.Kelvin;
import static dev.langchain4j.service.AiServicesWithToolsIT.TransactionService.EXPECTED_SPECIFICATION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServicesWithToolsIT {

    static Stream<ChatLanguageModel> models() {
        return Stream.of(
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build(),
                MistralAiChatModel.builder()
                        .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                        .modelName(MISTRAL_LARGE_LATEST)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build()
                // TODO other models supporting tools
        );
    }

    static Stream<ChatLanguageModel> modelsWithoutParallelToolCalling() {
        return Stream.of(
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_3_5_TURBO_0613) // this model can only call tools sequentially
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build(),
                MistralAiChatModel.builder()
                        .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                        .modelName(MISTRAL_LARGE_LATEST) // Mistral does not have a model that can call tools sequentially
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build()
                // TODO other models supporting tools
        );
    }

    interface Assistant {

        Response<AiMessage> chat(String userMessage);
    }

    static class TransactionService {

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("getTransactionAmount")
                .description("returns amount of a given transaction")
                .addParameter("arg0", STRING, description("ID of a transaction"))
                .build();

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

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    static class ApiTool {

        private String action;
        private String url;
        private String method;
        private String headers;
        private String examplePayload;
        private List<String> query;
        private String description;
    }

    static class UserDetails {

        @Description("") // or dynamically
        String name;

        @Description("")
        String surname;

        @Description("")
        String bookingNumber;
    }

    static class BookingChecker implements Function<UserDetails, String> {

        @Override
        public String apply(UserDetails userDetails) {
            if (userDetails.bookingNumber.equals("123-456")) {
                return "Booking found. Booking period: 1 July 2027 - 10 July 2027";
            } else {
                return "Booking not found";
            }
        }
    }

    String getRequest(String url) {
        assertThat(url).isEqualTo("https://url2.com/accounts");
        return "Account ids: 77, 13, 45";
    }

    @Test
    void test() throws IOException { // TODO name

        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        File jsonFile = new File(getClass().getClassLoader().getResource("tools.json").getFile());
        List<ApiTool> apiTools = new ObjectMapper().readValue(jsonFile, new TypeReference<List<ApiTool>>() {
        });

        AtomicInteger atomicInteger = new AtomicInteger(1);

        List<ToolSomething> tools = apiTools.stream()
                .filter(apiTool -> "GET".equals(apiTool.method))
                .map(apiTool -> ToolSomething.from(
                        "tool_" + atomicInteger.getAndIncrement(),
                        apiTool.description,
                        () -> getRequest(apiTool.url)
                ))
                .collect(Collectors.toList());

//        ToolSomething toolSomething = ToolSomething.from(
//                "get_booking_details",
//                "Get booking details",
//                UserDetails.class, // single param / multiple params. types? Map? How to describe params?
//                new BookingChecker()
//        );

        // TODO how to register them in Spring boot app? Return a list of those as a bean?

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(tools)
                .build();

        Response<AiMessage> response = assistant.chat("Show me all CRM accounts");

        assertThat(response.content().text()).contains("77", "13", "45");
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_a_tool_then_answer(ChatLanguageModel chatLanguageModel) {

        TransactionService transactionService = spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatLanguageModel spyChatLanguageModel = spy(chatLanguageModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(spyChatLanguageModel)
                .chatMemory(chatMemory)
                .tools(transactionService)
                .build();

        String userMessage = "What is the amounts of transaction T001?";

        Response<AiMessage> response = assistant.chat(userMessage);

        assertThat(response.content().text()).contains("11.1");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0); // TODO test token count
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(transactionService).getTransactionAmount("T001");
        verifyNoMoreInteractions(transactionService);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(4);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("getTransactionAmount");
        assertThat(toolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": \"T001\"}");

        ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(toolExecutionResultMessage.id()).isEqualTo(toolExecutionRequest.id());
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("getTransactionAmount");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("11.1");

        assertThat(messages.get(3)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(3).text()).contains("11.1");


        verify(spyChatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(EXPECTED_SPECIFICATION)
        );

        verify(spyChatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2)),
                singletonList(EXPECTED_SPECIFICATION)
        );
    }

    @ParameterizedTest
    @MethodSource("modelsWithoutParallelToolCalling")
    void should_execute_multiple_tools_sequentially_then_answer(ChatLanguageModel chatLanguageModel) {

        TransactionService transactionService = spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatLanguageModel spyChatLanguageModel = spy(chatLanguageModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(spyChatLanguageModel)
                .chatMemory(chatMemory)
                .tools(transactionService)
                .build();

        String userMessage = "What are the amounts of transactions T001 and T002? " +
                "First call getTransactionAmount for T001, then for T002. Do not answer before you know all amounts!";

        Response<AiMessage> response = assistant.chat(userMessage);

        assertThat(response.content().text()).contains("11.1", "22.2");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0); // TODO
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(transactionService).getTransactionAmount("T001");
        verify(transactionService).getTransactionAmount("T002");
        verifyNoMoreInteractions(transactionService);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(6);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("getTransactionAmount");
        assertThat(toolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": \"T001\"}");

        ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(toolExecutionResultMessage.id()).isEqualTo(toolExecutionRequest.id());
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("getTransactionAmount");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("11.1");

        AiMessage secondAiMessage = (AiMessage) messages.get(3);
        assertThat(secondAiMessage.text()).isNull();
        assertThat(secondAiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest secondToolExecutionRequest = secondAiMessage.toolExecutionRequests().get(0);
        assertThat(secondToolExecutionRequest.name()).isEqualTo("getTransactionAmount");
        assertThat(secondToolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": \"T002\"}");

        ToolExecutionResultMessage secondToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(4);
        assertThat(secondToolExecutionResultMessage.id()).isEqualTo(secondToolExecutionRequest.id());
        assertThat(secondToolExecutionResultMessage.toolName()).isEqualTo("getTransactionAmount");
        assertThat(secondToolExecutionResultMessage.text()).isEqualTo("22.2");

        assertThat(messages.get(5)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(5).text()).contains("11.1", "22.2");


        verify(spyChatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(EXPECTED_SPECIFICATION)
        );

        verify(spyChatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2)),
                singletonList(EXPECTED_SPECIFICATION)
        );

        verify(spyChatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2), messages.get(3), messages.get(4)),
                singletonList(EXPECTED_SPECIFICATION)
        );
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_multiple_tools_in_parallel_then_answer(ChatLanguageModel chatLanguageModel) {

        TransactionService transactionService = spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatLanguageModel spyChatLanguageModel = spy(chatLanguageModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(spyChatLanguageModel)
                .chatMemory(chatMemory)
                .tools(transactionService)
                .build();

        String userMessage = "What are the amounts of transactions T001 and T002?";

        Response<AiMessage> response = assistant.chat(userMessage);

        assertThat(response.content().text()).contains("11.1", "22.2");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0); // TODO
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(transactionService).getTransactionAmount("T001");
        verify(transactionService).getTransactionAmount("T002");
        verifyNoMoreInteractions(transactionService);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(5);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest firstToolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(firstToolExecutionRequest.name()).isEqualTo("getTransactionAmount");
        assertThat(firstToolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": \"T001\"}");

        ToolExecutionRequest secondToolExecutionRequest = aiMessage.toolExecutionRequests().get(1);
        assertThat(secondToolExecutionRequest.name()).isEqualTo("getTransactionAmount");
        assertThat(secondToolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": \"T002\"}");

        ToolExecutionResultMessage firstToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(firstToolExecutionResultMessage.id()).isEqualTo(firstToolExecutionRequest.id());
        assertThat(firstToolExecutionResultMessage.toolName()).isEqualTo("getTransactionAmount");
        assertThat(firstToolExecutionResultMessage.text()).isEqualTo("11.1");

        ToolExecutionResultMessage secondToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(3);
        assertThat(secondToolExecutionResultMessage.id()).isEqualTo(secondToolExecutionRequest.id());
        assertThat(secondToolExecutionResultMessage.toolName()).isEqualTo("getTransactionAmount");
        assertThat(secondToolExecutionResultMessage.text()).isEqualTo("22.2");

        assertThat(messages.get(4)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(4).text()).contains("11.1", "22.2");


        verify(spyChatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(EXPECTED_SPECIFICATION)
        );

        verify(spyChatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2), messages.get(3)),
                singletonList(EXPECTED_SPECIFICATION)
        );
    }


    static class StringListProcessor {

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("processStrings")
                .description("Processes list of strings")
                .addParameter("arg0", ARRAY, items(STRING), description("List of strings to process"))
                .build();

        @Tool("Processes list of strings")
        void processStrings(@P("List of strings to process") List<String> strings) {
            System.out.printf("called processStrings(%s)%n", strings);
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_tool_with_List_of_Strings_parameter(ChatLanguageModel chatLanguageModel) {

        StringListProcessor stringListProcessor = spy(new StringListProcessor());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatLanguageModel spyChatLanguageModel = spy(chatLanguageModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(spyChatLanguageModel)
                .chatMemory(chatMemory)
                .tools(stringListProcessor)
                .build();

        String userMessage = "Process strings 'cat' and 'dog' together, do not separate them!. " +
                "Use format ['cat', 'dog'] for the list of strings."; // Specify the format expected to avoid ambiguity

        // when
        assistant.chat(userMessage);

        // then
        verify(stringListProcessor).processStrings(asList("cat", "dog"));
        verifyNoMoreInteractions(stringListProcessor);

        List<ChatMessage> messages = chatMemory.messages();
        verify(spyChatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(StringListProcessor.EXPECTED_SPECIFICATION)
        );
        verify(spyChatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2)),
                singletonList(StringListProcessor.EXPECTED_SPECIFICATION)
        );
    }


    static class IntegerListProcessor {

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("processIntegers")
                .description("Processes list of integers")
                .addParameter("arg0", ARRAY, items(INTEGER), description("List of integers to process"))
                .build();

        @Tool("Processes list of integers")
        void processIntegers(@P("List of integers to process") List<Integer> integers) {
            System.out.printf("called processIntegers(%s)%n", integers);
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    @Disabled
        // TODO fix: should automatically convert List<Double> into List<Integer>
    void should_use_tool_with_List_of_Integers_parameter(ChatLanguageModel chatLanguageModel) {

        IntegerListProcessor integerListProcessor = spy(new IntegerListProcessor());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatLanguageModel spyChatLanguageModel = spy(chatLanguageModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(spyChatLanguageModel)
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
        verify(spyChatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(IntegerListProcessor.EXPECTED_SPECIFICATION)
        );
        verify(spyChatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2)),
                singletonList(IntegerListProcessor.EXPECTED_SPECIFICATION)
        );
    }


    static class StringArrayProcessor {

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("processStrings")
                .description("Processes array of strings")
                .addParameter("arg0", ARRAY, items(STRING), description("Array of strings to process"))
                .build();

        @Tool("Processes array of strings")
        void processStrings(@P("Array of strings to process") String[] ids) {
            System.out.printf("called processStrings(%s)%n", Arrays.toString(ids));
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    @Disabled
        // TODO fix: should automatically convert List<String> into String[]
    void should_use_tool_with_Array_of_Strings_parameter(ChatLanguageModel chatLanguageModel) {

        StringArrayProcessor stringArrayProcessor = spy(new StringArrayProcessor());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatLanguageModel spyChatLanguageModel = spy(chatLanguageModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(spyChatLanguageModel)
                .chatMemory(chatMemory)
                .tools(stringArrayProcessor)
                .build();

        String userMessage = "Process strings 'cat' and 'dog' together, do not separate them!";

        // when
        assistant.chat(userMessage);

        // then
        verify(stringArrayProcessor).processStrings(new String[]{"cat", "dog"});
        verifyNoMoreInteractions(stringArrayProcessor);

        List<ChatMessage> messages = chatMemory.messages();
        verify(spyChatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(StringArrayProcessor.EXPECTED_SPECIFICATION)
        );
        verify(spyChatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2)),
                singletonList(StringArrayProcessor.EXPECTED_SPECIFICATION)
        );
    }


    static class WeatherService {

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("currentTemperature")
                .description("") // TODO should be null?
                .addParameter("arg0", STRING)
                .addParameter("arg1", STRING, from("enum", asList("CELSIUS", "fahrenheit", "Kelvin")))
                .build();

        @Tool
        int currentTemperature(String city, TemperatureUnit unit) {
            System.out.printf("called currentTemperature(%s, %s)%n", city, unit);
            return 42;
        }
    }

    enum TemperatureUnit {
        CELSIUS, fahrenheit, Kelvin
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_tool_with_enum_parameter(ChatLanguageModel chatLanguageModel) {

        // given
        WeatherService weatherService = spy(new WeatherService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatLanguageModel spyChatLanguageModel = spy(chatLanguageModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(spyChatLanguageModel)
                .chatMemory(chatMemory)
                .tools(weatherService)
                .build();

        // when
        Response<AiMessage> response = assistant.chat("What is the temperature in Munich now, in kelvin?");

        // then
        assertThat(response.content().text()).contains("42");

        verify(weatherService).currentTemperature("Munich", Kelvin);
        verifyNoMoreInteractions(weatherService);

        List<ChatMessage> messages = chatMemory.messages();
        verify(spyChatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(WeatherService.EXPECTED_SPECIFICATION)
        );
        verify(spyChatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2)),
                singletonList(WeatherService.EXPECTED_SPECIFICATION)
        );
    }

    // TODO test Lists, Sets, Arrays of different types (including enums).
}
