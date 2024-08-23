package dev.langchain4j.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServicesWithToolsWithoutMemoryIT {

    @Spy
    ChatLanguageModel chatLanguageModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    interface Assistant {

        Response<AiMessage> chat(String userMessage);
    }

    static class Calculator {

        @Tool("calculates the square root of the provided number")
        double squareRoot(@P("number to operate on") double number) {
            System.out.printf("called squareRoot(%s)%n", number);
            return Math.sqrt(number);
        }
    }

    @Test
    void should_execute_a_tool_then_answer() {

        Calculator calculator = spy(new Calculator());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        Response<AiMessage> response = assistant.chat(userMessage);

        assertThat(response.content().text()).contains("6.97");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verifyNoMoreInteractions(calculator);
    }

    @Test
    void should_execute_multiple_tools_sequentially_then_answer() {

        ChatLanguageModel chatLanguageModel = spy(OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .parallelToolCalls(false) // to force the model to call tools sequentially
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());

        Calculator calculator = spy(new Calculator());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        Response<AiMessage> response = assistant.chat(userMessage);

        assertThat(response.content().text()).contains("6.97", "9.89");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(77 + 114 + 148);
        assertThat(tokenUsage.outputTokenCount()).isCloseTo(20 + 19 + 68, withPercentage(5));
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);
    }

    @Test
    void should_execute_multiple_tools_in_parallel_then_answer() {

        Calculator calculator = spy(new Calculator());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        Response<AiMessage> response = assistant.chat(userMessage);

        assertThat(response.content().text()).contains("6.97", "9.89");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);
    }
}
