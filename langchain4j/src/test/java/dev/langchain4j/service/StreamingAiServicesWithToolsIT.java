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
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.ARRAY;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.STRING;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.description;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.from;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.items;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_LARGE_LATEST;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.StreamingAiServicesWithToolsIT.TemperatureUnit.CELSIUS;
import static dev.langchain4j.model.chat.request.json.JsonStringSchema.JSON_STRING_SCHEMA;
import static dev.langchain4j.service.StreamingAiServicesWithToolsIT.TransactionService.EXPECTED_SPECIFICATION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class StreamingAiServicesWithToolsIT {

    static Stream<StreamingChatLanguageModel> models() {
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
                .name("getTransactionAmounts")
                .description("returns amounts of transactions")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("arg0", JsonArraySchema.builder()
                                .items(JSON_STRING_SCHEMA)
                                .description("IDs of transactions")
                                .build())
                        .required("arg0")
                        .build())
                .build();

        @Tool("returns amounts of transactions")
        List<Double> getTransactionAmounts(@P("IDs of transactions") List<String> ids) {
            System.out.printf("called getTransactionAmounts(%s)%n", ids);
            return ids.stream().map(id -> {
                switch (id) {
                    case "T001":
                        return 42.0;
                    case "T002":
                        return 57.0;
                    default:
                        throw new IllegalArgumentException("Unknown transaction ID: " + id);
                }
            }).collect(toList());
        }
    }

    static class TransactionServiceExecutor implements ToolExecutor {
        private final TransactionService transactionService = new TransactionService();

        @Override
        public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
            Map<String, Object> arguments = toMap(toolExecutionRequest.arguments());
            return transactionService.getTransactionAmounts((List<String>) arguments.get("arg0")).toString();
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_tool_with_List_of_Strings_parameter(StreamingChatLanguageModel model) throws Exception {

        // given
        TransactionService transactionService = spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        StreamingChatLanguageModel spyModel = spy(model);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(spyModel)
                .chatMemory(chatMemory)
                .tools(transactionService)
                .build();

        String userMessage = "What are the amounts of transactions T001 and T002?";

        // when
        CompletableFuture<Response<AiMessage>> future = new CompletableFuture<>();
        assistant.chat(userMessage)
                .onNext(token -> {
                })
                .onComplete(future::complete)
                .onError(future::completeExceptionally)
                .start();
        Response<AiMessage> response = future.get(60, SECONDS);

        // then
        assertThat(response.content().text()).contains("42", "57");

        // then
        verify(transactionService).getTransactionAmounts(asList("T001", "T002"));
        verifyNoMoreInteractions(transactionService);

        // then
        List<ChatMessage> messages = chatMemory.messages();
        verify(spyModel).generate(
                eq(singletonList(messages.get(0))),
                eq(singletonList(EXPECTED_SPECIFICATION)),
                any()
        );
        verify(spyModel).generate(
                eq(asList(messages.get(0), messages.get(1), messages.get(2))),
                eq(singletonList(EXPECTED_SPECIFICATION)),
                any()
        );
    }

    static class WeatherService {

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("currentTemperature")
                .description("")
                // TODO
                .addParameter("arg0", STRING)
                .addParameter("arg1", STRING, from("enum", asList("CELSIUS", "fahrenheit", "Kelvin")))
                .build();

        @Tool
        int currentTemperature(String city, TemperatureUnit unit) {
            System.out.printf("called currentTemperature(%s, %s)%n", city, unit);
            return 19;
        }
    }

    enum TemperatureUnit {
        CELSIUS, fahrenheit, Kelvin
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_use_tool_with_enum_parameter(StreamingChatLanguageModel model) throws Exception {

        // given
        WeatherService weatherService = spy(new WeatherService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        StreamingChatLanguageModel spyModel = spy(model);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(spyModel)
                .chatMemory(chatMemory)
                .tools(weatherService)
                .build();

        String userMessage = "What is the temperature in Munich now, in Celsius?";

        // when
        CompletableFuture<Response<AiMessage>> future = new CompletableFuture<>();
        assistant.chat(userMessage)
                .onNext(token -> {
                })
                .onComplete(future::complete)
                .onError(future::completeExceptionally)
                .start();
        Response<AiMessage> response = future.get(60, SECONDS);

        // then
        assertThat(response.content().text()).contains("19");

        verify(weatherService).currentTemperature("Munich", CELSIUS);
        verifyNoMoreInteractions(weatherService);

        List<ChatMessage> messages = chatMemory.messages();
        verify(spyModel).generate(
                eq(singletonList(messages.get(0))),
                eq(singletonList(WeatherService.EXPECTED_SPECIFICATION)),
                any()
        );
        verify(spyModel).generate(
                eq(asList(messages.get(0), messages.get(1), messages.get(2))),
                eq(singletonList(WeatherService.EXPECTED_SPECIFICATION)),
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

        StreamingChatLanguageModel spyModel = spy(models().findFirst().get());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(spyModel)
                .chatMemory(chatMemory)
                .toolProvider(toolProvider)
                .build();

        String userMessage = "What is the amounts of transactions T001?";

        // when
        CompletableFuture<Response<AiMessage>> future = new CompletableFuture<>();
        assistant.chat(userMessage)
                .onNext(token -> {
                })
                .onComplete(future::complete)
                .onError(future::completeExceptionally)
                .start();
        Response<AiMessage> response = future.get(60, SECONDS);

        // then
        assertThat(response.content().text()).contains("42");

        // then
        verify(toolExecutor).execute(any(), any());
        verifyNoMoreInteractions(toolExecutor);

        // then
        List<ChatMessage> messages = chatMemory.messages();
        verify(spyModel).generate(
                eq(singletonList(messages.get(0))),
                eq(singletonList(EXPECTED_SPECIFICATION)),
                any()
        );
        verify(spyModel).generate(
                eq(asList(messages.get(0), messages.get(1), messages.get(2))),
                eq(singletonList(EXPECTED_SPECIFICATION)),
                any()
        );
        verifyNoMoreInteractions(spyModel);
    }

    private static Map<String, Object> toMap(String arguments) {
        try {
            return new ObjectMapper().readValue(arguments, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO all other tests from sync version
}
