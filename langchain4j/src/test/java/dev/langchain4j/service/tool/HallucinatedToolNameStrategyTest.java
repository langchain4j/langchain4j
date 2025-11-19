package dev.langchain4j.service.tool;

import static dev.langchain4j.service.tool.HallucinatedToolNameStrategy.THROW_EXCEPTION;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

public class HallucinatedToolNameStrategyTest {

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    @Test // HallucinatedToolNameStrategy.THROW_EXCEPTION
    void should_throw_exception() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("browser_use") // Mock the wrong tool name and arguments
                .arguments("{\"arg0\":\"mock_action\"}")
                .build();
        AiMessage aiMessage = AiMessage.from("does not matter", List.of(toolExecutionRequest));
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(aiMessage);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .tools(new Calculator())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                // Use the default tool hallucination strategy
                .hallucinatedToolNameStrategy(THROW_EXCEPTION)
                .build();

        // when
        TokenStream tokenStream = assistant.chat("does not matter");

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        tokenStream
                .onCompleteResponse(r -> futureError.completeExceptionally(new IllegalStateException("onCompleteResponse should not be called")))
                .onError(futureError::complete)
                .start();
        Throwable error = futureError.get(30, SECONDS);

        // then
        assertThat(error)
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageContaining("no such tool exists. Most likely, it is a hallucination");
    }

    @Test // custom HallucinatedToolNameStrategy
    void should_execute_tool_without_interrupt() throws Exception {
        ToolExecutionRequest toolExecutionRequest1 = ToolExecutionRequest.builder()
                .name("browser_use")
                .arguments("{\"arg0\":\"mock_action\"}")
                .build();
        AiMessage aiMessage1 = AiMessage.from("invoke tool..", Lists.list(toolExecutionRequest1));

        ToolExecutionRequest toolExecutionRequest2 = ToolExecutionRequest.builder()
                .name("squareRoot")
                .arguments("{\"arg0\":\"4.859067984738941E17d\"}")
                .build();
        AiMessage aiMessage2 = AiMessage.from(
                "Maybe the assistant made a mistake in the tool call. Let me check the tool parameters again.",
                Lists.list(toolExecutionRequest2));

        AiMessage aiMessage3 = AiMessage.from("6.97070153193991E8");

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(aiMessage1, aiMessage2, aiMessage3);
        Calculator calculator = spy(new Calculator());

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .tools(calculator)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                // Use the custom HallucinatedToolNameStrategy let the LLM try to solve hallucination issues itself,
                // instead of throwing a RuntimeException.
                .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                        toolExecutionRequest, toolExecutionRequest.name()
                                + "' is not a tool. please check the tool specifications again and use available tools."))
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

        verify(calculator).squareRoot(4.859067984738941E17d);
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
