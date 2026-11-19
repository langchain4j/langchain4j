package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.guardrail.ToolGuardrailService;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * The deprecated {@link ToolService} methods that run a tool from its executors only: they apply the tool guardrails of
 * the tools registered on the service, and skip the ones of the other tools, saying so once.
 * <p>
 * These tests capture {@link System#err} (the default tinylog destination for WARN), so this class runs
 * {@link Isolated} and single-threaded.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@SuppressWarnings("deprecation")
class ToolServiceDeprecatedExecutionTest {

    interface Assistant {}

    private static final String WARNING = "without its tool guardrails";
    private static final InvocationContext CONTEXT = InvocationContext.builder().build();
    private static final Executor SAME_THREAD = Runnable::run;

    private static final ToolOutputGuardrail REJECTING = new ToolOutputGuardrail() {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            return failure("rejected");
        }

        @Override
        public CompletableFuture<ToolOutputGuardrailResult> validateAsync(ToolOutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    };

    private static ToolExecutor tool() {
        return new ToolExecutor() {
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return "raw result";
            }

            @Override
            public CompletableFuture<ToolExecutionResult> executeAsync(
                    ToolExecutionRequest request, InvocationContext context) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.builder().resultText("raw result").build());
            }
        };
    }

    private static ToolExecutionRequest call(String toolName) {
        return ToolExecutionRequest.builder()
                .id("1")
                .name(toolName)
                .arguments("{}")
                .build();
    }

    private static ToolService toolService(ToolOutputGuardrail globalGuardrail, AiServiceTool... tools) {
        var toolService = new ToolService();
        toolService.tools(List.<AiServiceTool>of(tools));
        var guardrails = ToolGuardrailService.builder(Assistant.class);
        if (globalGuardrail != null) {
            guardrails.outputGuardrails(globalGuardrail);
        }
        toolService.toolGuardrailService(guardrails.build());
        return toolService;
    }

    private static AiServiceTool registeredTool(String name, ToolOutputGuardrail... guardrails) {
        return AiServiceTool.builder()
                .toolSpecification(ToolSpecification.builder().name(name).build())
                .toolExecutor(tool())
                .toolOutputGuardrails(guardrails)
                .build();
    }

    @Test
    void execute_tool_applies_the_guardrails_of_a_registered_tool_without_warning() {
        var toolService = toolService(null, registeredTool("guarded", REJECTING));
        var executors = Map.of("guarded", tool());

        var log = captureStdErr(() -> assertThat(
                        toolService.executeTool(CONTEXT, executors, call("guarded"), null, null))
                .returns(true, ToolExecutionResult::isError)
                .returns("rejected", ToolExecutionResult::resultText));

        assertThat(log).doesNotContain(WARNING);
    }

    @Test
    void start_tool_applies_the_guardrails_of_a_registered_tool_without_warning() {
        var toolService = toolService(REJECTING, registeredTool("guarded"));
        var executors = Map.of("guarded", tool());

        var log = captureStdErr(() -> assertThat(toolService
                        .startTool(call("guarded"), executors, CONTEXT, null, null, SAME_THREAD)
                        .join())
                .returns(true, ToolExecutionResult::isError)
                .returns("rejected", ToolExecutionResult::resultText));

        assertThat(log).doesNotContain(WARNING);
    }

    @Test
    void execute_tool_skips_the_guardrails_of_a_tool_that_is_not_registered_and_warns_once() {
        var toolService = toolService(REJECTING);
        // a tool that is not registered, like the tool of a ToolProvider
        var executors = Map.of("provided", tool());

        var log = captureStdErr(() -> List.of(
                toolService.executeTool(CONTEXT, executors, call("provided"), null, null),
                toolService.executeTool(CONTEXT, executors, call("provided"), null, null)));

        assertThat(toolService.executeTool(CONTEXT, executors, call("provided"), null, null))
                .returns(false, ToolExecutionResult::isError)
                .returns("raw result", ToolExecutionResult::resultText);
        assertThat(occurrences(log, WARNING)).isEqualTo(1);
        assertThat(log).contains("WARN").contains("executeTool").contains("'provided'");
    }

    @Test
    void start_tool_skips_the_guardrails_of_a_tool_that_is_not_registered_and_warns() {
        var toolService = toolService(REJECTING);
        var executors = Map.of("provided", tool());

        var log = captureStdErr(() -> assertThat(toolService
                        .startTool(call("provided"), executors, CONTEXT, null, null, SAME_THREAD)
                        .join())
                .returns("raw result", ToolExecutionResult::resultText));

        assertThat(log).contains("WARN").contains("startTool").contains("'provided'");
    }

    @Test
    void no_warning_for_a_tool_without_guardrails() {
        var toolService = toolService(null, registeredTool("plain"));
        var executors = Map.of("plain", tool());

        var log = captureStdErr(() -> toolService.executeTool(CONTEXT, executors, call("plain"), null, null));

        assertThat(log).doesNotContain(WARNING);
    }

    @Test
    void no_warning_without_a_tool_guardrail_service() {
        var toolService = new ToolService();
        var executors = Map.of("plain", tool());

        var log = captureStdErr(() -> toolService.executeTool(CONTEXT, executors, call("plain"), null, null));

        assertThat(log).doesNotContain(WARNING);
    }

    private static int occurrences(String text, String part) {
        return text.split(part, -1).length - 1;
    }

    private static String captureStdErr(Supplier<?> action) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            action.get();
        } finally {
            System.setErr(originalErr);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }
}
