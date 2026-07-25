package dev.langchain4j.service.tool;

import static dev.langchain4j.agent.tool.ReturnBehavior.TO_LLM;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class AsyncToolExecutionTest {

    interface Assistant {
        Result<String> chat(@MemoryId String conversationId, @UserMessage String message);
    }

    static class Coordinates {
        double latitude;
        double longitude;

        Coordinates(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    static class Tools {

        String executingThread;

        @Tool(returnBehavior = TO_LLM)
        public CompletableFuture<String> lookup(@P(name = "key") String key) {
            return CompletableFuture.supplyAsync(
                    () -> {
                        executingThread = Thread.currentThread().getName();
                        return "looked-up:" + key;
                    },
                    Executors.newSingleThreadExecutor());
        }

        @Tool(returnBehavior = TO_LLM)
        public CompletableFuture<Coordinates> geocode(@P(name = "city") String city) {
            return CompletableFuture.completedFuture(new Coordinates(48.8566, 2.3522));
        }

        @Tool(returnBehavior = TO_LLM)
        public CompletableFuture<String> fail(@P(name = "key") String key) {
            return CompletableFuture.failedFuture(new RuntimeException("boom"));
        }
    }

    static class GatedTools {

        final CompletableFuture<String> gate = new CompletableFuture<>();

        @Tool(returnBehavior = TO_LLM)
        public CompletableFuture<String> awaitGate(@P(name = "key") String key) {
            return gate;
        }
    }

    @Test
    void should_unwrap_completable_future_string_result() throws Exception {

        DefaultToolExecutor executor =
                new DefaultToolExecutor(new Tools(), Tools.class.getDeclaredMethod("lookup", String.class));

        ToolExecutionResult result = executor.executeWithContext(
                ToolExecutionRequest.builder()
                        .name("lookup")
                        .arguments("{\"key\": \"price\"}")
                        .build(),
                null);

        assertThat(result.isError()).isFalse();
        assertThat(result.resultText()).isEqualTo("looked-up:price");
    }

    @Test
    void should_unwrap_completable_future_pojo_result_as_json() throws Exception {

        DefaultToolExecutor executor =
                new DefaultToolExecutor(new Tools(), Tools.class.getDeclaredMethod("geocode", String.class));

        ToolExecutionResult result = executor.executeWithContext(
                ToolExecutionRequest.builder()
                        .name("geocode")
                        .arguments("{\"city\": \"Paris\"}")
                        .build(),
                null);

        assertThat(result.resultText()).contains("48.8566").contains("2.3522");
    }

    @Test
    void should_route_async_tool_failure_through_error_handling() throws Exception {

        DefaultToolExecutor executor =
                new DefaultToolExecutor(new Tools(), Tools.class.getDeclaredMethod("fail", String.class));

        ToolExecutionResult result = executor.executeWithContext(
                ToolExecutionRequest.builder()
                        .name("fail")
                        .arguments("{\"key\": \"x\"}")
                        .build(),
                null);

        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).isEqualTo("boom");
    }

    @Test
    void should_run_async_tool_body_on_its_own_executor() throws Exception {

        Tools tools = new Tools();
        DefaultToolExecutor executor =
                new DefaultToolExecutor(tools, Tools.class.getDeclaredMethod("lookup", String.class));

        executor.executeWithContext(
                ToolExecutionRequest.builder()
                        .name("lookup")
                        .arguments("{\"key\": \"price\"}")
                        .build(),
                null);

        assertThat(tools.executingThread)
                .as("the async tool body runs on its own executor, not the thread that unwraps the future")
                .isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    void should_block_until_the_async_tool_future_completes() throws Exception {

        GatedTools tools = new GatedTools();
        DefaultToolExecutor executor =
                new DefaultToolExecutor(tools, GatedTools.class.getDeclaredMethod("awaitGate", String.class));

        Thread completer = new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            tools.gate.complete("resolved");
        });
        completer.start();

        ToolExecutionResult result = executor.executeWithContext(
                ToolExecutionRequest.builder()
                        .name("awaitGate")
                        .arguments("{\"key\": \"x\"}")
                        .build(),
                null);

        assertThat(result.resultText())
                .as("the future is still pending when the tool is invoked, so returning its "
                        + "value proves the executor joined it synchronously (blocking) before returning")
                .isEqualTo("resolved");

        completer.join();
    }

    @Test
    void should_feed_resolved_async_tool_result_to_the_model() {

        ToolExecutionRequest lookupCall = ToolExecutionRequest.builder()
                .id("call-1")
                .name("lookup")
                .arguments("{\"key\": \"price\"}")
                .build();

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(AiMessage.from(lookupCall), AiMessage.from("done"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(20))
                .tools(new Tools())
                .build();

        Result<String> result = assistant.chat("conv-async", "what is the price");

        assertThat(result.content()).isEqualTo("done");

        List<ChatMessage> secondRequest = model.requests().get(1).messages();
        ToolExecutionResultMessage toolResult = secondRequest.stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(toolResult.text()).isEqualTo("looked-up:price");
    }
}
