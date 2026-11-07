package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.event.CompensationReason;
import dev.langchain4j.observability.api.event.ToolCompensatedEvent;
import dev.langchain4j.observability.api.listener.ToolCompensatedEventListener;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolServiceResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ToolServiceAsyncCancellationTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_cancel_after_an_already_started_tool_finishes(boolean compensate) throws Exception {
        Fixture fixture = new Fixture(compensate);
        CompletableFuture<ToolServiceResult> result = fixture.start();

        assertThat(fixture.tools.started).isEqualTo(1);
        assertThat(result).isNotDone();
        assertThat(fixture.cancellation.cancel(false)).isTrue();
        assertThat(fixture.tools.result).isNotDone();
        assertThat(result).isNotDone();

        fixture.tools.result.complete("done");

        if (compensate) {
            assertThat(fixture.tools.compensated).singleElement().satisfies(execution -> {
                assertThat(execution.request()).isEqualTo(fixture.request);
                assertThat(execution.result()).isEqualTo("done");
            });
            assertThat(result)
                    .as("cancellation waits for the asynchronous rollback")
                    .isNotDone();
            fixture.tools.rollback.complete(null);
        }

        assertThat(failure(result)).isInstanceOf(CancellationException.class);
        assertThat(fixture.tools.result.get(5, SECONDS)).isEqualTo("done");
        fixture.assertNoContinuation();
        if (compensate) {
            assertThat(fixture.compensatedEvents).singleElement().satisfies(event -> {
                assertThat(event.request()).isEqualTo(fixture.request);
                assertThat(event.reason()).isEqualTo(CompensationReason.INVOCATION_CANCELLED);
            });
        } else {
            assertThat(fixture.tools.compensated).isEmpty();
            assertThat(fixture.compensatedEvents).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_prefer_cancellation_to_a_late_tool_failure(boolean compensate) throws Exception {
        Fixture fixture = new Fixture(compensate);
        CompletableFuture<ToolServiceResult> result = fixture.start();
        fixture.cancellation.cancel(false);

        fixture.tools.result.completeExceptionally(new IllegalStateException("tool failed"));

        assertThat(failure(result)).isInstanceOf(CancellationException.class);
        fixture.assertNoContinuationOrCompensation();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_preserve_the_tools_cancellation_exception(boolean compensate) throws Exception {
        Fixture fixture = new Fixture(compensate);
        CompletableFuture<ToolServiceResult> result = fixture.start();
        fixture.cancellation.cancel(false);
        CancellationException cancellation = new CancellationException("tool cancelled");

        fixture.tools.result.completeExceptionally(cancellation);

        assertThat(failure(result)).isSameAs(cancellation);
        fixture.assertNoContinuationOrCompensation();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_preserve_a_tool_failure_without_cancellation(boolean compensate) throws Exception {
        Fixture fixture = new Fixture(compensate);
        CompletableFuture<ToolServiceResult> result = fixture.start();
        IllegalStateException failure = new IllegalStateException("tool failed");

        fixture.tools.result.completeExceptionally(failure);

        assertThat(failure(result)).isSameAs(failure);
        fixture.assertNoContinuationOrCompensation();
    }

    private static Throwable failure(CompletableFuture<?> result) {
        Throwable error = catchThrowable(() -> result.get(5, SECONDS));
        assertThat(error).isInstanceOf(ExecutionException.class);
        return error.getCause();
    }

    private static class Fixture {
        final AiServiceContext context = new AiServiceContext(Assistant.class);
        final GatedTools tools = new GatedTools();
        final CompletableFuture<Void> cancellation = new CompletableFuture<>();
        final List<ToolCompensatedEvent> compensatedEvents = new ArrayList<>();
        final UserMessage userMessage = UserMessage.from("go");
        final List<ChatMessage> messages = new ArrayList<>(List.of(userMessage));
        final ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name("action")
                .arguments("{}")
                .build();
        int modelCalls;

        Fixture(boolean compensate) {
            context.toolService.tools(List.of(tools));
            if (compensate) {
                context.toolService.compensateOnToolErrors(true);
            }
            // Start the tool deterministically; its future still controls asynchronous completion.
            context.toolService.executeToolsConcurrently(Runnable::run);
            context.eventListenerRegistrar.register((ToolCompensatedEventListener) compensatedEvents::add);
        }

        CompletableFuture<ToolServiceResult> start() {
            InvocationContext invocationContext =
                    InvocationContext.builder().chatMemoryId("memory").build();
            return context.toolService.executeInferenceAndToolsLoopAsync(
                    context,
                    "memory",
                    ChatResponse.builder().aiMessage(AiMessage.from(request)).build(),
                    ChatRequestParameters.builder().build(),
                    messages,
                    null,
                    invocationContext,
                    context.toolService.createContext(invocationContext, userMessage, messages),
                    cancellation,
                    ignored -> {
                        modelCalls++;
                        return CompletableFuture.failedFuture(new AssertionError("Unexpected model continuation"));
                    });
        }

        void assertNoContinuation() {
            assertThat(modelCalls).isZero();
            assertThat(tools.started).isEqualTo(1);
            assertThat(messages).containsExactly(userMessage);
        }

        void assertNoContinuationOrCompensation() {
            assertNoContinuation();
            assertThat(tools.compensated).isEmpty();
            assertThat(compensatedEvents).isEmpty();
        }
    }

    public static class GatedTools {
        final CompletableFuture<String> result = new CompletableFuture<>();
        final CompletableFuture<Void> rollback = new CompletableFuture<>();
        final List<ToolExecution> compensated = new ArrayList<>();
        int started;

        @Tool
        public CompletableFuture<String> action() {
            started++;
            return result;
        }

        @CompensateFor("action")
        public CompletableFuture<Void> undo(ToolExecution execution) {
            compensated.add(execution);
            return rollback;
        }
    }

    interface Assistant {
        String chat(String message);
    }
}
