package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static dev.langchain4j.service.tool.HallucinatedToolNameStrategy.THROW_EXCEPTION;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class HallucinatedToolNameStrategyTest {

    interface AssistantHallucinatedTool {
        Result<AiMessage> chat(String userMessage);
    }

    static class HelloWorld {

        @Tool("Say hello")
        String hello(String name) {
            return "Hello " + name + "!";
        }
    }

    @Test
    void should_fail_on_hallucinated_tool_execution() {

        ChatModel chatModel = new ChatModelMock(ignore -> AiMessage.from(
                ToolExecutionRequest.builder().id("id").name("unknown").build()));

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        AssistantHallucinatedTool assistant = AiServices.builder(AssistantHallucinatedTool.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .tools(new HelloWorld())
                .hallucinatedToolNameStrategy(HallucinatedToolNameStrategy.THROW_EXCEPTION)
                .build();

        assertThatThrownBy(() -> assistant.chat("hi"))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageContaining("unknown");

        validateChatMemory(chatMemory);
    }

    @Test
    void should_retry_on_hallucinated_tool_execution() {

        ChatModel chatModel = new ChatModelMock(chatRequest -> {
            List<ToolExecutionResultMessage> toolResults = chatRequest.messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .toList();
            if (toolResults.isEmpty()) {
                return AiMessage.from(
                        ToolExecutionRequest.builder().id("id").name("unknown").build());
            }
            ToolExecutionResultMessage lastToolResult = toolResults.get(toolResults.size() - 1);
            String text = lastToolResult.text();
            if (text.contains("Error")) {
                // The LLM is supposed to understand the error and retry with the correct tool name
                return AiMessage.from(ToolExecutionRequest.builder()
                        .id("id")
                        .name("hello")
                        .arguments("{\"arg0\": \"Mario\"}")
                        .build());
            }
            return AiMessage.from(text);
        });

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        AssistantHallucinatedTool assistant = AiServices.builder(AssistantHallucinatedTool.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .tools(new HelloWorld())
                .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                        toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()))
                .build();

        Result<AiMessage> result = assistant.chat("hi");
        assertThat(result.content().text()).isEqualTo("Hello Mario!");

        validateChatMemory(chatMemory);
    }

    private static void validateChatMemory(ChatMemory chatMemory) {
        List<ChatMessage> messages = chatMemory.messages();
        Class<?> expectedMessageType = dev.langchain4j.data.message.UserMessage.class;
        for (ChatMessage message : messages) {
            assertThat(message).isInstanceOf(expectedMessageType);
            expectedMessageType = nextExpectedMessageType(message);
        }
    }

    private static Class<?> nextExpectedMessageType(ChatMessage message) {
        if (message instanceof dev.langchain4j.data.message.UserMessage) {
            return AiMessage.class;
        } else if (message instanceof AiMessage aiMessage) {
            if (aiMessage.hasToolExecutionRequests()) {
                return ToolExecutionResultMessage.class;
            } else {
                return dev.langchain4j.data.message.UserMessage.class;
            }
        } else if (message instanceof ToolExecutionResultMessage) {
            return AiMessage.class;
        }
        throw new UnsupportedOperationException(
                "Unsupported message type: " + message.getClass().getName());
    }

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    @Test
    void should_fail_on_hallucinated_tool_execution_when_streaming() throws Exception {

        // given
        HallucinatedToolNameStrategy hallucinatedToolNameStrategy = THROW_EXCEPTION;

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("browser_use") // Mock the wrong tool name and arguments
                .arguments("{\"arg0\":\"mock_action\"}")
                .build();
        AiMessage aiMessage = AiMessage.from("does not matter", List.of(toolExecutionRequest));
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(aiMessage);

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .tools(new Calculator())
                .chatMemory(chatMemory)
                // Use the default tool hallucination strategy
                .hallucinatedToolNameStrategy(hallucinatedToolNameStrategy)
                .build();

        // when
        TokenStream tokenStream = assistant.chat("does not matter");

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        tokenStream
                .onCompleteResponse(r -> futureError.completeExceptionally(
                        new IllegalStateException("onCompleteResponse should not be called")))
                .onError(futureError::complete)
                .start();
        Throwable error = futureError.get(30, SECONDS);

        // then
        assertThat(error)
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageContaining("no such tool exists. Most likely, it is a hallucination");

        validateChatMemory(chatMemory);
    }

    @Test
    void should_retry_on_hallucinated_tool_execution_when_streaming() throws Exception {

        // given
        Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy =
                (toolExecutionRequest) -> ToolExecutionResultMessage.from(
                        toolExecutionRequest, toolExecutionRequest.name()
                                + "' is not a tool. Please check the tool specifications again and use available tools.");

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

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .tools(calculator)
                .chatMemory(chatMemory)
                .hallucinatedToolNameStrategy(hallucinatedToolNameStrategy)
                .build();

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        assistant
                .chat(userMessage)
                .onPartialResponse(ignored -> {
                })
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();

        ChatResponse response = futureResponse.get(30, SECONDS);

        verify(calculator).squareRoot(4.859067984738941E17d);
        assertThat(response.aiMessage().text()).contains("6.97");

        validateChatMemory(chatMemory);
    }

    static class Calculator {

        @Tool("calculates the square root of the provided number")
        double squareRoot(@P("number to operate on") double number) {
            return Math.sqrt(number);
        }
    }
}
