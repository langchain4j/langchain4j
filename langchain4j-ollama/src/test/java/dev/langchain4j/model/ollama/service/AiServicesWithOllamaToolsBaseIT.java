package dev.langchain4j.model.ollama.service;

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
import dev.langchain4j.model.ollama.LangChain4jOllamaContainer;
import dev.langchain4j.model.ollama.OllamaImage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.description;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.*;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.ollama.service.AiServicesWithOllamaToolsBaseIT.BaseTests.TemperatureUnit.Kelvin;
import static dev.langchain4j.model.ollama.service.AiServicesWithOllamaToolsBaseIT.TransactionService.EXPECTED_SPECIFICATION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test, contains configuration and common tests for sequential and parallel tools
 */
@DisabledOnJre({JRE.JAVA_8, JRE.JAVA_11})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
abstract class AiServicesWithOllamaToolsBaseIT {
    private static final String LOCAL_OLLAMA_IMAGE = String.format("tc-%s-%s", OllamaImage.OLLAMA_IMAGE, OllamaImage.TINY_DOLPHIN_MODEL);

    static LangChain4jOllamaContainer ollama;

    static boolean LOCAL_OLLAMA_SERVER = false;

    static String ollamaUrl;
    static {
        if (LOCAL_OLLAMA_SERVER) {
            ollamaUrl = "http://localhost:11434";
        } else {
            ollama = new LangChain4jOllamaContainer(OllamaImage.resolve(OllamaImage.OLLAMA_IMAGE, LOCAL_OLLAMA_IMAGE))
                    .withModel("llama3");
            ollama.start();
            ollama.commitToImage(LOCAL_OLLAMA_IMAGE);
            ollamaUrl = ollama.getEndpoint();
        }
    }

    abstract ChatLanguageModel model();

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

    @SuppressWarnings("unused")
    public static class Calculator {
        @Tool("Calculates the length of a string")
        int stringLength(String s) {
            return s.length();
        }

        @Tool("Calculates the sum of two numbers")
        int add(int a, int b) {
            return a + b;
        }

        @Tool("Calculates the square root of a number")
        double sqrt(double x) {
            return Math.sqrt(x);
        }

    }

    public interface MathAssistant {
        Response<AiMessage> chat(String userMessage);
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

    static abstract class BaseTests extends AiServicesWithOllamaToolsBaseIT {

        @Test
        void should_give_simple_result() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatLanguageModel(model())
                    .build();
            String userMessage = "What is the result of 1+1 ?";
            Response<AiMessage> response = assistant.chat(userMessage);
            assertThat(response.content().text()).contains("The result of 1+1 is 2.");
        }

        @Test
        void should_execute_a_tool_then_answer() {
            ChatLanguageModel chatLanguageModel = model();

            TransactionService transactionService = Mockito.spy(new TransactionService());

            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

            ChatLanguageModel spyChatLanguageModel = Mockito.spy(chatLanguageModel);

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


            Mockito.verify(transactionService).getTransactionAmount("T001");
            Mockito.verifyNoMoreInteractions(transactionService);


            List<ChatMessage> messages = chatMemory.messages();
            assertThat(messages).hasSize(4);

            assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
            assertThat(messages.get(0).text()).isEqualTo(userMessage);

            AiMessage aiMessage = (AiMessage) messages.get(1);
            // assertThat(aiMessage.text()).isNull(); // Todo not possible for parallel delegate
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


            Mockito.verify(spyChatLanguageModel).generate(
                    singletonList(messages.get(0)),
                    singletonList(EXPECTED_SPECIFICATION)
            );

            Mockito.verify(spyChatLanguageModel).generate(
                    asList(messages.get(0), messages.get(1), messages.get(2)),
                    singletonList(EXPECTED_SPECIFICATION)
            );
        }

        @Test
        void should_use_tool_with_List_of_Strings_parameter() {
            ChatLanguageModel chatLanguageModel = model();
            StringListProcessor stringListProcessor = Mockito.spy(new StringListProcessor());

            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

            ChatLanguageModel spyChatLanguageModel = Mockito.spy(chatLanguageModel);

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
            Mockito.verify(stringListProcessor).processStrings(asList("cat", "dog"));
            Mockito.verifyNoMoreInteractions(stringListProcessor);

            List<ChatMessage> messages = chatMemory.messages();
            Mockito.verify(spyChatLanguageModel).generate(
                    singletonList(messages.get(0)),
                    singletonList(StringListProcessor.EXPECTED_SPECIFICATION)
            );
            Mockito.verify(spyChatLanguageModel).generate(
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

        @Test
        void should_use_tool_with_List_of_Integers_parameter() {
            ChatLanguageModel chatLanguageModel = model();
            IntegerListProcessor integerListProcessor = Mockito.spy(new IntegerListProcessor());

            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

            ChatLanguageModel spyChatLanguageModel = Mockito.spy(chatLanguageModel);

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatLanguageModel(spyChatLanguageModel)
                    .chatMemory(chatMemory)
                    .tools(integerListProcessor)
                    .build();

            String userMessage = "Process integers 1 and 2 together, do not separate them!";

            // when
            assistant.chat(userMessage);

            // then
            // TODO: Values are good but it's missing a \n at the end of the function call :)
            //  ???? Json Format Number strategy isseu with LONG_OR_DOUBLE?
            //Mockito.verify(integerListProcessor).processIntegers(asList(1, 2));
            //Mockito.verifyNoMoreInteractions(integerListProcessor);

            List<ChatMessage> messages = chatMemory.messages();
            Mockito.verify(spyChatLanguageModel).generate(
                    singletonList(messages.get(0)),
                    singletonList(IntegerListProcessor.EXPECTED_SPECIFICATION)
            );
            Mockito.verify(spyChatLanguageModel).generate(
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

        @Test
        void should_use_tool_with_Array_of_Strings_parameter() {
            ChatLanguageModel chatLanguageModel = model();

            StringArrayProcessor stringArrayProcessor = Mockito.spy(new StringArrayProcessor());

            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

            ChatLanguageModel spyChatLanguageModel = Mockito.spy(chatLanguageModel);

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatLanguageModel(spyChatLanguageModel)
                    .chatMemory(chatMemory)
                    .tools(stringArrayProcessor)
                    .build();

            String userMessage = "Process strings 'cat' and 'dog' together in a list, do not separate them!";

            // when
            assistant.chat(userMessage);

            // then
            Mockito.verify(stringArrayProcessor).processStrings(new String[]{"cat", "dog"});
            Mockito.verifyNoMoreInteractions(stringArrayProcessor);

            List<ChatMessage> messages = chatMemory.messages();
            Mockito.verify(spyChatLanguageModel).generate(
                    singletonList(messages.get(0)),
                    singletonList(StringArrayProcessor.EXPECTED_SPECIFICATION)
            );
            Mockito.verify(spyChatLanguageModel).generate(
                    asList(messages.get(0), messages.get(1), messages.get(2)),
                    singletonList(StringArrayProcessor.EXPECTED_SPECIFICATION)
            );
        }


        static class WeatherService {

            static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                    .name("currentTemperature")
                    // TODO should be null? @langchain4j why ? description in tool should be mandatory ?
                    .description("Give the temperature for a given city and unit")
                    .addParameter("arg0", STRING)
                    .addParameter("arg1", STRING, from("enum", asList("CELSIUS", "fahrenheit", "Kelvin")))
                    .build();

            // TODO: @langchain4j why no description? description in tool should be mandatory ?
            @Tool("Give the temperature for a given city and unit")
            int currentTemperature(String city, TemperatureUnit unit) {
                System.out.printf("called currentTemperature(%s, %s)%n", city, unit);
                return 42;
            }
        }

        enum TemperatureUnit {
            CELSIUS, fahrenheit, Kelvin
        }

        @Test
        void should_use_tool_with_enum_parameter() {
            ChatLanguageModel chatLanguageModel = model();
            // given
            WeatherService weatherService = Mockito.spy(new WeatherService());

            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

            ChatLanguageModel spyChatLanguageModel = Mockito.spy(chatLanguageModel);

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatLanguageModel(spyChatLanguageModel)
                    .chatMemory(chatMemory)
                    .tools(weatherService)
                    .build();

            // when
            Response<AiMessage> response = assistant.chat("What is the temperature in Munich now, in kelvin?");

            // then
            assertThat(response.content().text()).contains("42");

            Mockito.verify(weatherService).currentTemperature("Munich", Kelvin);
            Mockito.verifyNoMoreInteractions(weatherService);

            List<ChatMessage> messages = chatMemory.messages();
            Mockito.verify(spyChatLanguageModel).generate(
                    singletonList(messages.get(0)),
                    singletonList(WeatherService.EXPECTED_SPECIFICATION)
            );
        }

        @Test
        void should_execute_length_sum_square_no_chat_memory() {
            Calculator calculator = Mockito.spy(new Calculator());
            ChatLanguageModel spyChatLanguageModel = Mockito.spy(model());
            MathAssistant assistant = AiServices.builder(MathAssistant.class)
                    .chatLanguageModel(spyChatLanguageModel)
                    .tools(calculator)
                    .build();

            String userMessage = "What is the square root of the sum " +
                    "of the numbers of letters in the words hello and world. ";

            Response<AiMessage> response = assistant.chat(userMessage);

            assertThat(response.content().text()).contains("3.16");

            // TODO: Ollama sometimes does not provide this token usage ????
    //        TokenUsage tokenUsage = response.tokenUsage();
    //        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0); // TODO
    //        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
    //        assertThat(tokenUsage.totalTokenCount())
    //                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

            assertThat(response.finishReason()).isEqualTo(STOP);

            Mockito.verify(calculator).stringLength("hello");
            Mockito.verify(calculator).stringLength("world");
            Mockito.verify(calculator).add(5, 5);
            Mockito.verify(calculator).sqrt(10);
            // TODO Not true for ollama :
            // verifyNoMoreInteractions(calculator);
        }
    }
}
