package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * The contract a framework that runs its own tool loop relies on to reuse the tool guardrails: it builds a
 * {@link ToolGuardrailService} for an AI service, binds each of its tools with {@code bind(null, tool)} (its tools
 * have no {@code Method}, and carry the guardrails the framework found for them), and runs each tool call through
 * {@link ToolGuardrailService#executeGuardrails}. No {@code AiServices} is involved.
 */
class ToolGuardrailServiceFrameworkReuseTest {

    interface FrameworkAiService {}

    private static final ToolSpecification SPECIFICATION =
            ToolSpecification.builder().name("transfer").build();
    private static final ToolSource SOURCE = ToolSource.mcp("framework-server");
    private static final String ORIGINAL = "{\"amount\":100}";
    private static final String REWRITTEN = "{\"amount\":10}";
    private static final ToolExecutionRequest CALL = ToolExecutionRequest.builder()
            .id("call-1")
            .name("transfer")
            .arguments(ORIGINAL)
            .build();
    private static final GuardrailRequestParams PARAMS = GuardrailRequestParams.builder()
            .userMessageTemplate("t")
            .variables(Map.of())
            .invocationContext(InvocationContext.builder().build())
            .build();

    /** The executor of the framework: not a {@code DefaultToolExecutor}, so there is no method to read. */
    private static final ToolExecutor FRAMEWORK_EXECUTOR = (request, memoryId) -> "unused by the guardrail service";

    private final List<String> calls = new CopyOnWriteArrayList<>();

    private ToolInputGuardrail input(
            String name, Function<ToolInputGuardrailRequest, ToolInputGuardrailResult> result) {
        return new ToolInputGuardrail() {
            @Override
            public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
                calls.add(name);
                return result.apply(request);
            }
        };
    }

    private ToolOutputGuardrail output(
            String name, Function<ToolOutputGuardrailRequest, ToolOutputGuardrailResult> result) {
        return new ToolOutputGuardrail() {
            @Override
            public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
                calls.add(name);
                return result.apply(request);
            }
        };
    }

    private static AiServiceTool frameworkTool(ToolInputGuardrail input, ToolOutputGuardrail output) {
        return AiServiceTool.builder()
                .toolSpecification(SPECIFICATION)
                .toolExecutor(FRAMEWORK_EXECUTOR)
                .toolSource(SOURCE)
                .toolInputGuardrails(input)
                .toolOutputGuardrails(output)
                .build();
    }

    private Function<ToolExecutionRequest, ToolExecutionResult> recordingTool(List<ToolExecutionRequest> requests) {
        return request -> {
            calls.add("tool");
            requests.add(request);
            return ToolExecutionResult.builder().resultText("done").build();
        };
    }

    @Test
    void the_global_guardrails_run_before_the_guardrails_of_the_tool_and_a_rewrite_reaches_the_tool() {
        var service = ToolGuardrailService.builder(FrameworkAiService.class)
                .inputGuardrails(input("global input", request -> ToolInputGuardrailResult.success()))
                .outputGuardrails(output("global output", request -> ToolOutputGuardrailResult.success()))
                .build();
        var key = service.bind(
                null,
                frameworkTool(
                        input("tool input", request -> ToolInputGuardrailResult.successWith(REWRITTEN)),
                        output("tool output", request -> ToolOutputGuardrailResult.success())));
        var toolRequests = new ArrayList<ToolExecutionRequest>();

        var execution = service.executeGuardrails(key, CALL, PARAMS, recordingTool(toolRequests));

        assertThat(key)
                .returns(SPECIFICATION, ToolGuardrailService.ToolKey::toolSpecification)
                .returns(SOURCE, ToolGuardrailService.ToolKey::toolSource);
        assertThat(calls).containsExactly("global input", "tool input", "tool", "global output", "tool output");
        assertThat(toolRequests).extracting(ToolExecutionRequest::arguments).containsExactly(REWRITTEN);
        assertThat(execution.request())
                .returns(REWRITTEN, ToolExecutionRequest::arguments)
                .returns(CALL.id(), ToolExecutionRequest::id);
        assertThat(execution.result())
                .returns("done", ToolExecutionResult::resultText)
                .returns(false, ToolExecutionResult::isError);
        assertThat(execution.failedStage()).isEmpty();
    }

    @Test
    void the_framework_gives_the_llm_the_text_of_the_result_with_the_note_of_a_rewrite() {
        var service = ToolGuardrailService.builder(FrameworkAiService.class).build();
        var key = service.bind(
                null,
                frameworkTool(
                        input("tool input", request -> ToolInputGuardrailResult.successWith(REWRITTEN)),
                        output("tool output", request -> ToolOutputGuardrailResult.success())));

        var execution = service.executeGuardrails(key, CALL, PARAMS, recordingTool(new ArrayList<>()));

        // the framework keeps the call of the LLM in its history, so the LLM is told what ran
        assertThat(execution.resultTextForLlm(execution.result().resultText()))
                .isEqualTo("<tool_arguments_changed>The arguments of this call were changed before it ran: "
                        + REWRITTEN + "</tool_arguments_changed>\ndone");
        // the result itself stays the result of the tool
        assertThat(execution.result().resultText()).isEqualTo("done");
    }

    @Test
    void the_framework_gives_the_llm_the_text_of_the_result_as_is_when_the_note_is_disabled() {
        var service = ToolGuardrailService.builder(FrameworkAiService.class)
                .inputGuardrailsConfig(ToolInputGuardrailsConfig.builder()
                        .argumentsRewriteNoteEnabled(false)
                        .build())
                .build();
        var key = service.bind(
                null,
                frameworkTool(
                        input("tool input", request -> ToolInputGuardrailResult.successWith(REWRITTEN)),
                        output("tool output", request -> ToolOutputGuardrailResult.success())));

        var execution = service.executeGuardrails(key, CALL, PARAMS, recordingTool(new ArrayList<>()));

        assertThat(execution.resultTextForLlm(execution.result().resultText())).isEqualTo("done");
    }

    @Test
    void the_guardrail_requests_carry_the_specification_and_the_source_of_the_key() {
        var inputRequests = new ArrayList<ToolInputGuardrailRequest>();
        var outputRequests = new ArrayList<ToolOutputGuardrailRequest>();
        var service = ToolGuardrailService.builder(FrameworkAiService.class).build();
        var key = service.bind(
                null,
                frameworkTool(
                        input("tool input", request -> {
                            inputRequests.add(request);
                            return ToolInputGuardrailResult.success();
                        }),
                        output("tool output", request -> {
                            outputRequests.add(request);
                            return ToolOutputGuardrailResult.success();
                        })));

        service.executeGuardrails(key, CALL, PARAMS, recordingTool(new ArrayList<>()));

        assertThat(inputRequests)
                .singleElement()
                .returns(SPECIFICATION, ToolInputGuardrailRequest::toolSpecification)
                .returns(SOURCE, ToolInputGuardrailRequest::toolSource);
        assertThat(outputRequests)
                .singleElement()
                .returns(SPECIFICATION, ToolOutputGuardrailRequest::toolSpecification)
                .returns(SOURCE, ToolOutputGuardrailRequest::toolSource);
    }

    @Test
    void a_non_fatal_input_failure_becomes_an_error_result_with_the_message_verbatim_and_the_tool_does_not_run() {
        var service = ToolGuardrailService.builder(FrameworkAiService.class)
                .inputGuardrails(input("global input", request -> ToolInputGuardrailResult.success()))
                .build();
        ToolInputGuardrail rejecting = new ToolInputGuardrail() {
            @Override
            public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
                calls.add("tool input");
                return failure("Transfers above 50 need an approval");
            }
        };
        var key = service.bind(
                null, frameworkTool(rejecting, output("tool output", request -> ToolOutputGuardrailResult.success())));
        var toolRequests = new ArrayList<ToolExecutionRequest>();

        var execution = service.executeGuardrails(key, CALL, PARAMS, recordingTool(toolRequests));

        assertThat(calls).containsExactly("global input", "tool input");
        assertThat(toolRequests).isEmpty();
        assertThat(execution.result())
                .returns("Transfers above 50 need an approval", ToolExecutionResult::resultText)
                .returns(true, ToolExecutionResult::isError);
        assertThat(execution.failedStage()).contains(ToolGuardrailStage.INPUT);
    }

    @Test
    void a_non_fatal_output_failure_becomes_an_error_result_with_the_message_verbatim() {
        var service = ToolGuardrailService.builder(FrameworkAiService.class).build();
        ToolOutputGuardrail rejecting = new ToolOutputGuardrail() {
            @Override
            public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
                return failure("The result contains an account number");
            }
        };
        var key = service.bind(
                null, frameworkTool(input("tool input", request -> ToolInputGuardrailResult.success()), rejecting));

        var execution = service.executeGuardrails(key, CALL, PARAMS, recordingTool(new ArrayList<>()));

        assertThat(execution.result())
                .returns("The result contains an account number", ToolExecutionResult::resultText)
                .returns(true, ToolExecutionResult::isError);
        assertThat(execution.failedStage()).contains(ToolGuardrailStage.OUTPUT);
    }
}
