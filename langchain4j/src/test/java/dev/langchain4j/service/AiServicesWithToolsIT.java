package dev.langchain4j.service;

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
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.ARRAY;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.STRING;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.description;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.from;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.items;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.type;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.request.json.JsonBooleanSchema.JSON_BOOLEAN_SCHEMA;
import static dev.langchain4j.model.chat.request.json.JsonIntegerSchema.JSON_INTEGER_SCHEMA;
import static dev.langchain4j.model.chat.request.json.JsonNumberSchema.JSON_NUMBER_SCHEMA;
import static dev.langchain4j.model.chat.request.json.JsonStringSchema.JSON_STRING_SCHEMA;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_LARGE_LATEST;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_3_5_TURBO_0613;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.service.AiServicesWithToolsIT.Operator.EQUALS;
import static dev.langchain4j.service.AiServicesWithToolsIT.TemperatureUnit.Kelvin;
import static dev.langchain4j.service.AiServicesWithToolsIT.TransactionService.EXPECTED_SPECIFICATION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class AiServicesWithToolsIT {

    static Stream<ChatLanguageModel> models() {
        return Stream.of(
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O)
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
                        .build()
        );
    }

    interface Assistant {

        Response<AiMessage> chat(String userMessage);
    }

    // TODO cover all cases similar to AiServicesJsonSchemaIT and AiServicesJsonSchemaWithDescriptionsIT
    // TODO no arguments
    // TODO single argument: primitives, enum, pojo with primitives, pojo with pojos, map?
    // TODO single argument: List/Set/Array of primitives, List/Set/Array of enums, List/Set/Array of POJOs, map?
    // TODO multiple arguments
    // TODO with descriptions, including @Description

    static class Tools1 { // TODO name

        static class Person {

            String name;
            int age;
            Double height;
            boolean married;
        }

        @Tool
        void process(Person person) {
            assertThat(person.name).isEqualTo("Klaus");
            assertThat(person.age).isEqualTo(37);
            assertThat(person.height).isEqualTo(1.78);
            assertThat(person.married).isFalse();
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_call_tool_with_pojo_with_primitives(ChatLanguageModel model) {

        // given
        model = spy(model);

        Tools1 tools = spy(new Tools1());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(tools)
                .build();

        String text = " Klaus is 37 years old, 1.78m height and single";

        // when
        assistant.chat(text);

        // then
        verify(tools).process(any());
        verifyNoMoreInteractions(tools);

        verify(model).supportedCapabilities();
        verify(model).generate(singletonList(userMessage(text)), singletonList(ToolSpecification.builder()
                .name("process")
                .parameters(JsonObjectSchema.builder()
                        .properties(singletonMap("arg0", JsonObjectSchema.builder()
                                .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                                    put("name", JSON_STRING_SCHEMA);
                                    put("age", JSON_INTEGER_SCHEMA);
                                    put("height", JSON_NUMBER_SCHEMA);
                                    put("married", JSON_BOOLEAN_SCHEMA);
                                }})
                                .required("name", "age", "height", "married")
                                .additionalProperties(false)
                                .build()))
                        .required("arg0")
                        .additionalProperties(false)
                        .build())
                .build()));
        verifyNoMoreInteractions(model);
    }


    static class TransactionService {

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("getTransactionAmount")
                .description("returns amount of a given transaction")
                .parameters(JsonObjectSchema.builder()
                        .properties(singletonMap("arg0", JsonStringSchema.withDescription("ID of a transaction")))
                        .required("arg0")
                        .build())
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
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
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
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
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
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
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
                .parameters(JsonObjectSchema.builder()
                        .properties(singletonMap("arg0", JsonArraySchema.builder()
                                .description("List of strings to process")
                                .items(JSON_STRING_SCHEMA)
                                .build()))
                        .required("arg0")
                        .build())
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
                .parameters(JsonObjectSchema.builder()
                        .properties(singletonMap("arg0", JsonArraySchema.builder()
                                .description("List of integers to process")
                                .items(JSON_INTEGER_SCHEMA)
                                .build()))
                        .build())
                .addParameter("arg0", ARRAY, items(INTEGER), description("List of integers to process"))
                .build();

        @Tool("Processes list of integers")
        void processIntegers(@P("List of integers to process") List<Integer> integers) {
            System.out.printf("called processIntegers(%s)%n", integers);
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    @Disabled("should be enabled once List<Double> is automatically converted into List<Integer>")
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
                .parameters(JsonObjectSchema.builder()
                        .properties(singletonMap("arg0", JsonArraySchema.builder()
                                .description("Array of strings to process")
                                .items(JSON_STRING_SCHEMA)
                                .build()))
                        .required("arg0")
                        .build())
                .addParameter("arg0", ARRAY, items(STRING), description("Array of strings to process"))
                .build();

        @Tool("Processes array of strings")
        void processStrings(@P("Array of strings to process") String[] ids) {
            System.out.printf("called processStrings(%s)%n", Arrays.toString(ids));
        }
    }

    @ParameterizedTest
    @MethodSource("models")
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
                .parameters(JsonObjectSchema.builder()
                        .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                            put("arg0", JSON_STRING_SCHEMA);
                            put("arg1", JsonEnumSchema.builder()
                                    .enumValues("CELSIUS", "fahrenheit", "Kelvin")
                                    .build());
                        }})
                        .required("arg0", "arg1")
                        .build())
                .addParameter("arg0", STRING)
                .addParameter("arg1", STRING, from("enum", asList("CELSIUS", "fahrenheit", "Kelvin")))
                .build();

        @Tool
        int currentTemperature(String city, TemperatureUnit unit) {
            System.out.printf("called currentTemperature(%s, %s)%n", city, unit);
            return 37;
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
        assertThat(response.content().text()).contains("37");

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


    static class QueryService {

        @Tool("Execute the query and return the result")
        String executeQuery(@P("query to execute") Query query) {
            assertThat(query).isNotNull();
            assertThat(query.select).containsExactly("name");
            assertThat(query.where).containsExactly(new Condition("country", EQUALS, "India"));
            assertThat(query.limit).isEqualTo(3);

            return "Amar, Akbar, Antony";
        }
    }

    @Data
    static class Query {

        @Description("List of fields to fetch records")
        List<String> select;

        @Description("List of conditions to filter on. Pass null if no condition")
        List<Condition> where;

        @Description("limit on number of records")
        Integer limit;

        @Description("offset for fetching records")
        Integer offset;
    }

    @Data
    @AllArgsConstructor
    static class Condition {

        @Description("Field to filter on")
        String field;

        @Description("Operator to apply")
        Operator operator;

        @Description("Value to compare with")
        String value;
    }

    enum Operator {

        EQUALS,
        NOT_EQUALS,
        IS_NULL,
        IS_NOT_NULL
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_tool_with_pojo(ChatLanguageModel chatLanguageModel) {

        // given
        QueryService queryService = spy(new QueryService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatLanguageModel spyChatLanguageModel = spy(chatLanguageModel);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(spyChatLanguageModel)
                .chatMemory(chatMemory)
                .tools(queryService)
                .build();

        Response<AiMessage> response = assistant.chat("List names of 3 users where country is India");

        assertThat(response.content().text()).contains("Amar", "Akbar", "Antony");
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_programmatically_configured_tools(ChatLanguageModel chatLanguageModel) {

        // given
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("get_booking_details")
                .description("Returns booking details")
                .parameters(JsonObjectSchema.builder()
                        .properties(singletonMap("bookingNumber", JSON_STRING_SCHEMA))
                        .build())
                .build();

        ToolExecutor toolExecutor = (toolExecutionRequest, memoryId) -> {
            Map<String, Object> arguments = toMap(toolExecutionRequest.arguments());
            assertThat(arguments).containsExactly(entry("bookingNumber", "123-456"));
            return "Booking period: from 1 July 2027 to 10 July 2027";
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(singletonMap(toolSpecification, toolExecutor))
                .build();

        // when
        Response<AiMessage> response = assistant.chat("When does my booking 123-456 starts?");

        // then
        assertThat(response.content().text()).contains("2027");
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_programmatically_configured_tools_old(ChatLanguageModel chatLanguageModel) {

        // given
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("get_booking_details")
                .description("Returns booking details")
                .addParameter("bookingNumber", type("string")) // old
                .build();

        ToolExecutor toolExecutor = (toolExecutionRequest, memoryId) -> {
            Map<String, Object> arguments = toMap(toolExecutionRequest.arguments());
            assertThat(arguments).containsExactly(entry("bookingNumber", "123-456"));
            return "Booking period: from 1 July 2027 to 10 July 2027";
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(singletonMap(toolSpecification, toolExecutor))
                .build();

        // when
        Response<AiMessage> response = assistant.chat("When does my booking 123-456 starts?");

        // then
        assertThat(response.content().text()).contains("2027");
    }

    static class BookingToolExecutor implements ToolExecutor {

        @Override
        public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
            Map<String, Object> arguments = toMap(toolExecutionRequest.arguments());
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
                        .addParameter("bookingNumber", type("string"))
                        .build();
                return ToolProviderResult.builder()
                        .add(toolSpecification, toolExecutor)
                        .build();
            } else {
                return null;
            }
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(models().findFirst().get())
                .toolProvider(toolProvider)
                .build();

        assistant.chat("When does my holiday 123-456 starts?");
        verifyNoInteractions(toolExecutor); // user message does not contain word "booking"

        Response<AiMessage> response = assistant.chat("When does my booking 123-456 starts?");
        assertThat(response.content().text()).contains("2027");
        verify(toolExecutor).execute(any(), any());
        verifyNoMoreInteractions(toolExecutor);
    }

    @Test
    void should_not_allow_configuring_tools_and_tool_provider_simultaneously() {

        ChatLanguageModel chatLanguageModel = new ChatModelMock("mocked");

        // First provider then tools
        assertThrows(IllegalArgumentException.class, () ->
                AiServices.builder(Assistant.class)
                        .chatLanguageModel(chatLanguageModel)
                        .toolProvider((ToolProviderRequest request) -> null)
                        .tools(new HashMap<>())
                        .build()
        );

        // First provider then static tools
        assertThrows(IllegalArgumentException.class, () ->
                AiServices.builder(Assistant.class)
                        .chatLanguageModel(chatLanguageModel)
                        .toolProvider((ToolProviderRequest request) -> null)
                        .tools(new StringArrayProcessor())
                        .build()
        );

        // First tools then provider
        assertThrows(IllegalArgumentException.class, () ->
                AiServices.builder(Assistant.class)
                        .chatLanguageModel(chatLanguageModel)
                        .tools(new HashMap<>())
                        .toolProvider((ToolProviderRequest request) -> null)
                        .build()
        );

        // First static tools then provider
        assertThrows(IllegalArgumentException.class, () ->
                AiServices.builder(Assistant.class)
                        .chatLanguageModel(chatLanguageModel)
                        .tools(new StringArrayProcessor())
                        .toolProvider((ToolProviderRequest request) -> null)
                        .build()
        );
    }

    private static Map<String, Object> toMap(String arguments) {
        try {
            return new ObjectMapper().readValue(arguments, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    static class Clock {

        @Tool
        String currentTime() {
            return "16:37:43";
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_tool_without_parameters(ChatLanguageModel chatLanguageModel) {

        // given
        Clock clock = spy(new Clock());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(clock)
                .build();

        // when
        Response<AiMessage> response = assistant.chat("What is the time now?");

        // then
        assertThat(response.content().text()).contains("16:37:43");
    }


    interface AssistantReturningResult {

        Result<AiMessage> chat(String userMessage);
    }

    @ParameterizedTest
    @MethodSource("models")
    public void should_execute_a_tool_and_context_included_in_result(ChatLanguageModel chatLanguageModel) {

        // given
        TransactionService transactionService = spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatLanguageModel spyChatLanguageModel = spy(chatLanguageModel);

        AssistantReturningResult assistant = AiServices.builder(AssistantReturningResult.class)
                .chatLanguageModel(spyChatLanguageModel)
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

        verify(spyChatLanguageModel, times(2)).generate(anyList(), anyList());
    }


    @ParameterizedTest
    @MethodSource("models")
    public void should_execute_multi_tool_in_parallel_and_context_included_in_result(ChatLanguageModel chatLanguageModel) {

        // given
        TransactionService transactionService = spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatLanguageModel spyChatLanguageModel = spy(chatLanguageModel);

        AssistantReturningResult assistant = AiServices.builder(AssistantReturningResult.class)
                .chatLanguageModel(spyChatLanguageModel)
                .chatMemory(chatMemory)
                .tools(transactionService)
                .build();

        String userMessage = "What are the amounts of transactions T001 and T002? " +
                "First call getTransactionAmount for T001, then for T002. Do not answer before you know all amounts!";

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

        verify(spyChatLanguageModel, times(2)).generate(anyList(), anyList());
    }

    @ParameterizedTest
    @MethodSource("modelsWithoutParallelToolCalling")
    public void should_execute_multiple_tools_sequentially_and_context_included_in_result(ChatLanguageModel chatLanguageModel) {

        // given
        TransactionService transactionService = spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatLanguageModel spyChatLanguageModel = spy(chatLanguageModel);

        AssistantReturningResult assistant = AiServices.builder(AssistantReturningResult.class)
                .chatLanguageModel(spyChatLanguageModel)
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

        verify(spyChatLanguageModel, times(3)).generate(anyList(), anyList());
    }
}
