package dev.langchain4j.model.ollama.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.ollama.service.AiServicesWithOllamaToolsBaseIT.TransactionService.EXPECTED_SPECIFICATION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
abstract class AiServicesWithOllamaToolsSequentialIT extends AiServicesWithOllamaToolsBaseIT.BaseTests {

    @Test
    void should_execute_length_sum_square() {
        Calculator calculator = Mockito.spy(new Calculator());
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);
        ChatLanguageModel spyChatLanguageModel = Mockito.spy(model());
        MathAssistant assistant = AiServices.builder(MathAssistant.class)
                .chatLanguageModel(spyChatLanguageModel)
                .chatMemory(chatMemory)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of the sum " +
                "of the numbers of letters in the words hello and world. ";

        Response<AiMessage> response = assistant.chat(userMessage);

        assertThat(response.content().text()).contains("3.16");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);

        Mockito.verify(calculator).stringLength("hello");
        Mockito.verify(calculator).stringLength("world");
        Mockito.verify(calculator).add(5, 5);
        Mockito.verify(calculator).sqrt(10);
        // TODO Not true for ollama : first answer is weird
        // verifyNoMoreInteractions(calculator);

        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(12);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        // TODO we start at 3 because first llm answer is very weird
        int i = 3;

        AiMessage aiMessage = (AiMessage) messages.get(i);
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("stringLength");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"arg0\": \"hello\"}");

        ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(i+1);
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("stringLength");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("5");

        aiMessage = (AiMessage) messages.get(i+2);
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("stringLength");
        assertThat(toolExecutionRequest.arguments())
                .isEqualToNormalizingPunctuationAndWhitespace("{\"arg0\": \"world\"}");

        toolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(i+3);
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("stringLength");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("5");

        aiMessage = (AiMessage) messages.get(i+4);
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("add");
        assertThat(toolExecutionRequest.arguments().replace("\n", ""))
                // Should be 10, but ints are converted into doubles
                // @langchain, here is an example of Gson int to double conversion impact
                .isEqualToIgnoringWhitespace("{\"arg0\": 5.0, \"arg1\": 5.0}");

        toolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(i+5);
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("add");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("10");

        aiMessage = (AiMessage) messages.get(i+6);
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("sqrt");
        assertThat(toolExecutionRequest.arguments())
                // Should be 10, but ints are converted into doubles
                .isEqualToIgnoringWhitespace("{\"arg0\": 10.0}");

        toolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(i+7);
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("sqrt");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("3.1622776601683795");

        AiMessage secondAiMessage = (AiMessage) messages.get(i+8);
        assertThat(secondAiMessage.text()).contains("3.16");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();
    }

    @Test
    void should_execute_multiple_tools_sequentially_then_answer() {
        ChatLanguageModel chatLanguageModel = model();
        TransactionService transactionService = Mockito.spy(new TransactionService());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatLanguageModel spyChatLanguageModel = Mockito.spy(chatLanguageModel);

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


        Mockito.verify(transactionService).getTransactionAmount("T001");
        Mockito.verify(transactionService).getTransactionAmount("T002");
        Mockito.verifyNoMoreInteractions(transactionService);


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


        Mockito.verify(spyChatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(EXPECTED_SPECIFICATION)
        );

        Mockito.verify(spyChatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2)),
                singletonList(EXPECTED_SPECIFICATION)
        );

        Mockito.verify(spyChatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2), messages.get(3), messages.get(4)),
                singletonList(EXPECTED_SPECIFICATION)
        );
    }
}
