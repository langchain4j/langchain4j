package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Pins the current behavior of a {@link CompletableFuture}-returning {@code @Tool} in a streaming
 * ({@link TokenStream}) AI Service: it <b>works</b>, because the streaming tool loop executes tools via the
 * synchronous {@link dev.langchain4j.service.tool.ToolExecutor#executeWithContext} path, which joins the
 * future. This means it is <b>not yet truly non-blocking</b> on the streaming path - the join blocks the
 * thread until the tool's future completes (unlike the non-streaming {@code CompletableFuture}-returning AI
 * Service, which composes the tool future without blocking). This test guards that the feature functions;
 * when the streaming handler is made non-blocking, this test should keep passing.
 */
class StreamingAiServicesWithAsyncToolsTest {

    interface StreamingAssistant {

        TokenStream chat(String message);
    }

    static class AsyncTools {

        @Tool
        CompletableFuture<String> currentTemperature() {
            return CompletableFuture.supplyAsync(() -> "42", CompletableFuture.delayedExecutor(20, MILLISECONDS));
        }
    }

    @Test
    void future_returning_tool_works_in_streaming_ai_service() throws Exception {

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(toolExecutionRequest), AiMessage.from("It is 42 degrees"));

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .tools(new AsyncTools())
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant.chat("What is the temperature?")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse finalResponse = future.get(10, SECONDS);

        assertThat(finalResponse.aiMessage().text()).isEqualTo("It is 42 degrees");
        assertThat(model.requests()).hasSize(2);
        // the tool's future was unwrapped (via a blocking join) and its value sent to the LLM
        assertThat(model.requests().get(1).messages())
                .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                .singleElement()
                .satisfies(message -> assertThat(((ToolExecutionResultMessage) message).text())
                        .isEqualTo("42"));
    }
}
