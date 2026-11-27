package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServiceTokenStream;
import dev.langchain4j.service.AiServiceTokenStreamParameters;
import dev.langchain4j.service.guardrail.ToolGuardrailService;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * A {@link ToolServiceContext} that a framework builds: a tool runs with the guardrails of the key that
 * {@link ToolGuardrailService#bind(Class, AiServiceTool)} returned for it, and a tool without a key is bound on demand,
 * so it gets at least the guardrails that apply to every tool (fail closed).
 */
class ToolGuardrailKeysTest {

    interface Assistant {}

    private static final ToolSpecification SEARCH =
            ToolSpecification.builder().name("search").build();
    private static final ToolExecutionRequest CALL = ToolExecutionRequest.builder()
            .id("1")
            .name("search")
            .arguments("{}")
            .build();
    private static final ToolExecutor TOOL = (request, memoryId) -> "found";

    // the result factories are default methods of the interface, which lambdas cannot call
    private static final ToolInputGuardrail INPUT = request -> null;

    private static ToolInputGuardrail rejecting(String message) {
        return request -> INPUT.failure(message);
    }

    private static ToolExecutionResult execute(ToolServiceContext context) {
        return new ToolService()
                .startRound(context, InvocationContext.builder().build(), null)
                .executeTool(CALL, null, null)
                .result();
    }

    private static AiServiceTool search(ToolInputGuardrail... guardrails) {
        return AiServiceTool.builder()
                .toolSpecification(SEARCH)
                .toolExecutor(TOOL)
                .toolInputGuardrails(guardrails)
                .build();
    }

    @Test
    void a_tool_with_a_key_runs_with_the_guardrails_of_its_key() {
        var service = ToolGuardrailService.builder(Assistant.class).build();
        var context = ToolServiceContext.builder()
                .effectiveTools(List.of(SEARCH))
                .toolExecutors(Map.of("search", TOOL))
                .toolGuardrailService(service)
                .toolGuardrailKeys(Map.of("search", service.bind(null, search(rejecting("own guardrail")))))
                .build();

        assertThat(execute(context))
                .returns(true, ToolExecutionResult::isError)
                .returns("own guardrail", ToolExecutionResult::resultText);
    }

    @Test
    void a_tool_without_a_key_runs_with_the_guardrails_that_apply_to_every_tool() {
        var service = ToolGuardrailService.builder(Assistant.class)
                .inputGuardrails(rejecting("global guardrail"))
                .build();
        var context = ToolServiceContext.builder()
                .effectiveTools(List.of(SEARCH))
                .toolExecutors(Map.of("search", TOOL))
                .toolGuardrailService(service)
                .build();

        assertThat(execute(context))
                .returns(true, ToolExecutionResult::isError)
                .returns("global guardrail", ToolExecutionResult::resultText);
    }

    @Test
    void a_tool_bound_on_demand_gets_its_specification_from_the_context() {
        var specifications = new CopyOnWriteArrayList<ToolSpecification>();
        ToolInputGuardrail recording = request -> {
            specifications.add(request.toolSpecification());
            return ToolInputGuardrailResult.success();
        };
        var specification = SEARCH.toBuilder().description("Searches the web").build();
        var service =
                ToolGuardrailService.builder(Assistant.class).inputGuardrails(recording).build();
        var context = ToolServiceContext.builder()
                .effectiveTools(List.of(specification))
                .toolExecutors(Map.of("search", TOOL))
                .toolGuardrailService(service)
                .build();

        execute(context);

        assertThat(specifications).containsExactly(specification);
    }

    @Test
    void a_key_of_another_service_fails_the_call() {
        var service = ToolGuardrailService.builder(Assistant.class).build();
        var otherService = ToolGuardrailService.builder(Assistant.class).build();
        var context = ToolServiceContext.builder()
                .effectiveTools(List.of(SEARCH))
                .toolExecutors(Map.of("search", TOOL))
                .toolGuardrailService(service)
                .toolGuardrailKeys(Map.of("search", otherService.bind(null, search())))
                .build();

        assertThatThrownBy(() -> execute(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("another tool guardrail service");
    }

    @Test
    void without_a_tool_guardrail_service_no_tool_guardrails_run() {
        var context = ToolServiceContext.builder()
                .effectiveTools(List.of(SEARCH))
                .toolExecutors(Map.of("search", TOOL))
                .build();

        assertThat(execute(context))
                .returns(false, ToolExecutionResult::isError)
                .returns("found", ToolExecutionResult::resultText);
    }

    @Test
    void a_token_stream_gives_a_framework_built_context_the_common_guardrail_parameters() throws Exception {
        var seenParams = new CopyOnWriteArrayList<GuardrailRequestParams>();
        ToolInputGuardrail recording = request -> {
            seenParams.add(request.requestParams());
            return ToolInputGuardrailResult.success();
        };
        var service =
                ToolGuardrailService.builder(Assistant.class).inputGuardrails(recording).build();
        var toolServiceContext = ToolServiceContext.builder()
                .effectiveTools(List.of(SEARCH))
                .toolExecutors(Map.of("search", TOOL))
                .toolGuardrailService(service)
                .build();
        var userMessage = UserMessage.from("where?");
        var invocationContext =
                InvocationContext.builder().userMessage(userMessage).build();
        var commonParams = GuardrailRequestParams.builder()
                .userMessageTemplate("{{question}}")
                .variables(Map.of("question", "where?"))
                .invocationContext(invocationContext)
                .build();
        var context = AiServiceContext.create(Assistant.class);
        context.streamingChatModel = MockStreamingChatModel.thatResponds(AiMessage.from(CALL), AiMessage.from("done"));

        var response = new CompletableFuture<ChatResponse>();
        new AiServiceTokenStream(AiServiceTokenStreamParameters.builder()
                        .messages(List.of(userMessage))
                        .toolServiceContext(toolServiceContext)
                        .toolArgumentsErrorHandler(context.toolService.argumentsErrorHandler())
                        .toolExecutionErrorHandler(context.toolService.executionErrorHandler())
                        .context(context)
                        .invocationContext(invocationContext)
                        .commonGuardrailParams(commonParams)
                        .build())
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(response::complete)
                .onError(response::completeExceptionally)
                .start();

        assertThat(response.get(10, TimeUnit.SECONDS).aiMessage().text()).isEqualTo("done");
        assertThat(seenParams)
                .singleElement()
                .returns("{{question}}", GuardrailRequestParams::userMessageTemplate)
                .returns(Map.of("question", "where?"), GuardrailRequestParams::variables);
    }
}
