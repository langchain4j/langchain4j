package dev.langchain4j.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.description;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.*;
import static dev.langchain4j.service.StreamingAiServicesWithToolsIT.TransactionService.EXPECTED_SPECIFICATION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StreamingAiServicesWithToolsIT {

    static Stream<StreamingChatLanguageModel> models() {
        return Stream.of(
                OpenAiStreamingChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build(),
                MistralAiStreamingChatModel.builder()
                        .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                        .modelName("mistral-large-latest")
                        .logRequests(true)
                        .logResponses(true)
                        .build()
                // Add your AzureOpenAiChatModel instance here...
                // Add your GeminiChatModel instance here...
        );
    }

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    static class TransactionService {

        static ToolSpecification EXPECTED_SPECIFICATION = ToolSpecification.builder()
                .name("getTransactionAmounts")
                .description("returns amounts of transactions")
                .addParameter("arg0", ARRAY, items(STRING), description("IDs of transactions"))
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
        Response<AiMessage> response = future.get(60, TimeUnit.SECONDS);

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

    // TODO all other tests from sync version
}
