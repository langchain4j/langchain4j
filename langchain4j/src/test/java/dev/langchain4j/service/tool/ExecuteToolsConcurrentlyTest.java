package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.AiServices;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Wall-clock validation that {@link AiServices#executeToolsConcurrently(java.util.concurrent.Executor)}
 * actually parallelizes per-tool execution when the LLM returns multiple tool calls in one response.
 *
 * <p>Pattern: 3 tools each sleeping 200 ms. Serial would take >= 600 ms; parallel should finish well
 * under 500 ms (with generous slack for CI noise).
 */
class ExecuteToolsConcurrentlyTest {

    interface Assistant {
        String chat(String message);
    }

    static class SleepingTool {

        final AtomicInteger calls = new AtomicInteger();

        @Tool
        public String slow(String arg) throws InterruptedException {
            calls.incrementAndGet();
            Thread.sleep(200);
            return "done-" + arg;
        }
    }

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(8);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void should_execute_multiple_tools_in_parallel_when_executor_provided() {

        SleepingTool tool = new SleepingTool();
        AiMessage threeToolCalls = AiMessage.from(toolCall("c1", "a"), toolCall("c2", "b"), toolCall("c3", "c"));
        AiMessage finalAnswer = AiMessage.from("all done");
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(threeToolCalls, finalAnswer);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .executeToolsConcurrently(executor)
                .build();

        long start = System.currentTimeMillis();
        String reply = assistant.chat("go");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(reply).isEqualTo("all done");
        assertThat(tool.calls.get()).isEqualTo(3);
        // Serial would be >= 600ms (3 * 200ms). Parallel must be substantially less.
        assertThat(elapsed)
                .as("Three 200ms tools should run concurrently; observed %dms", elapsed)
                .isLessThan(500);
    }

    @Test
    void should_execute_sequentially_when_no_executor_provided() {

        SleepingTool tool = new SleepingTool();
        AiMessage threeToolCalls = AiMessage.from(toolCall("c1", "a"), toolCall("c2", "b"), toolCall("c3", "c"));
        AiMessage finalAnswer = AiMessage.from("all done");
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(threeToolCalls, finalAnswer);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                // intentionally do NOT call executeToolsConcurrently(...)
                .build();

        long start = System.currentTimeMillis();
        String reply = assistant.chat("go");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(reply).isEqualTo("all done");
        assertThat(tool.calls.get()).isEqualTo(3);
        // Serial baseline: 3 * 200ms = 600ms minimum.
        assertThat(elapsed)
                .as("Without an executor, tools should run serially; observed %dms", elapsed)
                .isGreaterThanOrEqualTo(600);
    }

    private static ToolExecutionRequest toolCall(String id, String arg) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("slow")
                .arguments("{\"arg0\": \"" + arg + "\"}")
                .build();
    }
}
