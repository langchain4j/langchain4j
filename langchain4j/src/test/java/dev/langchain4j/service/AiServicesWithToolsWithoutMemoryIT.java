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

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_3_5_TURBO_0613;
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
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(72 + 109); // TODO: failing, should we test token count? They may change in the future too
        assertThat(tokenUsage.outputTokenCount()).isCloseTo(20 + 31, withPercentage(5));
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
                // TODO: is is ok to use 4o-mini here? gpt-3.5-turbo-0613 deprecated, failing test
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
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(79 + 117 + 152); // TODO: failing, should we test token count? They may change in the future too
        assertThat(tokenUsage.outputTokenCount()).isCloseTo(21 + 20 + 53, withPercentage(5));
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
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(79 + 160); // TODO: failing, should we test token count? They may change in the future too
        assertThat(tokenUsage.outputTokenCount()).isCloseTo(54 + 58, withPercentage(5));
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);
    }
}
