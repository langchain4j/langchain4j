package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.guardrail.ToolGuardrailService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

/**
 * The deprecated {@link ToolService} methods that run a tool from its executors only: they apply the tool guardrails of
 * the tools registered on the service, and the guardrails that apply to every tool to the other tools (fail closed),
 * whose own guardrails are not known there.
 */
@SuppressWarnings("deprecation")
class ToolServiceDeprecatedExecutionTest {

    interface Assistant {}

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
    void execute_tool_applies_the_guardrails_of_a_registered_tool() {
        var toolService = toolService(null, registeredTool("guarded", REJECTING));
        var executors = toolService.toolExecutors();

        assertThat(toolService.executeTool(CONTEXT, executors, call("guarded"), null, null))
                .returns(true, ToolExecutionResult::isError)
                .returns("rejected", ToolExecutionResult::resultText);
    }

    @Test
    void start_tool_applies_the_guardrails_of_a_registered_tool() {
        var toolService = toolService(REJECTING, registeredTool("guarded"));
        var executors = Map.of("guarded", tool());

        assertThat(toolService
                        .startTool(call("guarded"), executors, CONTEXT, null, null, SAME_THREAD)
                        .join())
                .returns(true, ToolExecutionResult::isError)
                .returns("rejected", ToolExecutionResult::resultText);
    }

    @Test
    void another_executor_with_the_name_of_a_registered_tool_does_not_get_the_guardrails_of_that_tool() {
        var toolService = toolService(null, registeredTool("guarded", REJECTING));
        // the caller's own executor, which the guardrails of the registered tool were not resolved for
        var executors = Map.of("guarded", tool());

        assertThat(toolService.executeTool(CONTEXT, executors, call("guarded"), null, null))
                .returns(false, ToolExecutionResult::isError)
                .returns("raw result", ToolExecutionResult::resultText);
        assertThat(toolService
                        .startTool(call("guarded"), executors, CONTEXT, null, null, SAME_THREAD)
                        .join())
                .returns(false, ToolExecutionResult::isError)
                .returns("raw result", ToolExecutionResult::resultText);
    }

    @Test
    void another_executor_with_the_name_of_a_registered_tool_gets_the_global_guardrails() {
        var toolService = toolService(REJECTING, registeredTool("guarded"));
        var executors = Map.of("guarded", tool());

        assertThat(toolService.executeTool(CONTEXT, executors, call("guarded"), null, null))
                .returns(true, ToolExecutionResult::isError)
                .returns("rejected", ToolExecutionResult::resultText);
    }

    @Test
    void execute_tool_applies_the_global_guardrails_to_a_tool_that_is_not_registered() {
        var toolService = toolService(REJECTING);
        // a tool that is not registered, like the tool of a ToolProvider
        var executors = Map.of("provided", tool());

        assertThat(toolService.executeTool(CONTEXT, executors, call("provided"), null, null))
                .returns(true, ToolExecutionResult::isError)
                .returns("rejected", ToolExecutionResult::resultText);
    }

    @Test
    void start_tool_applies_the_global_guardrails_to_a_tool_that_is_not_registered() {
        var toolService = toolService(REJECTING);
        var executors = Map.of("provided", tool());

        assertThat(toolService
                        .startTool(call("provided"), executors, CONTEXT, null, null, SAME_THREAD)
                        .join())
                .returns(true, ToolExecutionResult::isError)
                .returns("rejected", ToolExecutionResult::resultText);
    }

    @Test
    void a_tool_without_guardrails_runs_as_is() {
        var toolService = toolService(null, registeredTool("plain"));
        var executors = Map.of("plain", tool());

        assertThat(toolService.executeTool(CONTEXT, executors, call("plain"), null, null))
                .returns(false, ToolExecutionResult::isError)
                .returns("raw result", ToolExecutionResult::resultText);
    }

    @Test
    void without_a_tool_guardrail_service_no_tool_guardrails_run() {
        var toolService = new ToolService();
        var executors = Map.of("plain", tool());

        assertThat(toolService.executeTool(CONTEXT, executors, call("plain"), null, null))
                .returns(false, ToolExecutionResult::isError)
                .returns("raw result", ToolExecutionResult::resultText);
    }
}
