package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StreamingToolRoundTripLimitTest {

    interface Assistant {
        TokenStream chat(String message);
    }

    static class Tools {
        final AtomicInteger calls = new AtomicInteger();

        @Tool
        String record() {
            calls.incrementAndGet();
            return "recorded";
        }
    }

    private static AiMessage calls(int round, int count) {
        List<ToolExecutionRequest> requests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            requests.add(ToolExecutionRequest.builder()
                    .id("round-" + round + "-call-" + i)
                    .name("record")
                    .arguments("{}")
                    .build());
        }
        return AiMessage.from(requests);
    }

    @ParameterizedTest
    @CsvSource({"false,1,1", "true,1,1", "false,2,2", "true,2,2"})
    void must_not_execute_tools_from_round_beyond_limit(boolean concurrent, int limit, int callsPerRound)
            throws Exception {
        Tools tools = new Tools();
        List<AiMessage> responses = new ArrayList<>();
        for (int round = 0; round <= limit; round++) {
            responses.add(calls(round, callsPerRound));
        }
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(responses);
        var builder = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .tools(tools)
                .maxToolCallingRoundTrips(limit);
        if (concurrent) {
            // A valid Executor: execute immediately to remove scheduler races from the regression.
            builder.executeToolsConcurrently(Runnable::run);
        }
        Assistant assistant = builder.build();
        CompletableFuture<Throwable> terminal = new CompletableFuture<>();
        AtomicInteger resultCallbacks = new AtomicInteger();
        assistant
                .chat("Record each requested item")
                .onToolExecuted(ignored -> resultCallbacks.incrementAndGet())
                .onCompleteResponse(
                        ignored -> terminal.completeExceptionally(new AssertionError("Expected limit error")))
                .onError(terminal::complete)
                .start();

        Throwable error = terminal.get(5, TimeUnit.SECONDS);
        assertThat(error).hasMessageContaining("exceeded " + limit + " tool calling round trips");
        assertThat(model.requests()).hasSize(limit + 1);
        assertThat(resultCallbacks.get()).isEqualTo(limit * callsPerRound);
        assertThat(tools.calls.get())
                .as("only allowed rounds may execute tools")
                .isEqualTo(limit * callsPerRound);
    }

    @ParameterizedTest
    @CsvSource({"false,1", "true,1", "false,2", "true,2"})
    void allows_final_text_after_exactly_the_configured_number_of_tool_rounds(boolean concurrent, int limit)
            throws Exception {
        Tools tools = new Tools();
        List<AiMessage> responses = new ArrayList<>();
        for (int round = 0; round < limit; round++) {
            responses.add(calls(round, 1));
        }
        responses.add(AiMessage.from("done"));
        var builder = AiServices.builder(Assistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(responses))
                .tools(tools)
                .maxToolCallingRoundTrips(limit);
        if (concurrent) {
            builder.executeToolsConcurrently(Runnable::run);
        }
        CompletableFuture<String> terminal = new CompletableFuture<>();
        builder.build()
                .chat("Record each requested item")
                .onCompleteResponse(
                        response -> terminal.complete(response.aiMessage().text()))
                .onError(terminal::completeExceptionally)
                .start();
        assertThat(terminal.get(5, TimeUnit.SECONDS)).isEqualTo("done");
        assertThat(tools.calls.get()).isEqualTo(limit);
    }

    @Test
    void must_not_execute_rejected_round_after_error_callback_on_worker_thread() throws Exception {
        Tools tools = new Tools();
        var worker = Executors.newSingleThreadExecutor();
        CountDownLatch errorDelivered = new CountDownLatch(1);
        AtomicInteger submissions = new AtomicInteger();
        CompletableFuture<Throwable> terminal = new CompletableFuture<>();
        try {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(calls(0, 1), calls(1, 1)))
                    .tools(tools)
                    .maxToolCallingRoundTrips(1)
                    .executeToolsConcurrently(task -> {
                        int submission = submissions.incrementAndGet();
                        worker.execute(() -> {
                            if (submission == 2) {
                                try {
                                    if (!errorDelivered.await(5, TimeUnit.SECONDS)) {
                                        throw new AssertionError("No limit error received");
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new AssertionError(e);
                                }
                            }
                            task.run();
                        });
                    })
                    .build();
            assistant
                    .chat("Record each requested item")
                    .onCompleteResponse(ignored -> terminal.completeExceptionally(new AssertionError("Expected error")))
                    .onError(error -> {
                        terminal.complete(error);
                        errorDelivered.countDown();
                    })
                    .start();
            assertThat(terminal.get(5, TimeUnit.SECONDS)).hasMessageContaining("exceeded 1 tool calling round trips");
            worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertThat(tools.calls.get())
                    .as("rejected tools must not run after the error callback")
                    .isEqualTo(1);
        } finally {
            worker.shutdownNow();
        }
    }
}
