package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the streaming counterpart of
 * {@link ProgrammaticCreatedImmediateReturnToolTest}: a tool supplied via
 * {@link ToolProvider} with {@link ReturnBehavior#IMMEDIATE} must short-circuit the
 * tool-execution loop in the streaming path as well.
 */
class StreamingProgrammaticCreatedImmediateReturnToolTest {

    interface Assistant {
        TokenStream chat(String userMessage);
    }

    @Test
    void should_execute_dynamic_tool_with_immediate_return_in_streaming() throws InterruptedException {
        AtomicBoolean toolExecuted = new AtomicBoolean(false);

        ToolSpecification tool = ToolSpecification.builder()
                .name("calculate")
                .description("Performs calculation")
                .build();

        ToolExecutor executor = (toolExecutionRequest, memoryId) -> {
            toolExecuted.set(true);
            return "4";
        };

        // The mock has only ONE message queued: a tool call. If IMMEDIATE is honored,
        // the streaming handler stops after the tool is executed and never asks the
        // model again. If IMMEDIATE is ignored (the bug we are guarding against), the
        // handler calls the model a second time, the queue is empty, and the mock
        // throws — surfacing as onError.
        AiMessage toolCallMessage = AiMessage.from(ToolExecutionRequest.builder()
                .id("calc-1")
                .name("calculate")
                .arguments("{\"expression\": \"2+2\"}")
                .build());
        StreamingChatModelMock mockModel = StreamingChatModelMock.thatAlwaysStreams(toolCallMessage);

        ToolProvider provider = request -> ToolProviderResult.builder()
                .add(tool, executor, ReturnBehavior.IMMEDIATE)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(mockModel)
                .toolProvider(provider)
                .build();

        AtomicReference<ChatResponse> completeResponse = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        assistant
                .chat("What is 2+2?")
                .onPartialResponse(token -> {})
                .onToolExecuted(execution -> {})
                .onCompleteResponse(response -> {
                    completeResponse.set(response);
                    done.countDown();
                })
                .onError(t -> {
                    error.set(t);
                    done.countDown();
                })
                .start();

        assertThat(done.await(10, TimeUnit.SECONDS))
                .as(
                        "streaming should complete within 10s — if IMMEDIATE is ignored a second LLM call is attempted and the mock throws")
                .isTrue();
        assertThat(error.get()).isNull();
        assertThat(toolExecuted.get()).isTrue();
        assertThat(mockModel.requests()).hasSize(1);

        ChatResponse response = completeResponse.get();
        assertThat(response).isNotNull();
        AiMessage finalAiMessage = response.aiMessage();
        assertThat(finalAiMessage.hasToolExecutionRequests()).isTrue();
        List<ToolExecutionRequest> requests = finalAiMessage.toolExecutionRequests();
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).name()).isEqualTo("calculate");
    }
}
