package dev.langchain4j.model.ollama.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.LangChain4jOllamaContainer;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaImage;
import dev.langchain4j.model.ollama.tool.ExperimentalTools;
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

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.model.ollama.service.AiServicesWithOllamaToolsBaseIT.TransactionService.EXPECTED_SPECIFICATION;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;


@DisabledOnJre({JRE.JAVA_8, JRE.JAVA_11})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
class OllamaToolsSimpleIT {
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

    interface Assistant {
        Response<AiMessage> chat(String userMessage);
    }

    static class TransactionService {
        @Tool("returns amount of a given transaction")
        double getTransactionAmount(@P("ID of a transaction") String id) {
            System.out.printf("called getTransactionAmount(%s)%n", id);
            return switch (id) {
                case "T001" -> 11.1;
                case "T002" -> 22.2;
                default -> throw new IllegalArgumentException("Unknown transaction ID: " + id);
            };
        }
    }


    @Test
    void should_execute_a_tool_then_answer() {
        ChatLanguageModel chatLanguageModel = OllamaChatModel.builder()
                .baseUrl(ollamaUrl)
                .modelName("llama3")
                .temperature(0.0)
                .topK(40)
                .topP(0.9)
                .numCtx(2048)
                .numPredict(2048)
                .logRequests(true)
                .logResponses(true)
                .experimentalTools(ExperimentalTools.PARALLEL)
                .timeout(Duration.ofMinutes(5))
                .build();

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

}
