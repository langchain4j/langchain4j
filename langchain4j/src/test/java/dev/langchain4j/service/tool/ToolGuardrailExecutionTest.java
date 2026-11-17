package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailException;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailException;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.event.ToolInputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.ToolOutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import dev.langchain4j.observability.api.listener.ToolInputGuardrailExecutedListener;
import dev.langchain4j.observability.api.listener.ToolOutputGuardrailExecutedListener;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.ToolGuardrailService;
import dev.langchain4j.service.tool.search.simple.SimpleToolSearchStrategy;
import dev.langchain4j.test.mock.MockChatModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ToolGuardrailExecutionTest {

    private static final String ORIGINAL_ARGUMENTS = "{\"q\":\"a\"}";
    private static final String REWRITTEN_ARGUMENTS = "{\"q\":\"b\"}";
    private static final Object RAW_RESULT = new Object();
    // the result factories are default methods of the interfaces, which lambdas cannot call
    private static final ToolInputGuardrail INPUT = request -> null;
    private static final ToolOutputGuardrail OUTPUT = request -> null;
    private static final ToolSpecification SEARCH =
            ToolSpecification.builder().name("search").description("Searches").build();

    interface Assistant {

        Result<String> chat(@UserMessage String message);
    }

    /** A tool that records the arguments it ran with and returns a result with attributes and a raw object. */
    static final class RecordingTool implements ToolExecutor {

        final List<String> receivedArguments = new CopyOnWriteArrayList<>();
        private final boolean failing;

        RecordingTool(boolean failing) {
            this.failing = failing;
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
            receivedArguments.add(request.arguments());
            if (failing) {
                throw new IllegalStateException("boom");
            }
            return ToolExecutionResult.builder()
                    .resultText("found " + request.arguments())
                    .result(RAW_RESULT)
                    .attributes(Map.of("k", "v"))
                    .build();
        }
    }

    /** Everything a run of the AI service reports. */
    private static final class Run {

        final RecordingTool tool;
        final MockChatModel model;
        final ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        final List<ToolExecution> afterToolExecutions = new CopyOnWriteArrayList<>();
        final List<ToolExecutedEvent> toolEvents = new CopyOnWriteArrayList<>();
        final List<ToolInputGuardrailExecutedEvent> inputGuardrailEvents = new CopyOnWriteArrayList<>();
        final List<ToolOutputGuardrailExecutedEvent> outputGuardrailEvents = new CopyOnWriteArrayList<>();
        final List<ToolInputGuardrailRequest> inputRequests = new CopyOnWriteArrayList<>();
        final List<ToolOutputGuardrailRequest> outputRequests = new CopyOnWriteArrayList<>();
        Result<String> result;

        Run(RecordingTool tool, MockChatModel model) {
            this.tool = tool;
            this.model = model;
        }

        /** The tool result message the LLM got in its second request. */
        ToolExecutionResultMessage toolResultMessageOfSecondRequest() {
            return model.requests().get(1).messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .findFirst()
                    .orElseThrow();
        }

        /** The tool calls of the AI message that chat memory holds. */
        List<ToolExecutionRequest> toolCallsInMemory() {
            return memory.messages().stream()
                    .filter(AiMessage.class::isInstance)
                    .map(AiMessage.class::cast)
                    .filter(AiMessage::hasToolExecutionRequests)
                    .flatMap(message -> message.toolExecutionRequests().stream())
                    .toList();
        }
    }

    private static ToolExecutionRequest searchCall(String id) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("search")
                .arguments(ORIGINAL_ARGUMENTS)
                .build();
    }

    private static MockChatModel modelCallingSearch() {
        return MockChatModel.thatResponds(AiMessage.from(searchCall("1")), AiMessage.from("done"));
    }

    private static Run run(
            AiServiceTool.Builder toolBuilder, boolean failingTool, Consumer<AiServices<Assistant>> configuration) {
        return run(toolBuilder, failingTool, modelCallingSearch(), configuration);
    }

    private static Run run(
            AiServiceTool.Builder toolBuilder,
            boolean failingTool,
            MockChatModel model,
            Consumer<AiServices<Assistant>> configuration) {
        var run = new Run(new RecordingTool(failingTool), model);
        var tool = toolBuilder.toolSpecification(SEARCH).toolExecutor(run.tool).build();

        var services = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(run.memory)
                .tools(List.<AiServiceTool>of(tool))
                .afterToolExecution(run.afterToolExecutions::add)
                .registerListener((ToolExecutedEventListener) run.toolEvents::add)
                .registerListener((ToolInputGuardrailExecutedListener) run.inputGuardrailEvents::add)
                .registerListener((ToolOutputGuardrailExecutedListener) run.outputGuardrailEvents::add);
        configuration.accept(services);

        run.result = services.build().chat("hello");
        return run;
    }

    private static ToolInputGuardrail recordingInput(
            Run[] holder, java.util.function.Function<ToolInputGuardrailRequest, ToolInputGuardrailResult> action) {
        return request -> action.apply(request);
    }

    private static ToolInputGuardrailResult failInput(String message) {
        return INPUT.failure(message);
    }

    // ---------- no guardrails ----------

    @Test
    void a_tool_without_guardrails_runs_as_before() {
        var run = run(AiServiceTool.builder(), false, services -> {});

        assertThat(run.tool.receivedArguments).containsExactly(ORIGINAL_ARGUMENTS);
        assertThat(run.result.content()).isEqualTo("done");
        assertThat(run.result.toolExecutions())
                .singleElement()
                .satisfies(execution -> assertThat(execution)
                        .returns("found " + ORIGINAL_ARGUMENTS, ToolExecution::result)
                        .returns(RAW_RESULT, ToolExecution::resultObject)
                        .returns(Map.of("k", "v"), ToolExecution::attributes));
        assertThat(run.inputGuardrailEvents).isEmpty();
        assertThat(run.outputGuardrailEvents).isEmpty();
    }

    @Test
    void guardrails_are_not_applied_to_a_hallucinated_tool_name() {
        var inputCalls = new AtomicInteger();
        var outputCalls = new AtomicInteger();
        var model = MockChatModel.thatResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("unknown")
                        .arguments("{}")
                        .build()),
                AiMessage.from("done"));

        run(
                AiServiceTool.builder(),
                false,
                model,
                services -> services.hallucinatedToolNameStrategy(
                                request -> ToolExecutionResultMessage.from(request, "no such tool"))
                        .toolInputGuardrails((ToolInputGuardrail) request -> {
                            inputCalls.incrementAndGet();
                            return ToolInputGuardrailResult.success();
                        })
                        .toolOutputGuardrails((ToolOutputGuardrail) request -> {
                            outputCalls.incrementAndGet();
                            return ToolOutputGuardrailResult.success();
                        }));

        assertThat(inputCalls).hasValue(0);
        assertThat(outputCalls).hasValue(0);
    }

    // ---------- input guardrails ----------

    @Test
    void an_input_rewrite_is_what_the_tool_runs_with_and_what_everything_downstream_carries() {
        ToolInputGuardrail rewrite = request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS);
        var beforeRequests = new ArrayList<ToolExecutionRequest>();

        var run = run(
                AiServiceTool.builder().toolInputGuardrails(rewrite),
                false,
                services -> services.beforeToolExecution(before -> beforeRequests.add(before.request())));

        assertThat(run.tool.receivedArguments).containsExactly(REWRITTEN_ARGUMENTS);
        // beforeToolExecution has already run when the guardrails rewrite, so it saw the original
        assertThat(beforeRequests)
                .singleElement()
                .extracting(ToolExecutionRequest::arguments)
                .isEqualTo(ORIGINAL_ARGUMENTS);
        assertThat(run.afterToolExecutions)
                .singleElement()
                .satisfies(execution -> assertThat(execution.request())
                        .returns("1", ToolExecutionRequest::id)
                        .returns("search", ToolExecutionRequest::name)
                        .returns(REWRITTEN_ARGUMENTS, ToolExecutionRequest::arguments));
        assertThat(run.result.toolExecutions())
                .singleElement()
                .extracting(execution -> execution.request().arguments())
                .isEqualTo(REWRITTEN_ARGUMENTS);
        assertThat(run.toolEvents)
                .singleElement()
                .extracting(event -> event.request().arguments())
                .isEqualTo(REWRITTEN_ARGUMENTS);
        // the tool call stored in chat memory matches what the tool ran with
        assertThat(run.toolCallsInMemory())
                .singleElement()
                .extracting(ToolExecutionRequest::arguments)
                .isEqualTo(REWRITTEN_ARGUMENTS);
    }

    @Test
    void without_chat_memory_the_next_request_carries_the_rewritten_tool_call() {
        ToolInputGuardrail rewrite = request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS);
        var model = modelCallingSearch();

        AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(SEARCH)
                        .toolExecutor(new RecordingTool(false))
                        .toolInputGuardrails(rewrite)
                        .build()))
                .build()
                .chat("hello");

        assertThat(model.requests().get(1).messages())
                .filteredOn(AiMessage.class::isInstance)
                .map(AiMessage.class::cast)
                .singleElement()
                .extracting(message -> message.toolExecutionRequests().get(0).arguments())
                .isEqualTo(REWRITTEN_ARGUMENTS);
    }

    @Test
    void an_input_rewrite_reaches_the_output_guardrail_request() {
        var run = new Run[1];
        var seenArguments = new ArrayList<String>();
        ToolInputGuardrail rewrite = request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS);
        ToolOutputGuardrail output = request -> {
            seenArguments.add(request.arguments());
            return ToolOutputGuardrailResult.success();
        };

        run[0] = run(
                AiServiceTool.builder().toolInputGuardrails(rewrite).toolOutputGuardrails(output),
                false,
                services -> {});

        assertThat(seenArguments).containsExactly(REWRITTEN_ARGUMENTS);
    }

    @Test
    void a_non_fatal_input_failure_does_not_run_the_tool_and_reports_the_message_verbatim() {
        ToolInputGuardrail reject = request -> failInput("Not allowed: use another query");

        var run = run(AiServiceTool.builder().toolInputGuardrails(reject), false, services -> {});

        assertThat(run.tool.receivedArguments).isEmpty();
        assertThat(run.toolResultMessageOfSecondRequest())
                .returns(true, ToolExecutionResultMessage::isError)
                .returns("Not allowed: use another query", ToolExecutionResultMessage::text);
        assertThat(run.result.toolExecutions())
                .singleElement()
                .returns(true, ToolExecution::hasFailed)
                .returns("Not allowed: use another query", ToolExecution::result);
        assertThat(run.toolEvents).singleElement().returns(true, ToolExecutedEvent::isError);
        assertThat(run.result.content()).isEqualTo("done");
    }

    @Test
    void a_rejected_call_after_a_rewrite_is_reported_with_the_rewritten_request() {
        ToolInputGuardrail rewrite = request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS);
        ToolInputGuardrail reject = request -> failInput("no");

        var run = run(AiServiceTool.builder().toolInputGuardrails(rewrite, reject), false, services -> {});

        assertThat(run.tool.receivedArguments).isEmpty();
        assertThat(run.afterToolExecutions)
                .singleElement()
                .extracting(execution -> execution.request().arguments())
                .isEqualTo(REWRITTEN_ARGUMENTS);
        assertThat(run.toolEvents)
                .singleElement()
                .extracting(event -> event.request().arguments())
                .isEqualTo(REWRITTEN_ARGUMENTS);
    }

    @Test
    void a_fatal_input_failure_aborts_the_invocation_past_the_tool_execution_error_handler() {
        ToolInputGuardrail fatal = request -> INPUT.fatal("stop");
        var handlerCalls = new AtomicInteger();
        var tool = new RecordingTool(false);
        var model = modelCallingSearch();
        var toolEvents = new CopyOnWriteArrayList<ToolExecutedEvent>();

        var assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(SEARCH)
                        .toolExecutor(tool)
                        .toolInputGuardrails(fatal)
                        .build()))
                .toolExecutionErrorHandler((error, context) -> {
                    handlerCalls.incrementAndGet();
                    return ToolErrorHandlerResult.text("handled");
                })
                .registerListener((ToolExecutedEventListener) toolEvents::add)
                .build();

        assertThatThrownBy(() -> assistant.chat("hello")).isInstanceOf(ToolInputGuardrailException.class);

        assertThat(handlerCalls).hasValue(0);
        assertThat(tool.receivedArguments).isEmpty();
        assertThat(toolEvents).singleElement().returns(true, ToolExecutedEvent::isError);
    }

    @Test
    void a_tool_that_throws_after_an_input_rewrite_is_reported_with_the_rewritten_request() {
        ToolInputGuardrail rewrite = request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS);
        var tool = new RecordingTool(true);
        var events = new CopyOnWriteArrayList<ToolExecutedEvent>();

        var assistant = AiServices.builder(Assistant.class)
                .chatModel(modelCallingSearch())
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(SEARCH)
                        .toolExecutor(tool)
                        .toolInputGuardrails(rewrite)
                        .build()))
                .toolExecutionErrorHandler((error, context) -> {
                    throw new IllegalArgumentException("rethrown", error);
                })
                .registerListener((ToolExecutedEventListener) events::add)
                .build();

        assertThatThrownBy(() -> assistant.chat("hello")).hasMessage("rethrown");

        assertThat(tool.receivedArguments).containsExactly(REWRITTEN_ARGUMENTS);
        assertThat(events)
                .singleElement()
                .returns(true, ToolExecutedEvent::isError)
                .extracting(event -> event.request().arguments())
                .isEqualTo(REWRITTEN_ARGUMENTS);
    }

    @Test
    void a_fatal_output_failure_after_an_input_rewrite_is_reported_with_the_rewritten_request() {
        ToolInputGuardrail rewrite = request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS);
        ToolOutputGuardrail fatal = request -> OUTPUT.fatal("stop");
        var events = new CopyOnWriteArrayList<ToolExecutedEvent>();
        var model =
                MockChatModel.thatResponds(AiMessage.from(searchCall("1"), searchCall("2")), AiMessage.from("done"));

        var assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(SEARCH)
                        .toolExecutor(new RecordingTool(false))
                        .toolInputGuardrails(rewrite)
                        .toolOutputGuardrails(fatal)
                        .build()))
                .registerListener((ToolExecutedEventListener) events::add)
                .build();

        assertThatThrownBy(() -> assistant.chat("hello")).isInstanceOf(ToolOutputGuardrailException.class);

        // sequential: the first call fails the round, the second never starts
        assertThat(events)
                .singleElement()
                .returns(true, ToolExecutedEvent::isError)
                .satisfies(event -> assertThat(event.request())
                        .returns("1", ToolExecutionRequest::id)
                        .returns(REWRITTEN_ARGUMENTS, ToolExecutionRequest::arguments));
    }

    @Test
    void a_fatal_input_failure_after_a_rewrite_is_reported_with_the_rewritten_request() {
        ToolInputGuardrail rewrite = request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS);
        ToolInputGuardrail fatal = request -> INPUT.fatal("stop");
        var events = new CopyOnWriteArrayList<ToolExecutedEvent>();

        var assistant = AiServices.builder(Assistant.class)
                .chatModel(modelCallingSearch())
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(SEARCH)
                        .toolExecutor(new RecordingTool(false))
                        .toolInputGuardrails(rewrite, fatal)
                        .build()))
                .registerListener((ToolExecutedEventListener) events::add)
                .build();

        assertThatThrownBy(() -> assistant.chat("hello")).isInstanceOf(ToolInputGuardrailException.class);

        assertThat(events)
                .singleElement()
                .returns(true, ToolExecutedEvent::isError)
                .satisfies(event -> assertThat(event.request())
                        .returns("1", ToolExecutionRequest::id)
                        .returns(REWRITTEN_ARGUMENTS, ToolExecutionRequest::arguments));
    }

    // ---------- output guardrails ----------

    @Test
    void an_output_rewrite_keeps_the_attributes_and_drops_the_raw_result() {
        ToolOutputGuardrail rewrite = request -> ToolOutputGuardrailResult.successWith("sanitized");

        var run = run(AiServiceTool.builder().toolOutputGuardrails(rewrite), false, services -> {});

        assertThat(run.toolResultMessageOfSecondRequest())
                .returns("sanitized", ToolExecutionResultMessage::text)
                .returns(false, ToolExecutionResultMessage::isError)
                .returns(Map.of("k", "v"), ToolExecutionResultMessage::attributes);
        assertThat(run.result.toolExecutions())
                .singleElement()
                .returns("sanitized", ToolExecution::result)
                .returns(null, ToolExecution::resultObject)
                .returns(Map.of("k", "v"), ToolExecution::attributes);
        assertThat(run.afterToolExecutions).singleElement().returns("sanitized", ToolExecution::result);
        assertThat(run.toolEvents)
                .singleElement()
                .extracting(event -> event.resultContents().get(0))
                .isEqualTo(TextContent.from("sanitized"));
    }

    @Test
    void an_output_guardrail_that_keeps_the_result_keeps_the_raw_result_too() {
        ToolOutputGuardrail keep = request -> ToolOutputGuardrailResult.success();

        var run = run(AiServiceTool.builder().toolOutputGuardrails(keep), false, services -> {});

        assertThat(run.result.toolExecutions())
                .singleElement()
                .returns("found " + ORIGINAL_ARGUMENTS, ToolExecution::result)
                .returns(RAW_RESULT, ToolExecution::resultObject);
    }

    @Test
    void a_non_fatal_output_failure_is_an_error_result_with_the_message_verbatim() {
        ToolOutputGuardrail reject = request -> OUTPUT.failure("Result blocked, do not retry");

        var run = run(AiServiceTool.builder().toolOutputGuardrails(reject), false, services -> {});

        assertThat(run.tool.receivedArguments).containsExactly(ORIGINAL_ARGUMENTS);
        assertThat(run.toolResultMessageOfSecondRequest())
                .returns(true, ToolExecutionResultMessage::isError)
                .returns("Result blocked, do not retry", ToolExecutionResultMessage::text);
        assertThat(run.result.toolExecutions())
                .singleElement()
                .returns(true, ToolExecution::hasFailed)
                .returns(null, ToolExecution::resultObject);
    }

    @Test
    void a_fatal_output_failure_aborts_the_invocation_past_the_tool_execution_error_handler() {
        ToolOutputGuardrail fatal = request -> OUTPUT.fatal("stop");
        var handlerCalls = new AtomicInteger();

        var assistant = AiServices.builder(Assistant.class)
                .chatModel(modelCallingSearch())
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(SEARCH)
                        .toolExecutor(new RecordingTool(false))
                        .toolOutputGuardrails(fatal)
                        .build()))
                .toolExecutionErrorHandler((error, context) -> {
                    handlerCalls.incrementAndGet();
                    return ToolErrorHandlerResult.text("handled");
                })
                .build();

        assertThatThrownBy(() -> assistant.chat("hello")).isInstanceOf(ToolOutputGuardrailException.class);

        assertThat(handlerCalls).hasValue(0);
    }

    @Test
    void an_output_rewrite_to_empty_contents_aborts_the_invocation() {
        ToolOutputGuardrail empty = request -> OUTPUT.successWith(List.of(), false);

        var assistant = AiServices.builder(Assistant.class)
                .chatModel(modelCallingSearch())
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(SEARCH)
                        .toolExecutor(new RecordingTool(false))
                        .toolOutputGuardrails(empty)
                        .build()))
                .build();

        assertThatThrownBy(() -> assistant.chat("hello"))
                .isInstanceOf(ToolOutputGuardrailException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void output_guardrails_also_see_the_error_result_of_a_failed_tool() {
        var seen = new ArrayList<String>();
        ToolOutputGuardrail rewrite = request -> {
            seen.add(request.isError() + ":" + request.text());
            return ToolOutputGuardrailResult.successWith("sanitized error");
        };

        var run = run(
                AiServiceTool.builder().toolOutputGuardrails(rewrite),
                true,
                services -> services.toolExecutionErrorHandler(
                        (error, context) -> ToolErrorHandlerResult.text("tool failed")));

        assertThat(seen).containsExactly("true:tool failed");
        assertThat(run.toolResultMessageOfSecondRequest())
                .returns(true, ToolExecutionResultMessage::isError)
                .returns("sanitized error", ToolExecutionResultMessage::text);
    }

    @Test
    void an_output_guardrail_can_turn_an_error_result_into_a_success() {
        ToolOutputGuardrail recover =
                request -> ToolOutputGuardrailResult.successWith(List.of(TextContent.from("recovered")), false);

        var run = run(
                AiServiceTool.builder().toolOutputGuardrails(recover),
                true,
                services -> services.toolExecutionErrorHandler(
                        (error, context) -> ToolErrorHandlerResult.text("tool failed")));

        assertThat(run.toolResultMessageOfSecondRequest())
                .returns(false, ToolExecutionResultMessage::isError)
                .returns("recovered", ToolExecutionResultMessage::text);
    }

    // ---------- context of the guardrails ----------

    @Test
    void guardrails_get_the_parameters_of_the_invocation_and_their_events_reach_its_listeners() {
        var templates = new ArrayList<String>();
        ToolInputGuardrail input = request -> {
            templates.add(request.requestParams().userMessageTemplate());
            return ToolInputGuardrailResult.success();
        };
        ToolOutputGuardrail output = request -> ToolOutputGuardrailResult.success();

        var run = run(
                AiServiceTool.builder().toolInputGuardrails(input).toolOutputGuardrails(output), false, services -> {});

        assertThat(templates).containsExactly("hello");
        assertThat(run.inputGuardrailEvents).hasSize(1);
        assertThat(run.outputGuardrailEvents).hasSize(1);
    }

    @Test
    void
            without_invocation_parameters_the_guardrails_run_with_minimal_parameters_and_the_registrar_of_the_invocation() {
        var seen = new ArrayList<ToolInputGuardrailRequest>();
        ToolInputGuardrail input = request -> {
            seen.add(request);
            return ToolInputGuardrailResult.success();
        };
        var events = new CopyOnWriteArrayList<ToolInputGuardrailExecutedEvent>();
        var aiServiceContext = AiServiceContext.create(Assistant.class);
        aiServiceContext.chatModel = MockChatModel.thatResponds("done");
        aiServiceContext.eventListenerRegistrar.register((ToolInputGuardrailExecutedListener) events::add);
        aiServiceContext.toolService.tools(List.<AiServiceTool>of(AiServiceTool.builder()
                .toolSpecification(SEARCH)
                .toolExecutor(new RecordingTool(false))
                .build()));
        aiServiceContext.toolService.toolGuardrailService(ToolGuardrailService.builder(Assistant.class)
                .inputGuardrails(input)
                .build());
        var invocationContext = InvocationContext.builder().build();
        List<ChatMessage> messages = List.of(dev.langchain4j.data.message.UserMessage.from("hello"));
        // a context that does not come from an AI service invocation carries no parameters
        var toolServiceContext = aiServiceContext.toolService.createContext(invocationContext, null, messages);

        aiServiceContext.toolService.executeInferenceAndToolsLoop(
                aiServiceContext,
                null,
                ChatResponse.builder()
                        .aiMessage(AiMessage.from(searchCall("1")))
                        .build(),
                ChatRequestParameters.builder().build(),
                messages,
                null,
                invocationContext,
                toolServiceContext,
                false);

        assertThat(seen)
                .singleElement()
                .extracting(ToolInputGuardrailRequest::requestParams)
                .returns("", params -> params.userMessageTemplate())
                .returns(Map.of(), params -> params.variables());
        assertThat(events).hasSize(1);
    }

    @Test
    void guardrails_of_every_level_apply_with_the_tool_source() {
        var sources = new ArrayList<String>();
        ToolInputGuardrail global = request -> {
            sources.add(request.toolSource().toString());
            return ToolInputGuardrailResult.success();
        };

        run(AiServiceTool.builder(), false, services -> services.toolInputGuardrails(global));

        assertThat(sources).hasSize(1);
    }

    // ---------- execution modes ----------

    @Test
    void guardrails_apply_to_every_tool_of_a_round_executed_concurrently() {
        ToolInputGuardrail rewrite = request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS);
        var tool = new RecordingTool(false);
        var model = MockChatModel.thatResponds(
                AiMessage.from(searchCall("1"), searchCall("2"), searchCall("3")), AiMessage.from("done"));
        var events = new CopyOnWriteArrayList<ToolExecutedEvent>();
        var memory = MessageWindowChatMemory.withMaxMessages(20);

        var assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(memory)
                .executeToolsConcurrently()
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(SEARCH)
                        .toolExecutor(tool)
                        .toolInputGuardrails(rewrite)
                        .build()))
                .registerListener((ToolExecutedEventListener) events::add)
                .build();

        var result = assistant.chat("hello");

        assertThat(tool.receivedArguments)
                .containsExactly(REWRITTEN_ARGUMENTS, REWRITTEN_ARGUMENTS, REWRITTEN_ARGUMENTS);
        assertThat(result.toolExecutions())
                .extracting(execution -> execution.request().id())
                .containsExactly("1", "2", "3");
        assertThat(events).extracting(event -> event.request().arguments()).containsOnly(REWRITTEN_ARGUMENTS);
        assertThat(memory.messages())
                .filteredOn(AiMessage.class::isInstance)
                .map(AiMessage.class::cast)
                .filteredOn(AiMessage::hasToolExecutionRequests)
                .singleElement()
                .extracting(message -> message.toolExecutionRequests().stream()
                        .map(ToolExecutionRequest::arguments)
                        .toList())
                .isEqualTo(List.of(REWRITTEN_ARGUMENTS, REWRITTEN_ARGUMENTS, REWRITTEN_ARGUMENTS));
    }

    @Test
    void a_fatal_failure_of_a_concurrent_round_fires_the_events_of_the_tools_that_finished() {
        ToolInputGuardrail fatalForSecond =
                request -> request.toolExecutionRequest().id().equals("2")
                        ? INPUT.fatal("stop")
                        : ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS);
        var model =
                MockChatModel.thatResponds(AiMessage.from(searchCall("1"), searchCall("2")), AiMessage.from("done"));
        var events = new CopyOnWriteArrayList<ToolExecutedEvent>();

        var assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .executeToolsConcurrently()
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(SEARCH)
                        .toolExecutor(new RecordingTool(false))
                        .toolInputGuardrails(fatalForSecond)
                        .build()))
                .registerListener((ToolExecutedEventListener) events::add)
                .build();

        assertThatThrownBy(() -> assistant.chat("hello")).isInstanceOf(ToolInputGuardrailException.class);

        assertThat(events).hasSize(2);
        assertThat(events.get(0))
                .returns(false, ToolExecutedEvent::isError)
                .extracting(event -> event.request().arguments())
                .isEqualTo(REWRITTEN_ARGUMENTS);
        assertThat(events.get(1)).returns(true, ToolExecutedEvent::isError);
    }

    // ---------- return behavior ----------

    @Test
    void a_rejected_immediate_tool_does_not_return_immediately() {
        ToolInputGuardrail reject = request -> failInput("no");

        var run = run(
                AiServiceTool.builder().returnBehavior(ReturnBehavior.IMMEDIATE).toolInputGuardrails(reject),
                false,
                services -> {});

        assertThat(run.model.requests()).hasSize(2);
        assertThat(run.result.content()).isEqualTo("done");
    }

    @Test
    void a_rewritten_immediate_tool_returns_immediately_with_the_rewritten_contents() {
        ToolOutputGuardrail rewrite = request -> ToolOutputGuardrailResult.successWith("sanitized");

        var run = run(
                AiServiceTool.builder().returnBehavior(ReturnBehavior.IMMEDIATE).toolOutputGuardrails(rewrite),
                false,
                services -> {});

        assertThat(run.model.requests()).hasSize(1);
        assertThat(run.result.toolExecutions())
                .singleElement()
                .returns("sanitized", ToolExecution::result)
                .returns(null, ToolExecution::resultObject);
    }

    // ---------- tool search ----------

    @Test
    void the_built_in_tool_search_tool_is_not_guarded() {
        var calls = new ArrayList<String>();
        ToolInputGuardrail global = request -> {
            calls.add(request.toolName());
            return ToolInputGuardrailResult.success();
        };
        var strategy = new SimpleToolSearchStrategy();
        var searchTool =
                strategy.getToolSearchTools(InvocationContext.builder().build()).get(0);
        var model = MockChatModel.thatResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name(searchTool.name())
                        .arguments("{\"terms\":[\"search\"]}")
                        .build()),
                AiMessage.from(searchCall("2")),
                AiMessage.from("done"));

        var run = run(
                AiServiceTool.builder(),
                false,
                model,
                services -> services.toolSearchStrategy(strategy).toolInputGuardrails(global));

        assertThat(run.tool.receivedArguments).containsExactly(ORIGINAL_ARGUMENTS);
        assertThat(calls).containsExactly("search");
    }
}
