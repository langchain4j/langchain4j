package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailException;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailException;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

/**
 * The public contract of {@link ToolGuardrailService#executeGuardrails}: what a framework gets when it runs a tool call
 * through the tool guardrails, without knowing how they work.
 */
class ToolGuardrailServiceExecuteGuardrailsTest {

    interface Assistant {}

    private static final String ORIGINAL = "{\"q\":\"a\"}";
    private static final String REWRITTEN = "{\"q\":\"b\"}";
    private static final Object RAW = new Object();
    private static final ToolSpecification SPECIFICATION =
            ToolSpecification.builder().name("search").build();
    private static final ToolSource SOURCE = ToolSource.mcp("server");
    private static final ToolExecutionRequest CALL = ToolExecutionRequest.builder()
            .id("1")
            .name("search")
            .arguments(ORIGINAL)
            .build();
    private static final GuardrailRequestParams PARAMS = GuardrailRequestParams.builder()
            .userMessageTemplate("t")
            .variables(Map.of())
            .invocationContext(InvocationContext.builder().build())
            .build();

    // the result factories are default methods of the interfaces, which lambdas cannot call
    private static final ToolInputGuardrail INPUT = request -> null;
    private static final ToolOutputGuardrail OUTPUT = request -> null;

    /** A tool that records the requests it ran with. */
    private static final class Tool implements Function<ToolExecutionRequest, ToolExecutionResult> {

        final List<ToolExecutionRequest> requests = new ArrayList<>();
        private final ToolExecutionResult result;

        Tool(ToolExecutionResult result) {
            this.result = result;
        }

        @Override
        public ToolExecutionResult apply(ToolExecutionRequest request) {
            requests.add(request);
            return result;
        }
    }

    private static ToolExecutionResult toolResult(boolean isError) {
        return ToolExecutionResult.builder()
                .resultText("found")
                .isError(isError)
                .result(RAW)
                .attributes(Map.of("k", "v"))
                .build();
    }

    private static GuardedToolExecution execute(
            List<ToolInputGuardrail> inputs,
            List<ToolOutputGuardrail> outputs,
            Function<ToolExecutionRequest, ToolExecutionResult> tool) {
        var service = ToolGuardrailService.builder(Assistant.class).build();
        var key = service.bind(
                null,
                AiServiceTool.builder()
                        .toolSpecification(SPECIFICATION)
                        .toolExecutor((request, memoryId) -> "unused")
                        .toolInputGuardrails(inputs)
                        .toolOutputGuardrails(outputs)
                        .build());
        return service.executeGuardrails(key, CALL, SPECIFICATION, SOURCE, PARAMS, tool);
    }

    @Test
    void without_guardrails_the_tool_runs_with_the_call_and_its_result_is_returned_as_is() {
        var result = toolResult(false);
        var tool = new Tool(result);

        var execution = execute(List.of(), List.of(), tool);

        assertThat(tool.requests).containsExactly(CALL);
        assertThat(execution)
                .returns(CALL, GuardedToolExecution::request)
                .returns(result, GuardedToolExecution::result);
    }

    @Test
    void the_guardrail_requests_carry_the_specification_the_source_and_the_parameters() {
        var inputRequests = new ArrayList<ToolInputGuardrailRequest>();
        var outputRequests = new ArrayList<ToolOutputGuardrailRequest>();
        ToolInputGuardrail input = request -> {
            inputRequests.add(request);
            return ToolInputGuardrailResult.success();
        };
        ToolOutputGuardrail output = request -> {
            outputRequests.add(request);
            return ToolOutputGuardrailResult.success();
        };

        execute(List.of(input), List.of(output), new Tool(toolResult(false)));

        assertThat(inputRequests)
                .singleElement()
                .returns(CALL, ToolInputGuardrailRequest::toolExecutionRequest)
                .returns(SPECIFICATION, ToolInputGuardrailRequest::toolSpecification)
                .returns(SOURCE, ToolInputGuardrailRequest::toolSource)
                .returns(PARAMS, ToolInputGuardrailRequest::requestParams);
        assertThat(outputRequests)
                .singleElement()
                .returns(SPECIFICATION, ToolOutputGuardrailRequest::toolSpecification)
                .returns(SOURCE, ToolOutputGuardrailRequest::toolSource)
                .returns("found", ToolOutputGuardrailRequest::text)
                .returns(false, ToolOutputGuardrailRequest::isError);
    }

    @Test
    void an_input_rewrite_is_what_the_tool_and_the_output_guardrails_get_and_what_is_returned() {
        var seenByOutput = new ArrayList<String>();
        ToolInputGuardrail rewrite = request -> ToolInputGuardrailResult.successWith(REWRITTEN);
        ToolOutputGuardrail output = request -> {
            seenByOutput.add(request.arguments());
            return ToolOutputGuardrailResult.success();
        };
        var tool = new Tool(toolResult(false));

        var execution = execute(List.of(rewrite), List.of(output), tool);

        assertThat(tool.requests)
                .singleElement()
                .returns("1", ToolExecutionRequest::id)
                .returns(REWRITTEN, ToolExecutionRequest::arguments);
        assertThat(seenByOutput).containsExactly(REWRITTEN);
        assertThat(execution.request()).isEqualTo(tool.requests.get(0));
    }

    @Test
    void a_non_fatal_input_failure_does_not_run_the_tool_and_returns_the_message_verbatim() {
        ToolInputGuardrail rewrite = request -> ToolInputGuardrailResult.successWith(REWRITTEN);
        ToolInputGuardrail reject = request -> INPUT.failure("Not allowed");
        var tool = new Tool(toolResult(false));

        var execution = execute(List.of(rewrite, reject), List.of(), tool);

        assertThat(tool.requests).isEmpty();
        assertThat(execution.result())
                .returns(true, ToolExecutionResult::isError)
                .returns("Not allowed", ToolExecutionResult::resultText);
        assertThat(execution.request().arguments()).isEqualTo(REWRITTEN);
    }

    @Test
    void an_output_rewrite_keeps_the_attributes_and_drops_the_raw_result() {
        ToolOutputGuardrail rewrite = request -> ToolOutputGuardrailResult.successWith("sanitized");

        var execution = execute(List.of(), List.of(rewrite), new Tool(toolResult(false)));

        assertThat(execution.result())
                .returns("sanitized", ToolExecutionResult::resultText)
                .returns(false, ToolExecutionResult::isError)
                .returns(Map.of("k", "v"), ToolExecutionResult::attributes)
                .returns(null, ToolExecutionResult::result);
    }

    @Test
    void an_output_guardrail_that_keeps_the_result_returns_it_as_is() {
        var result = toolResult(false);
        ToolOutputGuardrail keep = request -> ToolOutputGuardrailResult.success();

        var execution = execute(List.of(), List.of(keep), new Tool(result));

        assertThat(execution.result()).isSameAs(result);
    }

    @Test
    void output_guardrails_also_run_on_an_error_result_and_can_turn_it_into_a_success() {
        ToolOutputGuardrail recover =
                request -> ToolOutputGuardrailResult.successWith(List.of(TextContent.from("recovered")), false);

        var execution = execute(List.of(), List.of(recover), new Tool(toolResult(true)));

        assertThat(execution.result())
                .returns("recovered", ToolExecutionResult::resultText)
                .returns(false, ToolExecutionResult::isError);
    }

    @Test
    void a_non_fatal_output_failure_returns_the_message_verbatim() {
        ToolOutputGuardrail reject = request -> OUTPUT.failure("Blocked");

        var execution = execute(List.of(), List.of(reject), new Tool(toolResult(false)));

        assertThat(execution.result())
                .returns(true, ToolExecutionResult::isError)
                .returns("Blocked", ToolExecutionResult::resultText)
                .returns(null, ToolExecutionResult::result);
    }

    @Test
    void a_fatal_input_failure_throws_without_running_the_tool_and_carries_the_validated_arguments() {
        ToolInputGuardrail rewrite = request -> ToolInputGuardrailResult.successWith(REWRITTEN);
        ToolInputGuardrail fatal = request -> INPUT.fatal("stop");
        var tool = new Tool(toolResult(false));

        assertThatThrownBy(() -> execute(List.of(rewrite, fatal), List.of(), tool))
                .asInstanceOf(InstanceOfAssertFactories.type(ToolInputGuardrailException.class))
                .extracting(exception -> exception.arguments().orElseThrow())
                .isEqualTo(REWRITTEN);
        assertThat(tool.requests).isEmpty();
    }

    @Test
    void a_fatal_output_failure_throws() {
        ToolOutputGuardrail fatal = request -> OUTPUT.fatal("stop");

        assertThatThrownBy(() -> execute(List.of(), List.of(fatal), new Tool(toolResult(false))))
                .isInstanceOf(ToolOutputGuardrailException.class);
    }

    @Test
    void an_exception_of_the_tool_propagates_as_is() {
        var failure = new IllegalStateException("boom");
        ToolInputGuardrail input = request -> ToolInputGuardrailResult.success();

        assertThatThrownBy(() -> execute(List.of(input), List.of(), request -> {
                    throw failure;
                }))
                .isSameAs(failure);
    }

    @Test
    void the_key_the_request_the_parameters_and_the_tool_are_required() {
        var service = ToolGuardrailService.builder(Assistant.class).build();
        var key = service.bind(
                null,
                AiServiceTool.builder()
                        .toolSpecification(SPECIFICATION)
                        .toolExecutor((request, memoryId) -> "unused")
                        .build());
        var tool = new Tool(toolResult(false));

        assertThatThrownBy(() -> service.executeGuardrails(null, CALL, SPECIFICATION, SOURCE, PARAMS, tool))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.executeGuardrails(key, null, SPECIFICATION, SOURCE, PARAMS, tool))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.executeGuardrails(key, CALL, SPECIFICATION, SOURCE, null, tool))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.executeGuardrails(key, CALL, SPECIFICATION, SOURCE, PARAMS, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void the_execution_builder_round_trips_and_requires_the_request_and_the_result() {
        var execution = GuardedToolExecution.builder()
                .request(CALL)
                .result(toolResult(false))
                .build();

        assertThat(execution.toBuilder().build()).isEqualTo(execution);
        assertThatThrownBy(() ->
                        GuardedToolExecution.builder().result(toolResult(false)).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GuardedToolExecution.builder().request(CALL).build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
