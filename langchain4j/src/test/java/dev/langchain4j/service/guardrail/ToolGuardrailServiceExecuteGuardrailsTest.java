package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.ToolGuardrailStage;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CancellationException;

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
    private static final ToolInputGuardrail INPUT_PASS = request -> ToolInputGuardrailResult.success();

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

    // ---------- executeGuardrailsAsync ----------

    /** An input guardrail that also validates asynchronously, as the non-blocking AI service modes require. */
    private static ToolInputGuardrail asyncInput(
            Function<ToolInputGuardrailRequest, ToolInputGuardrailResult> validation) {
        return new ToolInputGuardrail() {
            @Override
            public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
                return validation.apply(request);
            }

            @Override
            public CompletableFuture<ToolInputGuardrailResult> validateAsync(ToolInputGuardrailRequest request) {
                return CompletableFuture.completedFuture(validate(request));
            }
        };
    }

    /** An output guardrail that also validates asynchronously, as the non-blocking AI service modes require. */
    private static ToolOutputGuardrail asyncOutput(
            Function<ToolOutputGuardrailRequest, ToolOutputGuardrailResult> validation) {
        return new ToolOutputGuardrail() {
            @Override
            public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
                return validation.apply(request);
            }

            @Override
            public CompletableFuture<ToolOutputGuardrailResult> validateAsync(ToolOutputGuardrailRequest request) {
                return CompletableFuture.completedFuture(validate(request));
            }
        };
    }

    private static CompletableFuture<GuardedToolExecution> executeAsync(
            List<ToolInputGuardrail> inputs,
            List<ToolOutputGuardrail> outputs,
            Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> tool) {
        var service = ToolGuardrailService.builder(Assistant.class).build();
        var key = service.bind(
                null,
                AiServiceTool.builder()
                        .toolSpecification(SPECIFICATION)
                        .toolExecutor((request, memoryId) -> "unused")
                        .toolInputGuardrails(inputs)
                        .toolOutputGuardrails(outputs)
                        .build());
        return service.executeGuardrailsAsync(key, CALL, SPECIFICATION, SOURCE, PARAMS, tool);
    }

    private static Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> async(Tool tool) {
        return request -> CompletableFuture.completedFuture(tool.apply(request));
    }

    /** An input guardrail whose validation completes when the test completes {@link #pending}. */
    private static class PendingInputGuardrail implements ToolInputGuardrail {

        final CompletableFuture<ToolInputGuardrailResult> pending = new CompletableFuture<>();

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            throw new UnsupportedOperationException("validated asynchronously only");
        }

        @Override
        public CompletableFuture<ToolInputGuardrailResult> validateAsync(ToolInputGuardrailRequest request) {
            return pending;
        }
    }

    @Test
    void async_without_guardrails_the_tool_runs_with_the_call_and_its_result_is_returned_as_is() {
        var result = toolResult(false);
        var tool = new Tool(result);

        assertThat(executeAsync(List.of(), List.of(), async(tool)))
                .succeedsWithin(Duration.ofSeconds(5))
                .returns(CALL, GuardedToolExecution::request)
                .returns(result, GuardedToolExecution::result);
        assertThat(tool.requests).containsExactly(CALL);
    }

    @Test
    void async_an_input_rewrite_and_an_output_rewrite_apply_as_in_the_blocking_variant() {
        ToolInputGuardrail input = asyncInput(request -> ToolInputGuardrailResult.successWith(REWRITTEN));
        ToolOutputGuardrail output = asyncOutput(request -> ToolOutputGuardrailResult.successWith("clean"));
        var tool = new Tool(toolResult(false));

        assertThat(executeAsync(List.of(input), List.of(output), async(tool)))
                .succeedsWithin(Duration.ofSeconds(5))
                .returns(REWRITTEN, execution -> execution.request().arguments())
                .returns("clean", execution -> execution.result().resultText())
                .returns(Map.of("k", "v"), execution -> execution.result().attributes())
                .returns(null, execution -> execution.result().result());
        assertThat(tool.requests).extracting(ToolExecutionRequest::arguments).containsExactly(REWRITTEN);
    }

    @Test
    void async_a_non_fatal_input_failure_does_not_run_the_tool_and_returns_the_message_verbatim() {
        ToolInputGuardrail failing = asyncInput(request -> INPUT.failure("not allowed"));
        var tool = new Tool(toolResult(false));

        assertThat(executeAsync(List.of(failing), List.of(), async(tool)))
                .succeedsWithin(Duration.ofSeconds(5))
                .extracting(GuardedToolExecution::result)
                .returns(true, ToolExecutionResult::isError)
                .returns("not allowed", ToolExecutionResult::resultText);
        assertThat(tool.requests).isEmpty();
    }

    @Test
    void async_a_non_fatal_output_failure_returns_the_message_verbatim() {
        ToolOutputGuardrail failing = asyncOutput(request -> OUTPUT.failure("leaky"));

        assertThat(executeAsync(List.of(), List.of(failing), async(new Tool(toolResult(false)))))
                .succeedsWithin(Duration.ofSeconds(5))
                .extracting(GuardedToolExecution::result)
                .returns(true, ToolExecutionResult::isError)
                .returns("leaky", ToolExecutionResult::resultText);
    }

    @Test
    void async_a_fatal_input_failure_fails_the_future_without_running_the_tool() {
        ToolInputGuardrail rewrite = asyncInput(request -> ToolInputGuardrailResult.successWith(REWRITTEN));
        ToolInputGuardrail fatal = asyncInput(request -> INPUT.fatal("stop"));
        var tool = new Tool(toolResult(false));

        var execution = executeAsync(List.of(rewrite, fatal), List.of(), async(tool));

        assertThat(execution)
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .asInstanceOf(InstanceOfAssertFactories.type(ToolInputGuardrailException.class))
                .extracting(exception -> exception.arguments().orElseThrow())
                .isEqualTo(REWRITTEN);
        assertThat(tool.requests).isEmpty();
    }

    @Test
    void async_a_fatal_output_failure_fails_the_future() {
        ToolOutputGuardrail fatal = asyncOutput(request -> OUTPUT.fatal("stop"));

        assertThat(executeAsync(List.of(), List.of(fatal), async(new Tool(toolResult(false)))))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(ToolOutputGuardrailException.class);
    }

    @Test
    void async_a_tool_that_throws_or_fails_its_future_fails_the_returned_future_with_its_exception() {
        var failure = new IllegalStateException("boom");
        ToolInputGuardrail input = asyncInput(request -> ToolInputGuardrailResult.success());

        assertThat(executeAsync(List.of(input), List.of(), request -> {
                    throw failure;
                }))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCause(failure);
        assertThat(executeAsync(List.of(input), List.of(), request -> CompletableFuture.failedFuture(failure)))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCause(failure);
    }

    @Test
    void async_a_guardrail_that_throws_while_starting_fails_the_future_instead_of_throwing() {
        var failure = new IllegalStateException("broken guardrail");
        var throwing = new PendingInputGuardrail() {
            @Override
            public CompletableFuture<ToolInputGuardrailResult> validateAsync(ToolInputGuardrailRequest request) {
                throw failure;
            }
        };
        var tool = new Tool(toolResult(false));

        assertThat(executeAsync(List.of(throwing), List.of(), async(tool)))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isSameAs(failure);
        assertThat(tool.requests).isEmpty();
    }

    @Test
    void async_guardrails_run_with_validate_async_and_do_not_block_the_calling_thread() {
        var guardrail = new PendingInputGuardrail();
        var tool = new Tool(toolResult(false));

        var execution = executeAsync(List.of(guardrail), List.of(), async(tool));

        assertThat(execution).isNotDone();
        assertThat(tool.requests).isEmpty();

        guardrail.pending.complete(ToolInputGuardrailResult.successWith(REWRITTEN));

        assertThat(execution)
                .succeedsWithin(Duration.ofSeconds(5))
                .returns(REWRITTEN, result -> result.request().arguments());
        assertThat(tool.requests).extracting(ToolExecutionRequest::arguments).containsExactly(REWRITTEN);
    }

    @Test
    void async_canceling_the_returned_future_cancels_the_guardrails_in_flight_and_never_runs_the_tool() {
        var guardrail = new PendingInputGuardrail();
        var tool = new Tool(toolResult(false));

        var execution = executeAsync(List.of(guardrail), List.of(), async(tool));
        execution.cancel(true);

        assertThat(guardrail.pending).isCancelled();
        assertThat(execution).isCancelled();
        assertThat(tool.requests).isEmpty();
    }

    @Test
    void a_key_that_the_service_did_not_return_fails_instead_of_running_no_guardrails() {
        ToolInputGuardrail rejecting = request -> INPUT.failure("rejected");
        var service = ToolGuardrailService.builder(Assistant.class)
                .inputGuardrails(rejecting)
                .build();
        ToolGuardrailService.ToolKey foreignKey = () -> "search";
        Function<ToolExecutionRequest, ToolExecutionResult> tool = request -> toolResult(false);
        Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> asyncTool =
                request -> CompletableFuture.completedFuture(toolResult(false));

        assertThatThrownBy(() -> service.executeGuardrails(foreignKey, CALL, SPECIFICATION, SOURCE, PARAMS, tool))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.hasInputGuardrails("search")).isInstanceOf(IllegalArgumentException.class);
        assertFailsWithIllegalArgument(
                service.executeGuardrailsAsync(foreignKey, CALL, SPECIFICATION, SOURCE, PARAMS, asyncTool), "key");
    }

    @Test
    void async_the_key_the_request_the_parameters_and_the_tool_are_required() {
        var service = ToolGuardrailService.builder(Assistant.class).build();
        var key = service.bind(
                null,
                AiServiceTool.builder()
                        .toolSpecification(SPECIFICATION)
                        .toolExecutor((request, memoryId) -> "unused")
                        .build());
        Function<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> tool =
                request -> CompletableFuture.completedFuture(toolResult(false));

        assertFailsWithIllegalArgument(service.executeGuardrailsAsync(null, CALL, SPECIFICATION, SOURCE, PARAMS, tool), "key");
        assertFailsWithIllegalArgument(service.executeGuardrailsAsync(key, null, SPECIFICATION, SOURCE, PARAMS, tool), "request");
        assertFailsWithIllegalArgument(
                service.executeGuardrailsAsync(key, CALL, SPECIFICATION, SOURCE, null, tool), "requestParams");
        assertFailsWithIllegalArgument(service.executeGuardrailsAsync(key, CALL, SPECIFICATION, SOURCE, PARAMS, null), "tool");
    }

    @Test
    void async_a_guardrail_that_does_not_implement_validate_async_fails_the_future() {
        ToolInputGuardrail blockingOnly = request -> ToolInputGuardrailResult.success();
        var tool = new Tool(toolResult(false));

        assertThat(executeAsync(List.of(blockingOnly), List.of(), async(tool)))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .havingRootCause()
                .isInstanceOf(AsyncNotSupportedException.class);
        assertThat(tool.requests).isEmpty();
    }

    @Test
    void async_a_cancellation_after_the_input_guardrails_ended_does_not_start_the_tool() {
        var service = new ControlledInputService();
        var key = service.bind(
                null,
                AiServiceTool.builder()
                        .toolSpecification(SPECIFICATION)
                        .toolExecutor((request, memoryId) -> "unused")
                        .toolInputGuardrails(INPUT_PASS)
                        .build());
        var tool = new Tool(toolResult(false));

        var execution = service.executeGuardrailsAsync(key, CALL, SPECIFICATION, SOURCE, PARAMS, async(tool));
        // registered after the service's own continuation, so it runs first: the input stage has ended, and the tool
        // stage has not started yet
        service.inputStage.whenComplete((ignored, error) -> execution.cancel(true));
        service.inputStage.complete(ToolInputGuardrailResult.success());

        assertThat(execution).isCancelled();
        assertThat(tool.requests).isEmpty();
    }

    @Test
    void async_a_cancellation_after_the_tool_ended_does_not_start_the_output_guardrails() {
        var outputCalls = new ArrayList<ToolOutputGuardrailRequest>();
        var output = new ToolOutputGuardrail() {
            @Override
            public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
                outputCalls.add(request);
                return ToolOutputGuardrailResult.success();
            }

            @Override
            public CompletableFuture<ToolOutputGuardrailResult> validateAsync(ToolOutputGuardrailRequest request) {
                return CompletableFuture.completedFuture(validate(request));
            }
        };
        var toolFuture = new CompletableFuture<ToolExecutionResult>();

        var execution = executeAsync(List.of(), List.of(output), request -> toolFuture);
        // registered after the service's own continuation, so it runs first: the tool has ended, and the output
        // stage has not started yet
        toolFuture.whenComplete((ignored, error) -> execution.cancel(true));
        toolFuture.complete(toolResult(false));

        assertThat(execution).isCancelled();
        assertThat(outputCalls).isEmpty();
    }

    /**
     * A tool guardrail service whose input stage is a future the test completes, delegating everything else to the
     * default service.
     */
    private static final class ControlledInputService implements ToolGuardrailService {

        final CompletableFuture<ToolInputGuardrailResult> inputStage = new CompletableFuture<>();
        private final ToolGuardrailService delegate =
                ToolGuardrailService.builder(Assistant.class).build();

        @Override
        public ToolKey bind(Class<?> toolClass, AiServiceTool tool) {
            return delegate.bind(toolClass, tool);
        }

        @Override
        public Class<?> targetClass() {
            return delegate.targetClass();
        }

        @Override
        public <K> ToolInputGuardrailResult executeInputGuardrails(K key, ToolInputGuardrailRequest request) {
            return delegate.executeInputGuardrails(key, request);
        }

        @Override
        public <K> CompletableFuture<ToolInputGuardrailResult> executeInputGuardrailsAsync(
                K key, ToolInputGuardrailRequest request) {
            return inputStage;
        }

        @Override
        public <K> ToolOutputGuardrailResult executeOutputGuardrails(K key, ToolOutputGuardrailRequest request) {
            return delegate.executeOutputGuardrails(key, request);
        }

        @Override
        public <K> CompletableFuture<ToolOutputGuardrailResult> executeOutputGuardrailsAsync(
                K key, ToolOutputGuardrailRequest request) {
            return delegate.executeOutputGuardrailsAsync(key, request);
        }

        @Override
        public <K> boolean hasInputGuardrails(K key) {
            return delegate.hasInputGuardrails(key);
        }

        @Override
        public <K> boolean hasOutputGuardrails(K key) {
            return delegate.hasOutputGuardrails(key);
        }
    }

    @Test
    void async_without_guardrails_canceling_the_returned_future_cancels_the_tool() {
        var toolFuture = new CompletableFuture<ToolExecutionResult>();

        var execution = executeAsync(List.of(), List.of(), request -> toolFuture);
        execution.cancel(true);

        assertThat(toolFuture).isCancelled();
    }

    // ---------- rejectedRequest ----------

    @Test
    void the_rejected_request_of_a_fatal_input_failure_carries_the_arguments_the_failing_guardrail_validated() {
        ToolInputGuardrail rewrite = request -> ToolInputGuardrailResult.successWith(REWRITTEN);
        ToolInputGuardrail fatal = request -> INPUT.fatal("stop");
        var service = ToolGuardrailService.builder(Assistant.class).build();
        var failure = catchThrowable(() -> execute(List.of(rewrite, fatal), List.of(), new Tool(toolResult(false))));

        assertThat(service.rejectedRequest(CALL, failure))
                .hasValueSatisfying(request -> assertThat(request)
                        .returns(REWRITTEN, ToolExecutionRequest::arguments)
                        .returns(CALL.id(), ToolExecutionRequest::id)
                        .returns(CALL.name(), ToolExecutionRequest::name));
        assertThat(service.rejectedRequest(CALL, new CompletionException(failure)))
                .map(ToolExecutionRequest::arguments)
                .hasValue(REWRITTEN);
    }

    @Test
    void no_rejected_request_is_told_for_any_other_failure() {
        ToolOutputGuardrail fatal = request -> OUTPUT.fatal("stop");
        var service = ToolGuardrailService.builder(Assistant.class).build();
        var outputFailure = catchThrowable(() -> execute(List.of(), List.of(fatal), new Tool(toolResult(false))));

        assertThat(service.rejectedRequest(CALL, outputFailure)).isEmpty();
        assertThat(service.rejectedRequest(CALL, new IllegalStateException("boom")))
                .isEmpty();
        assertThat(service.rejectedRequest(CALL, null)).isEmpty();
        assertThatThrownBy(() -> service.rejectedRequest(null, outputFailure))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- failedStage and rejectedStage ----------

    @Test
    void a_call_without_a_guardrail_failure_tells_no_stage() {
        ToolOutputGuardrail sanitize = request -> OUTPUT.successWith("sanitized");

        assertThat(execute(List.of(), List.of(), new Tool(toolResult(false))).failedStage())
                .isEmpty();
        assertThat(execute(List.of(INPUT_PASS), List.of(sanitize), new Tool(toolResult(false)))
                        .failedStage())
                .isEmpty();
    }

    @Test
    void an_input_rejection_tells_the_input_stage_and_the_tool_does_not_run() {
        ToolInputGuardrail reject = request -> INPUT.failure("denied");
        var tool = new Tool(toolResult(false));

        var execution = execute(List.of(reject), List.of(), tool);

        assertThat(execution.failedStage()).hasValue(ToolGuardrailStage.INPUT);
        assertThat(tool.requests).isEmpty();
    }

    @Test
    void an_output_failure_or_a_rewrite_to_an_error_of_a_success_tells_the_output_stage() {
        ToolOutputGuardrail fail = request -> OUTPUT.failure("blocked");
        ToolOutputGuardrail toError = request -> OUTPUT.successWith(List.of(TextContent.from("flagged")), true);

        assertThat(execute(List.of(), List.of(fail), new Tool(toolResult(false)))
                        .failedStage())
                .hasValue(ToolGuardrailStage.OUTPUT);
        assertThat(execute(List.of(), List.of(toError), new Tool(toolResult(false)))
                        .failedStage())
                .hasValue(ToolGuardrailStage.OUTPUT);
    }

    @Test
    void a_tool_that_failed_on_its_own_tells_no_stage_whatever_the_output_guardrails_do_with_its_error() {
        ToolOutputGuardrail keepError = request -> OUTPUT.successWith("[removed]");
        ToolOutputGuardrail fail = request -> OUTPUT.failure("blocked");

        assertThat(execute(List.of(), List.of(keepError), new Tool(toolResult(true)))
                        .failedStage())
                .isEmpty();
        assertThat(execute(List.of(), List.of(fail), new Tool(toolResult(true))).failedStage())
                .isEmpty();
    }

    @Test
    void an_output_chain_that_flips_back_to_a_success_tells_no_stage() {
        ToolOutputGuardrail toError = request -> OUTPUT.successWith(List.of(TextContent.from("flagged")), true);
        ToolOutputGuardrail toSuccess = request -> OUTPUT.successWith(List.of(TextContent.from("clean")), false);

        assertThat(execute(List.of(), List.of(toError, toSuccess), new Tool(toolResult(false)))
                        .failedStage())
                .isEmpty();
    }

    @Test
    void the_async_execution_tells_the_same_stages() {
        ToolInputGuardrail reject = asyncInput(request -> INPUT.failure("denied"));
        ToolOutputGuardrail fail = asyncOutput(request -> OUTPUT.failure("blocked"));
        ToolOutputGuardrail keepError = asyncOutput(request -> OUTPUT.successWith("[removed]"));

        assertThat(executeAsync(List.of(reject), List.of(), async(new Tool(toolResult(false)))))
                .succeedsWithin(Duration.ofSeconds(5))
                .returns(Optional.of(ToolGuardrailStage.INPUT), GuardedToolExecution::failedStage);
        assertThat(executeAsync(List.of(), List.of(fail), async(new Tool(toolResult(false)))))
                .succeedsWithin(Duration.ofSeconds(5))
                .returns(Optional.of(ToolGuardrailStage.OUTPUT), GuardedToolExecution::failedStage);
        assertThat(executeAsync(List.of(), List.of(keepError), async(new Tool(toolResult(true)))))
                .succeedsWithin(Duration.ofSeconds(5))
                .returns(Optional.empty(), GuardedToolExecution::failedStage);
        assertThat(executeAsync(List.of(), List.of(), async(new Tool(toolResult(false)))))
                .succeedsWithin(Duration.ofSeconds(5))
                .returns(Optional.empty(), GuardedToolExecution::failedStage);
    }

    @Test
    void a_fatal_failure_tells_its_stage_also_when_wrapped() {
        ToolInputGuardrail fatalInput = request -> INPUT.fatal("stop");
        ToolOutputGuardrail fatalOutput = request -> OUTPUT.fatal("stop");
        var service = ToolGuardrailService.builder(Assistant.class).build();
        var inputFailure = catchThrowable(() -> execute(List.of(fatalInput), List.of(), new Tool(toolResult(false))));
        var outputFailure = catchThrowable(() -> execute(List.of(), List.of(fatalOutput), new Tool(toolResult(false))));

        assertThat(service.rejectedStage(CALL, inputFailure)).hasValue(ToolGuardrailStage.INPUT);
        assertThat(service.rejectedStage(CALL, new CompletionException(inputFailure)))
                .hasValue(ToolGuardrailStage.INPUT);
        assertThat(service.rejectedStage(CALL, outputFailure)).hasValue(ToolGuardrailStage.OUTPUT);
        assertThat(service.rejectedStage(CALL, new CompletionException(outputFailure)))
                .hasValue(ToolGuardrailStage.OUTPUT);
    }

    @Test
    void no_rejected_stage_is_told_for_any_other_failure() {
        var service = ToolGuardrailService.builder(Assistant.class).build();

        assertThat(service.rejectedStage(CALL, new IllegalStateException("boom")))
                .isEmpty();
        assertThat(service.rejectedStage(CALL, new CancellationException()))
                .isEmpty();
        assertThat(service.rejectedStage(CALL, null)).isEmpty();
        assertThatThrownBy(() -> service.rejectedStage(null, new IllegalStateException("boom")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertFailsWithIllegalArgument(CompletableFuture<?> future, String parameter) {
        assertThat(future)
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(IllegalArgumentException.class)
                .withMessageContaining(parameter);
    }
}
