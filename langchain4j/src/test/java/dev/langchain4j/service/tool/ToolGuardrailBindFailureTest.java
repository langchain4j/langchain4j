package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.merge;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.guardrail.ToolGuardrailService;
import dev.langchain4j.test.mock.MockChatModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * A tool guardrail service whose {@code bind(..)} returns no key, or fails, for a tool: the tool never runs without its
 * guardrails, the failure is reported where the tool is bound, and the round still reports and rolls back the other
 * tools without binding the failing tool again.
 */
class ToolGuardrailBindFailureTest {

    interface Assistant {}

    private static final InvocationContext INVOCATION = InvocationContext.builder().build();

    /**
     * A tool guardrail service that binds one tool with {@code keyOfTheTool}, which returns {@code null} or throws,
     * and delegates everything else to the default service.
     */
    private static final class FailingBindService implements ToolGuardrailService {

        private final ToolGuardrailService delegate =
                ToolGuardrailService.builder(Assistant.class).build();
        private final String toolName;
        private final Supplier<ToolKey> keyOfTheTool;

        private FailingBindService(String toolName, Supplier<ToolKey> keyOfTheTool) {
            this.toolName = toolName;
            this.keyOfTheTool = keyOfTheTool;
        }

        @Override
        public ToolKey bind(Class<?> toolClass, AiServiceTool tool) {
            return tool.name().equals(toolName) ? keyOfTheTool.get() : delegate.bind(toolClass, tool);
        }

        @Override
        public Class<?> aiServiceClass() {
            return delegate.aiServiceClass();
        }

        @Override
        public ToolInputGuardrailsConfig toolInputGuardrailsConfig() {
            return delegate.toolInputGuardrailsConfig();
        }

        @Override
        public ToolInputGuardrailResult executeInputGuardrails(ToolKey key, ToolInputGuardrailRequest request) {
            return delegate.executeInputGuardrails(key, request);
        }

        @Override
        public CompletableFuture<ToolInputGuardrailResult> executeInputGuardrailsAsync(
                ToolKey key, ToolInputGuardrailRequest request) {
            return delegate.executeInputGuardrailsAsync(key, request);
        }

        @Override
        public ToolOutputGuardrailResult executeOutputGuardrails(ToolKey key, ToolOutputGuardrailRequest request) {
            return delegate.executeOutputGuardrails(key, request);
        }

        @Override
        public CompletableFuture<ToolOutputGuardrailResult> executeOutputGuardrailsAsync(
                ToolKey key, ToolOutputGuardrailRequest request) {
            return delegate.executeOutputGuardrailsAsync(key, request);
        }

        @Override
        public boolean hasInputGuardrails(ToolKey key) {
            return delegate.hasInputGuardrails(key);
        }

        @Override
        public boolean hasOutputGuardrails(ToolKey key) {
            return delegate.hasOutputGuardrails(key);
        }
    }

    /** A tool that counts its runs. */
    private static final class CountingTool implements ToolExecutor {

        final AtomicInteger runs = new AtomicInteger();

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            runs.incrementAndGet();
            return "found";
        }

        @Override
        public CompletableFuture<ToolExecutionResult> executeAsync(
                ToolExecutionRequest request, InvocationContext context) {
            return CompletableFuture.completedFuture(ToolExecutionResult.builder()
                    .resultText(execute(request, null))
                    .build());
        }
    }

    private static ToolExecutionRequest call(String id, String toolName) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(toolName)
                .arguments("{}")
                .build();
    }

    private static ToolServiceContext contextWithoutKeys(ToolGuardrailService service, ToolExecutor tool) {
        return ToolServiceContext.builder()
                .effectiveTools(List.of(ToolSpecification.builder().name("lookup").build()))
                .toolExecutors(Map.of("lookup", tool))
                .toolGuardrailService(service)
                .build();
    }

    // ---------- no key ----------

    @Test
    void a_registered_tool_that_the_service_returns_no_key_for_fails_when_it_is_bound() {
        var toolService = new ToolService();
        toolService.tools(List.<AiServiceTool>of(AiServiceTool.builder()
                .toolSpecification(ToolSpecification.builder().name("lookup").build())
                .toolExecutor(new CountingTool())
                .build()));
        var service = new FailingBindService("lookup", () -> null);

        assertThatThrownBy(() -> toolService.toolGuardrailService(service))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lookup")
                .hasMessageContaining(FailingBindService.class.getName());
    }

    @Test
    void a_tool_bound_on_demand_that_the_service_returns_no_key_for_fails_and_does_not_run() {
        var tool = new CountingTool();
        var round = new ToolService()
                .startRound(contextWithoutKeys(new FailingBindService("lookup", () -> null), tool), INVOCATION, null);

        assertThatThrownBy(() -> round.executeTool(call("1", "lookup"), null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lookup")
                .hasMessageContaining(FailingBindService.class.getName());
        assertThat(tool.runs).hasValue(0);
    }

    @Test
    void a_tool_bound_on_demand_that_the_service_returns_no_key_for_fails_its_future_and_does_not_run() {
        var tool = new CountingTool();
        var round = new ToolService()
                .startRound(contextWithoutKeys(new FailingBindService("lookup", () -> null), tool), INVOCATION, null);

        assertThat(round.startTool(call("1", "lookup"), null, null, Runnable::run))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessageContaining("lookup")
                .withMessageContaining(FailingBindService.class.getName());
        assertThat(tool.runs).hasValue(0);
    }

    // ---------- bind fails on demand, next to a compensable tool ----------

    public static class Bookings {

        final List<String> log = new CopyOnWriteArrayList<>();

        @Tool
        public String book() {
            log.add("book");
            return "booked";
        }

        @CompensateFor("book")
        public void cancel(ToolExecution execution) {
            log.add("cancel");
        }
    }

    /**
     * A loop over a first response that calls {@code book}, a compensable tool with a key, then {@code unbound}, a
     * tool the context has no key for (as a framework builds it), whose binding fails on demand each time with a new
     * exception.
     */
    private static final class Loop {

        final Bookings bookings = new Bookings();
        final AtomicInteger bindAttempts = new AtomicInteger();
        final List<ToolExecution> afterToolExecutions = new CopyOnWriteArrayList<>();
        final AiServiceContext context = AiServiceContext.create(Assistant.class);
        final List<ChatMessage> messages = new ArrayList<>(List.of(UserMessage.from("hello")));
        final ChatResponse firstResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from(call("1", "book"), call("2", "unbound")))
                .build();

        Loop() {
            context.chatModel = MockChatModel.thatResponds("done");
            context.toolService.compensateOnToolErrors(true);
            context.toolService.afterToolExecution(afterToolExecutions::add);
            context.toolService.tools(List.<Object>of(bookings));
            context.toolService.toolGuardrailService(new FailingBindService("unbound", () -> {
                throw new IllegalStateException("cannot bind, attempt " + bindAttempts.incrementAndGet());
            }));
        }

        ToolServiceContext toolServiceContext() {
            var created = context.toolService.createContext(INVOCATION, null, messages);
            return created.toBuilder()
                    .effectiveTools(merge(
                            created.effectiveTools(),
                            List.of(ToolSpecification.builder().name("unbound").build())))
                    .toolExecutors(merge(created.toolExecutors(), Map.of("unbound", new CountingTool())))
                    .build();
        }
    }

    @Test
    void a_failed_binding_fails_the_invocation_with_its_error_after_rolling_back_the_other_tools() {
        var loop = new Loop();

        assertThatThrownBy(() -> loop.context.toolService.executeInferenceAndToolsLoop(
                        loop.context,
                        null,
                        loop.firstResponse,
                        ChatRequestParameters.builder().build(),
                        loop.messages,
                        null,
                        INVOCATION,
                        loop.toolServiceContext(),
                        false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cannot bind, attempt 1");
        assertThat(loop.bookings.log).containsExactly("book", "cancel");
    }

    @Test
    void a_failed_binding_fails_the_invocation_with_its_error_after_rolling_back_the_other_tools_without_blocking() {
        var loop = new Loop();

        var result = loop.context.toolService.executeInferenceAndToolsLoopAsync(
                loop.context,
                null,
                loop.firstResponse,
                ChatRequestParameters.builder().build(),
                loop.messages,
                null,
                INVOCATION,
                loop.toolServiceContext(),
                null);

        assertThat(result)
                .failsWithin(Duration.ofSeconds(10))
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessage("cannot bind, attempt 1");
        assertThat(loop.bookings.log).containsExactly("book", "cancel");
        assertThat(loop.afterToolExecutions)
                .extracting(execution -> execution.request().name())
                .containsExactlyInAnyOrder("book", "unbound");
    }
}
