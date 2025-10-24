package dev.langchain4j.service.tool;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

public class HallucinatedToolNameStrategyTest {

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    @Test // HallucinatedToolNameStrategy.THROW_EXCEPTION
    void should_throw_exception() {
        final ExecutionException assertedThrows = assertThrows(ExecutionException.class, () -> {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name("browser_use") // Mock the wrong tool name and arguments
                    .arguments("action")
                    .build();
            AiMessage aiMessage = AiMessage.from("invoke tool..", Lists.list(toolExecutionRequest));
            StreamingChatModelMock model = new StreamingChatModelMock(Lists.list(aiMessage));

            Assistant assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(model)
                    .tools(new Calculator())
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    // Use the default tool hallucination strategy
                    .hallucinatedToolNameStrategy(HallucinatedToolNameStrategy.THROW_EXCEPTION)
                    .build();

            StringBuilder answerBuilder = new StringBuilder();
            CompletableFuture<String> futureAnswer = new CompletableFuture<>();
            CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

            String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

            assistant
                    .chat(userMessage)
                    .onPartialResponse(answerBuilder::append)
                    .onCompleteResponse(response -> {
                        futureAnswer.complete(answerBuilder.toString());
                        futureResponse.complete(response);
                    })
                    .onError(futureAnswer::completeExceptionally)
                    .start();

            futureAnswer.get(30, SECONDS);
            futureResponse.get(30, SECONDS);
        });

        Throwable cause = assertedThrows.getCause();
        assertTrue(cause instanceof RuntimeException);
        assertThat(cause.getMessage()).contains("no such tool exists. Most likely, it is a hallucination");
    }

    @Test // HallucinatedToolNameStrategy.LET_LLM_TRY
    void should_execute_tool_without_interrupt() throws Exception {

        ToolExecutionRequest toolExecutionRequest1 = ToolExecutionRequest.builder()
                .name("browser_use")
                .arguments("action")
                .build();
        AiMessage aiMessage1 = AiMessage.from("invoke tool..", Lists.list(toolExecutionRequest1));

        ToolExecutionRequest toolExecutionRequest2 = ToolExecutionRequest.builder()
                .name("calculator")
                .arguments("number")
                .build();
        AiMessage aiMessage2 = AiMessage.from(
                "Maybe the assistant made a mistake in the tool call. Let me check the tool parameters again.",
                Lists.list(toolExecutionRequest2));

        AiMessage aiMessage3 = AiMessage.from("6.97070153193991E8");

        StreamingChatModelMock model = new StreamingChatModelMock(Lists.list(aiMessage1, aiMessage2, aiMessage3));

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .tools(new Calculator())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                // Use the `LET_LLM_TRY` strategy let the LLM try to solve hallucination issues itself,
                // instead of throwing a RuntimeException.
                .hallucinatedToolNameStrategy(HallucinatedToolNameStrategy.LET_LLM_TRY)
                .build();

        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        assistant
                .chat(userMessage)
                .onPartialResponse(answerBuilder::append)
                .onCompleteResponse(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String answer = futureAnswer.get(30, SECONDS);
        ChatResponse response = futureResponse.get(30, SECONDS);

        assertThat(answer).contains("6.97");
        assertThat(response.aiMessage().text()).contains("6.97");
    }

    static class Calculator {

        @Tool("calculates the square root of the provided number")
        double squareRoot(@P("number to operate on") double number) {
            return Math.sqrt(number);
        }
    }
}
