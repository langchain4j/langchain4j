package dev.langchain4j.service.guardrail;

import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.ORIGINAL_ARGUMENTS;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.REWRITTEN_ARGUMENTS;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.input;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.inputFailure;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.inputFatal;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.run;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailException;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.Mode;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.SearchTool;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.ToolKind;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * What a {@link ToolInputGuardrail} does to a tool call (success, rewrite, non-fatal and fatal failure), in the four
 * modes of an AI service and for the four kinds of tools.
 */
class ToolInputGuardrailTests extends BaseGuardrailTests {

    // ---------- success ----------

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_tool_without_guardrails_runs_as_before(Mode mode) {
        var tool = SearchTool.echoing();

        var run = run(mode, services -> ToolKind.LIST.register(services, tool));

        assertThat(run.answerText()).isEqualTo("done");
        assertThat(tool.receivedArguments).containsExactly(ORIGINAL_ARGUMENTS);
        assertThat(run.inputGuardrailEvents).isEmpty();
        assertThat(run.outputGuardrailEvents).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_guardrail_that_succeeds_lets_the_tool_run_with_the_arguments_it_was_called_with(Mode mode) {
        var tool = SearchTool.echoing();
        var validated = new AtomicInteger();

        var run = run(mode, services -> {
            ToolKind.LIST.register(services, tool);
            services.toolInputGuardrails(input(request -> {
                validated.incrementAndGet();
                return ToolInputGuardrailResult.success();
            }));
        });

        assertThat(run.answerText()).isEqualTo("done");
        assertThat(validated).hasValue(1);
        assertThat(tool.receivedArguments).containsExactly(ORIGINAL_ARGUMENTS);
        assertThat(run.toolResultMessageOfSecondRequest("search"))
                .returns(false, ToolExecutionResultMessage::isError)
                .returns("found " + ORIGINAL_ARGUMENTS, ToolExecutionResultMessage::text);
    }

    // ---------- non-fatal failure (D3) ----------

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_non_fatal_failure_does_not_run_the_tool_and_the_llm_gets_the_message_verbatim(Mode mode) {
        var tool = SearchTool.echoing();

        var run = run(mode, services -> {
            ToolKind.LIST.register(services, tool);
            services.toolInputGuardrails(input(request -> inputFailure("Not allowed: search for something else")));
        });

        // the LLM self-corrects: it gets the failure as the tool result and answers
        assertThat(run.answerText()).isEqualTo("done");
        assertThat(tool.receivedArguments).isEmpty();
        assertThat(run.toolResultMessageOfSecondRequest("search"))
                .returns(true, ToolExecutionResultMessage::isError)
                // no framework prefix like "validation failed: "
                .returns("Not allowed: search for something else", ToolExecutionResultMessage::text);
        assertThat(run.afterToolExecutions)
                .singleElement()
                .returns(true, ToolExecution::hasFailed)
                .returns("Not allowed: search for something else", ToolExecution::result);
        assertThat(run.toolEvents).singleElement().returns(true, ToolExecutedEvent::isError);
        assertThat(run.errorHandlerCalls).hasValue(0);
    }

    @ParameterizedTest
    @EnumSource(ToolKind.class)
    void a_non_fatal_failure_works_for_every_kind_of_tool(ToolKind kind) {
        var tool = SearchTool.echoing();

        var run = run(Mode.SYNC, services -> {
            kind.register(services, tool);
            services.toolInputGuardrails(input(request -> inputFailure("denied")));
        });

        assertThat(tool.receivedArguments).isEmpty();
        assertThat(run.toolResultMessageOfSecondRequest("search"))
                .returns(true, ToolExecutionResultMessage::isError)
                .returns("denied", ToolExecutionResultMessage::text);
    }

    // ---------- rewrite (D18) ----------

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_rewrite_is_what_the_tool_runs_with_and_what_the_execution_and_the_event_report(Mode mode) {
        var tool = SearchTool.echoing();
        var beforeRequests = new CopyOnWriteArrayList<ToolExecutionRequest>();

        var run = run(mode, services -> {
            ToolKind.LIST.register(services, tool);
            services.beforeToolExecution(before -> beforeRequests.add(before.request()))
                    .toolInputGuardrails(input(request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS)));
        });

        assertThat(run.answerText()).isEqualTo("done");
        assertThat(tool.receivedArguments).containsExactly(REWRITTEN_ARGUMENTS);
        assertThat(run.toolResultMessageOfSecondRequest("search").text()).isEqualTo("found " + REWRITTEN_ARGUMENTS);
        // beforeToolExecution runs before the guardrails, so it sees the original
        assertThat(beforeRequests)
                .singleElement()
                .extracting(ToolExecutionRequest::arguments)
                .isEqualTo(ORIGINAL_ARGUMENTS);
        assertThat(run.afterToolExecutions)
                .singleElement()
                .extracting(execution -> execution.request().arguments())
                .isEqualTo(REWRITTEN_ARGUMENTS);
        assertThat(run.toolEvents)
                .singleElement()
                .extracting(event -> event.request().arguments())
                .isEqualTo(REWRITTEN_ARGUMENTS);
    }

    @ParameterizedTest
    @EnumSource(ToolKind.class)
    void a_rewrite_works_for_every_kind_of_tool(ToolKind kind) {
        var tool = SearchTool.echoing();

        var run = run(Mode.SYNC, services -> {
            kind.register(services, tool);
            services.toolInputGuardrails(input(request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS)));
        });

        assertThat(tool.receivedArguments).containsExactly(REWRITTEN_ARGUMENTS);
        assertThat(run.result.toolExecutions())
                .singleElement()
                .extracting(execution -> execution.request().arguments())
                .isEqualTo(REWRITTEN_ARGUMENTS);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void the_tool_call_in_chat_memory_carries_the_rewritten_arguments(Mode mode) {
        var run = run(mode, services -> {
            ToolKind.LIST.register(services, SearchTool.echoing());
            services.toolInputGuardrails(input(request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS)));
        });

        assertThat(run.answerText()).isEqualTo("done");
        assertThat(run.memory.messages())
                .filteredOn(AiMessage.class::isInstance)
                .map(AiMessage.class::cast)
                .filteredOn(AiMessage::hasToolExecutionRequests)
                .singleElement()
                .extracting(message -> message.toolExecutionRequests().get(0).arguments())
                .isEqualTo(REWRITTEN_ARGUMENTS);
    }

    // ---------- fatal failure (D4) ----------

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_fatal_failure_aborts_the_invocation_and_bypasses_the_error_handler(Mode mode) {
        var tool = SearchTool.echoing();

        var run = run(mode, services -> {
            ToolKind.LIST.register(services, tool);
            services.toolInputGuardrails(input(request -> inputFatal("attack detected")));
        });

        assertThat(run.failure())
                .isInstanceOfSatisfying(
                        ToolInputGuardrailException.class,
                        exception -> assertThat(exception)
                                .hasMessageContaining("attack detected")
                                .satisfies(e -> assertThat(e.arguments()).contains(ORIGINAL_ARGUMENTS)));
        assertThat(tool.receivedArguments).isEmpty();
        assertThat(run.errorHandlerCalls).hasValue(0);
        // the blocking mode throws from the call, the others fail the future or go to onError, and never throw
        assertThat(run.thrownByTheCall).isEqualTo(mode == Mode.SYNC);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_fatal_failure_after_a_rewrite_reports_the_rewritten_arguments(Mode mode) {
        var tool = SearchTool.echoing();
        ToolInputGuardrail rewrite = input(request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS));
        ToolInputGuardrail fatal = input(request -> inputFatal("attack detected"));

        var run = run(mode, services -> {
            ToolKind.LIST.register(services, tool);
            services.toolInputGuardrails(List.of(rewrite, fatal));
        });

        assertThat(run.failure())
                .isInstanceOfSatisfying(
                        ToolInputGuardrailException.class,
                        exception -> assertThat(exception.arguments()).contains(REWRITTEN_ARGUMENTS));
        assertThat(tool.receivedArguments).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ToolKind.class)
    void a_fatal_failure_works_for_every_kind_of_tool(ToolKind kind) {
        var tool = SearchTool.echoing();

        var run = run(Mode.SYNC, services -> {
            kind.register(services, tool);
            services.toolInputGuardrails(input(request -> inputFatal("attack detected")));
        });

        assertThat(run.failure()).isInstanceOf(ToolInputGuardrailException.class);
        assertThat(tool.receivedArguments).isEmpty();
    }

    // ---------- the guardrails of the tool itself ----------

    @ParameterizedTest
    @EnumSource(
            value = ToolKind.class,
            names = {"LIST", "PROVIDER"})
    void the_guardrails_of_an_ai_service_tool_apply_to_that_tool(ToolKind kind) {
        var tool = SearchTool.echoing();

        var run = run(
                Mode.SYNC,
                services -> kind.register(
                        services,
                        tool,
                        builder -> builder.toolInputGuardrails(
                                input(request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS)))));

        assertThat(tool.receivedArguments).containsExactly(REWRITTEN_ARGUMENTS);
        assertThat(run.result.content()).isEqualTo("done");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_guardrail_can_validate_the_parsed_arguments(Mode mode) {
        var tool = SearchTool.echoing();

        var run = run(mode, services -> {
            ToolKind.LIST.register(services, tool);
            services.toolInputGuardrails(
                    input(request -> "a".equals(request.argumentsAsMap().get("q"))
                            ? inputFailure("query 'a' is too short")
                            : ToolInputGuardrailResult.success()));
        });

        assertThat(tool.receivedArguments).isEmpty();
        assertThat(run.toolResultMessageOfSecondRequest("search").text()).isEqualTo("query 'a' is too short");
    }
}
