package dev.langchain4j.service.guardrail;

import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.ORIGINAL_ARGUMENTS;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.REWRITTEN_ARGUMENTS;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.input;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.inputFailure;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.onInputOf;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.onOutputOf;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.outputFailure;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.run;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.toolCall;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.ToolInputGuardrailException;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.Mode;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.SearchTool;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.search.simple.SimpleToolSearchStrategy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tool guardrails when the model calls several tools in one round, executed one after the other or concurrently, and
 * the calls that guardrails never see: a tool name that does not exist (D14) and the built-in tool search tool.
 */
class ToolGuardrailToolRoundTests extends BaseGuardrailTests {

    private static final List<String> TOOLS = List.of("passes", "rewritten", "rejected", "blocked");

    /** Four tools, each with a {@link SearchTool} of its own so a test sees what each one ran with. */
    private record Tools(Map<String, SearchTool> byName) {

        static Tools create() {
            return new Tools(TOOLS.stream().collect(Collectors.toMap(name -> name, name -> SearchTool.echoing())));
        }

        void register(AiServices<?> services) {
            services.tools(TOOLS.stream().map(this::tool).toList());
        }

        private AiServiceTool tool(String name) {
            return AiServiceTool.builder()
                    .toolSpecification(ToolSpecification.builder()
                            .name(name)
                            .description(name)
                            .build())
                    .toolExecutor(byName.get(name))
                    .build();
        }
    }

    /** One round in which the model calls all four tools, then answers. */
    private static List<AiMessage> callingAllToolsThenAnswering() {
        return List.of(
                AiMessage.from(TOOLS.stream()
                        .map(name -> toolCall("call-" + name, name, ORIGINAL_ARGUMENTS))
                        .toList()),
                AiMessage.from("done"));
    }

    private enum Execution {
        SEQUENTIAL,
        CONCURRENT
    }

    private static void guard(AiServices<?> services, Execution execution) {
        if (execution == Execution.CONCURRENT) {
            services.executeToolsConcurrently();
        }
        services.toolInputGuardrails(
                        onInputOf("rewritten", request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS)),
                        onInputOf("rejected", request -> inputFailure("denied")))
                .toolOutputGuardrails(onOutputOf("blocked", request -> outputFailure("blocked")));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void every_call_of_a_sequential_round_gets_its_own_outcome(Mode mode) {
        assertEachCallGetsItsOwnOutcome(mode, Execution.SEQUENTIAL);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void every_call_of_a_concurrent_round_gets_its_own_outcome(Mode mode) {
        assertEachCallGetsItsOwnOutcome(mode, Execution.CONCURRENT);
    }

    private static void assertEachCallGetsItsOwnOutcome(Mode mode, Execution execution) {
        var tools = Tools.create();

        var run = run(mode, callingAllToolsThenAnswering(), services -> {
            tools.register(services);
            guard(services, execution);
        });

        assertThat(run.answerText()).isEqualTo("done");
        assertThat(tools.byName().get("passes").receivedArguments).containsExactly(ORIGINAL_ARGUMENTS);
        // the rewrite of one call does not leak into the others
        assertThat(tools.byName().get("rewritten").receivedArguments).containsExactly(REWRITTEN_ARGUMENTS);
        // the rejected tool never ran
        assertThat(tools.byName().get("rejected").receivedArguments).isEmpty();
        assertThat(tools.byName().get("blocked").receivedArguments).containsExactly(ORIGINAL_ARGUMENTS);

        // the results reach the LLM in the order of its calls, each with the outcome of its own call
        var messages = run.toolResultMessagesOfSecondRequest();
        assertThat(messages).extracting(ToolExecutionResultMessage::toolName).containsExactlyElementsOf(TOOLS);
        assertThat(messages)
                .extracting(ToolExecutionResultMessage::text, ToolExecutionResultMessage::isError)
                .containsExactly(
                        tuple("found " + ORIGINAL_ARGUMENTS, false),
                        tuple("found " + REWRITTEN_ARGUMENTS, false),
                        tuple("denied", true),
                        tuple("blocked", true));
        assertThat(run.toolEvents).hasSize(4);
        assertThat(run.afterToolExecutions).hasSize(4);
        // the two input guardrails are global, so each runs for each of the 4 calls; the output guardrail runs for the
        // 3 tools that ran (the rejected one has no result to check)
        assertThat(run.inputGuardrailEvents).hasSize(2 * 4);
        assertThat(run.outputGuardrailEvents).hasSize(3);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_fatal_failure_of_one_call_of_a_concurrent_round_fails_the_invocation(Mode mode) {
        var tools = Tools.create();

        var run = run(mode, callingAllToolsThenAnswering(), services -> {
            tools.register(services);
            services.executeToolsConcurrently()
                    .toolInputGuardrails(
                            onInputOf("rejected", request -> ToolGuardrailTestSupport.inputFatal("attack detected")));
        });

        assertThat(run.failure())
                .isInstanceOf(ToolInputGuardrailException.class)
                .hasMessageContaining("attack detected");
        assertThat(tools.byName().get("rejected").receivedArguments).isEmpty();
        assertThat(run.errorHandlerCalls).hasValue(0);
    }

    // ---------- calls that guardrails never see ----------

    @ParameterizedTest
    @EnumSource(Mode.class)
    void a_hallucinated_tool_name_skips_the_guardrails(Mode mode) {
        var inputCalls = new AtomicInteger();
        var outputCalls = new AtomicInteger();
        var responses = List.of(AiMessage.from(toolCall("1", "does-not-exist", "{}")), AiMessage.from("done"));

        var run = run(
                mode,
                responses,
                services -> services.hallucinatedToolNameStrategy(
                                request -> ToolExecutionResultMessage.from(request, "no such tool"))
                        .toolInputGuardrails(input(request -> {
                            inputCalls.incrementAndGet();
                            return ToolInputGuardrailResult.success();
                        }))
                        .toolOutputGuardrails(ToolGuardrailTestSupport.output(request -> {
                            outputCalls.incrementAndGet();
                            return ToolOutputGuardrailResult.success();
                        })));

        assertThat(run.answerText()).isEqualTo("done");
        // there is no tool to guard, so the LLM just gets the answer of the hallucination strategy
        assertThat(inputCalls).hasValue(0);
        assertThat(outputCalls).hasValue(0);
        assertThat(run.toolResultMessageOfSecondRequest("does-not-exist").text())
                .isEqualTo("no such tool");
        assertThat(run.inputGuardrailEvents).isEmpty();
    }

    @Test
    void the_built_in_tool_search_tool_is_not_guarded_but_the_tools_it_finds_are() {
        var toolNames = new CopyOnWriteArrayList<String>();
        var strategy = new SimpleToolSearchStrategy();
        var searchToolName = strategy.getToolSearchTools(
                        InvocationContext.builder().build())
                .get(0)
                .name();
        var tool = SearchTool.echoing();
        var responses = List.of(
                AiMessage.from(toolCall("1", searchToolName, "{\"terms\":[\"search\"]}")),
                AiMessage.from(ToolGuardrailTestSupport.searchCall()),
                AiMessage.from("done"));

        var run = run(Mode.SYNC, responses, services -> {
            ToolGuardrailTestSupport.registerTool(services, tool, builder -> {});
            services.toolSearchStrategy(strategy).toolInputGuardrails(input(request -> {
                toolNames.add(request.toolName());
                return ToolInputGuardrailResult.success();
            }));
        });

        assertThat(run.answerText()).isEqualTo("done");
        assertThat(tool.receivedArguments).containsExactly(ORIGINAL_ARGUMENTS);
        // the guardrail saw the real tool only, never the search tool
        assertThat(toolNames).containsExactly("search");
    }
}
