package dev.langchain4j.service.guardrail;

import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.REWRITTEN_ARGUMENTS;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.input;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.inputFailure;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.output;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.outputFailure;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.registerTool;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.run;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.Mode;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.SearchTool;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.Test;

/**
 * Tool guardrails and tools that return immediately ({@link ReturnBehavior#IMMEDIATE}): the guarded result is what the
 * AI service returns, and a rejected tool does not return immediately. Run with the blocking AI service, whose
 * {@code Result} return type is what allows an immediate return.
 */
class ToolGuardrailReturnBehaviorTests extends BaseGuardrailTests {

    @Test
    void a_rewritten_immediate_tool_returns_immediately_with_the_rewritten_contents() {
        var tool = SearchTool.returning("secret");

        var run = run(
                Mode.SYNC,
                services -> registerTool(
                        services,
                        tool,
                        builder -> builder.returnBehavior(ReturnBehavior.IMMEDIATE)
                                .toolOutputGuardrails(
                                        output(request -> ToolOutputGuardrailResult.successWith("[removed]")))));

        // the LLM is not asked again
        assertThat(run.modelRequests()).hasSize(1);
        assertThat(run.result.toolExecutions())
                .singleElement()
                .returns("[removed]", ToolExecution::result)
                // the raw result belongs to the original, so it is dropped when the contents are rewritten
                .returns(null, ToolExecution::resultObject);
    }

    @Test
    void an_immediate_tool_whose_input_is_rewritten_returns_what_the_tool_made_of_the_rewritten_arguments() {
        var tool = SearchTool.echoing();

        var run = run(
                Mode.SYNC,
                services -> registerTool(
                        services,
                        tool,
                        builder -> builder.returnBehavior(ReturnBehavior.IMMEDIATE)
                                .toolInputGuardrails(
                                        input(request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS)))));

        assertThat(run.modelRequests()).hasSize(1);
        assertThat(tool.receivedArguments).containsExactly(REWRITTEN_ARGUMENTS);
        assertThat(run.result.toolExecutions())
                .singleElement()
                .returns("found " + REWRITTEN_ARGUMENTS, ToolExecution::result);
    }

    @Test
    void an_immediate_tool_rejected_by_an_input_guardrail_does_not_return_immediately() {
        var tool = SearchTool.echoing();

        var run = run(
                Mode.SYNC,
                services -> registerTool(
                        services,
                        tool,
                        builder -> builder.returnBehavior(ReturnBehavior.IMMEDIATE)
                                .toolInputGuardrails(input(request -> inputFailure("denied")))));

        // the failure goes back to the LLM, which answers
        assertThat(tool.receivedArguments).isEmpty();
        assertThat(run.modelRequests()).hasSize(2);
        assertThat(run.result.content()).isEqualTo("done");
        assertThat(run.toolResultMessageOfSecondRequest("search").text()).isEqualTo("denied");
    }

    @Test
    void an_immediate_tool_rejected_by_an_output_guardrail_does_not_return_immediately() {
        var run = run(
                Mode.SYNC,
                services -> registerTool(
                        services,
                        SearchTool.returning("injected"),
                        builder -> builder.returnBehavior(ReturnBehavior.IMMEDIATE)
                                .toolOutputGuardrails(output(request -> outputFailure("blocked")))));

        assertThat(run.modelRequests()).hasSize(2);
        assertThat(run.result.content()).isEqualTo("done");
        assertThat(run.toolResultMessageOfSecondRequest("search").text()).isEqualTo("blocked");
        assertThat(run.everythingTheLlmReceived()).doesNotContain("injected");
    }
}
