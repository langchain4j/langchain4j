package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.test.mock.MockChatModel;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The chat memory keeps a tool call as the LLM made it, so when an input guardrail changed its arguments, the tool
 * result the LLM gets starts with a note that says so, followed by the result of the tool.
 */
class ToolArgumentsRewriteNoteTest {

    private static final String ORIGINAL = "{\"amount\":10000}";
    private static final String CAPPED = "{\"amount\":1000}";
    private static final String NOTE =
            "<tool_arguments_changed>The arguments of this call were changed before it ran: %s</tool_arguments_changed>\n%s";

    // the result factories are default methods of the interface, which lambdas cannot call
    private static final ToolInputGuardrail INPUT = request -> null;

    private static final ToolInputGuardrail CAPPING =
            request -> ToolInputGuardrailResult.successWithArguments(Map.of("amount", 1000));

    interface Assistant {

        Result<String> chat(@UserMessage String message);
    }

    private static final ToolSpecification TRANSFER =
            ToolSpecification.builder().name("transfer").build();

    private static ToolExecutionRequest transferCall() {
        return ToolExecutionRequest.builder()
                .id("1")
                .name("transfer")
                .arguments(ORIGINAL)
                .build();
    }

    private record Run(MockChatModel model, Result<String> result, MessageWindowChatMemory memory) {

        ToolExecutionResultMessage resultMessageOfSecondRequest() {
            return model.requests().get(1).messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static Run run(ToolExecutor tool, Consumer<AiServices<Assistant>> configuration) {
        var model = MockChatModel.thatResponds(AiMessage.from(transferCall()), AiMessage.from("done"));
        var memory = MessageWindowChatMemory.withMaxMessages(20);
        var services = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(memory)
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(TRANSFER)
                        .toolExecutor(tool)
                        .build()));
        configuration.accept(services);
        return new Run(model, services.build().chat("send 10000"), memory);
    }

    private static ToolExecutor transferDone() {
        return (request, memoryId) -> "Transfer done";
    }

    @Test
    void the_llm_gets_the_note_and_the_result_while_the_memory_keeps_the_original_call() {
        var run = run(transferDone(), services -> services.toolInputGuardrails(CAPPING));

        assertThat(run.resultMessageOfSecondRequest())
                .returns(NOTE.formatted(CAPPED, "Transfer done"), ToolExecutionResultMessage::text);
        assertThat(run.memory().messages())
                .filteredOn(AiMessage.class::isInstance)
                .map(AiMessage.class::cast)
                .filteredOn(AiMessage::hasToolExecutionRequests)
                .singleElement()
                .extracting(message -> message.toolExecutionRequests().get(0).arguments())
                .isEqualTo(ORIGINAL);
        assertThat(run.result().toolExecutions())
                .singleElement()
                .returns(CAPPED, execution -> execution.request().arguments())
                .returns("Transfer done", ToolExecution::result);
    }

    @Test
    void a_call_whose_arguments_no_guardrail_changed_gets_no_note() {
        ToolInputGuardrail accepting = request -> ToolInputGuardrailResult.success();

        var run = run(transferDone(), services -> services.toolInputGuardrails(accepting));

        assertThat(run.resultMessageOfSecondRequest().text()).isEqualTo("Transfer done");
    }

    @Test
    void a_call_rejected_after_a_rewrite_gets_the_message_of_the_guardrail_verbatim() {
        ToolInputGuardrail rejecting = request -> INPUT.failure("Transfers over 500 need an approval.");

        var run = run(transferDone(), services -> services.toolInputGuardrails(CAPPING, rejecting));

        assertThat(run.resultMessageOfSecondRequest().text()).isEqualTo("Transfers over 500 need an approval.");
    }

    @Test
    void the_note_leaves_out_the_arguments_a_guardrail_added() {
        ToolInputGuardrail cappingAndAddingTheTenant = request ->
                ToolInputGuardrailResult.successWithArguments(Map.of("amount", 1000, "tenantId", "acme-secret"));

        var run = run(transferDone(), services -> services.toolInputGuardrails(cappingAndAddingTheTenant));

        assertThat(run.resultMessageOfSecondRequest().text())
                .isEqualTo(NOTE.formatted(CAPPED, "Transfer done"))
                .doesNotContain("acme-secret");
        // the tool still ran with every argument
        assertThat(run.result().toolExecutions())
                .singleElement()
                .extracting(execution -> execution.request().arguments())
                .asString()
                .contains("acme-secret");
    }

    @Test
    void a_guardrail_that_only_adds_arguments_adds_no_note() {
        ToolInputGuardrail addingTheTenant = request ->
                ToolInputGuardrailResult.successWithArguments(Map.of("amount", 10000, "tenantId", "acme-secret"));

        var run = run(transferDone(), services -> services.toolInputGuardrails(addingTheTenant));

        assertThat(run.resultMessageOfSecondRequest().text()).isEqualTo("Transfer done");
    }

    @Test
    void a_removed_argument_is_shown_as_null() {
        ToolInputGuardrail removingTheAmount = request -> ToolInputGuardrailResult.successWith("{}");

        var run = run(transferDone(), services -> services.toolInputGuardrails(removingTheAmount));

        assertThat(run.resultMessageOfSecondRequest().text())
                .isEqualTo(NOTE.formatted("{\"amount\":null}", "Transfer done"));
    }

    @Test
    void a_rewrite_that_only_changes_the_spacing_or_the_key_order_adds_no_note() {
        var model = MockChatModel.thatResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("transfer")
                        .arguments("{ \"amount\" : 10000, \"to\" : \"bob\" }")
                        .build()),
                AiMessage.from("done"));
        ToolInputGuardrail reserializing =
                request -> ToolInputGuardrailResult.successWith("{\"to\":\"bob\",\"amount\":10000}");

        AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(TRANSFER)
                        .toolExecutor(transferDone())
                        .build()))
                .toolInputGuardrails(reserializing)
                .build()
                .chat("send 10000 to bob");

        assertThat(model.requests().get(1).messages())
                .filteredOn(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .singleElement()
                .extracting(ToolExecutionResultMessage::text)
                .isEqualTo("Transfer done");
    }

    @Test
    void the_note_can_be_turned_off() {
        var run = run(transferDone(), services -> services.toolInputGuardrails(CAPPING)
                .toolInputGuardrailsConfig(ToolInputGuardrailsConfig.builder()
                        .argumentsRewriteNoteEnabled(false)
                        .build()));

        assertThat(run.resultMessageOfSecondRequest().text()).isEqualTo("Transfer done");
    }

    @Test
    void a_template_can_put_the_note_after_the_result_and_its_placeholders_are_filled_in_one_pass() {
        ToolExecutor echoingPlaceholders = (request, memoryId) -> "Transfer done {{arguments}} {{result}}";

        var run = run(echoingPlaceholders, services -> services.toolInputGuardrails(CAPPING)
                .toolInputGuardrailsConfig(ToolInputGuardrailsConfig.builder()
                        .argumentsRewriteNoteTemplate("{{result}}\n(it ran with {{arguments}})")
                        .build()));

        assertThat(run.resultMessageOfSecondRequest().text())
                .isEqualTo("Transfer done {{arguments}} {{result}}\n(it ran with " + CAPPED + ")");
    }

    @Test
    void a_result_without_text_gets_no_note_and_keeps_a_single_content() {
        ToolExecutor imageOnly = new ToolExecutor() {
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ToolExecutionResult executeWithContext(
                    ToolExecutionRequest request, InvocationContext context) {
                return ToolExecutionResult.builder()
                        .resultContents(List.of(ImageContent.from("http://example.com/receipt.png")))
                        .build();
            }
        };

        var run = run(imageOnly, services -> services.toolInputGuardrails(CAPPING));

        assertThat(run.resultMessageOfSecondRequest().contents())
                .singleElement()
                .isInstanceOf(ImageContent.class);
    }

    @Test
    void the_note_also_precedes_an_error_result_of_the_tool() {
        ToolExecutor failing = (request, memoryId) -> {
            throw new IllegalStateException("insufficient funds");
        };

        var run = run(failing, services -> services.toolInputGuardrails(CAPPING)
                .toolExecutionErrorHandler((error, context) -> ToolErrorHandlerResult.text(error.getMessage())));

        assertThat(run.resultMessageOfSecondRequest())
                .returns(true, ToolExecutionResultMessage::isError)
                .returns(NOTE.formatted(CAPPED, "insufficient funds"), ToolExecutionResultMessage::text);
    }

    public static class Bank {

        @Tool
        public String transfer(int amount) {
            return "Transfer done";
        }

        @CompensateFor("transfer")
        public void undo(ToolExecution execution) {}

        @Tool
        public String notify(String to) {
            throw new IllegalStateException("mail server down");
        }
    }

    @Test
    void the_note_stays_when_the_call_is_rolled_back() {
        var model = MockChatModel.thatResponds(
                AiMessage.from(
                        ToolExecutionRequest.builder()
                                .id("1")
                                .name("transfer")
                                .arguments("{\"arg0\":10000}")
                                .build(),
                        ToolExecutionRequest.builder()
                                .id("2")
                                .name("notify")
                                .arguments("{\"arg0\":\"me\"}")
                                .build()),
                AiMessage.from("done"));
        ToolInputGuardrail capping = request -> request.toolName().equals("transfer")
                ? ToolInputGuardrailResult.successWithArguments(Map.of("arg0", 1000))
                : ToolInputGuardrailResult.success();

        AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(new Bank())
                .compensateOnToolErrors(true)
                .toolExecutionErrorHandler((error, context) -> ToolErrorHandlerResult.text(error.getMessage()))
                .toolInputGuardrails(capping)
                .build()
                .chat("send 10000 and tell me");

        assertThat(model.requests().get(1).messages())
                .filteredOn(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .filteredOn(message -> message.toolName().equals("transfer"))
                .singleElement()
                .extracting(ToolExecutionResultMessage::text)
                .asString()
                .startsWith("<tool_arguments_changed>The arguments of this call were changed before it ran: "
                        + "{\"arg0\":1000}</tool_arguments_changed>\n")
                .contains("was rolled back");
    }
}
