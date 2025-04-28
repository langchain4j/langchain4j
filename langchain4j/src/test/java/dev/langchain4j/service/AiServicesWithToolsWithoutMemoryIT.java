package dev.langchain4j.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AiServicesWithToolsWithoutMemoryIT {

    @Spy
    ChatModel chatModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    interface Assistant {

        Result<String> chat(String userMessage);
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
                .chatModel(chatModel)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        Result<String> result = assistant.chat(userMessage);

        assertThat(result.content()).contains("6.97");

        TokenUsage tokenUsage = result.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(result.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verifyNoMoreInteractions(calculator);
    }

    @Test
    void should_execute_multiple_tools_sequentially_then_return_result_with_accumulated_token_usage() {

        // given
        ChatModel chatModel = spy(OpenAiChatModel.builder()
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
                .chatModel(chatModel)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        // when
        Result<String> result = assistant.chat(userMessage);

        // then
        assertThat(result.content()).contains("6.97", "9.89");

        TokenUsage tokenUsage = result.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(77 + 114 + 148);
        assertThat(tokenUsage.outputTokenCount()).isCloseTo(20 + 19 + 68, withPercentage(5));
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(result.finishReason()).isEqualTo(STOP);

        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);
    }

    @Test
    void should_execute_multiple_tools_sequentially_then_return_legacy_response_with_accumulated_token_usage() {

        // given
        interface Assistant {

            Response<AiMessage> chat(String userMessage);
        }

        ChatModel chatModel = spy(OpenAiChatModel.builder()
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
                .chatModel(chatModel)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        // when
        Response<AiMessage> response = assistant.chat(userMessage);

        // then
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
                .chatModel(chatModel)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        Result<String> result = assistant.chat(userMessage);

        assertThat(result.content()).contains("6.97", "9.89");

        TokenUsage tokenUsage = result.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(result.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);
    }
}
