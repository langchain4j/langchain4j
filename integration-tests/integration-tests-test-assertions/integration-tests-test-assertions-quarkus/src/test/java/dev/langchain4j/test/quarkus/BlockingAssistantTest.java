package dev.langchain4j.test.quarkus;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.quarkus.Models.toolCall;
import static dev.langchain4j.test.quarkus.Models.useChatModel;
import static dev.langchain4j.test.quarkus.Models.useChatModelResponding;

import com.example.support.CustomerContext;
import com.example.support.CustomerSupportAssistant;
import com.example.support.EscalationTools;
import com.example.support.OrderTools;
import com.example.support.SupportService;
import com.example.support.TicketSummarizer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BlockingAssistantTest {

    @Inject
    CustomerSupportAssistant assistant;

    @Inject
    SupportService supportService;

    @Inject
    OrderTools orderTools;

    @Inject
    EscalationTools escalationTools;

    @Inject
    CustomerContext customerContext;

    @Test
    void records_tool_executed_through_client_proxy() {
        useChatModelResponding(
                toolCall("getOrderStatus", "{\"orderNumber\":\"ORD-1\"}"),
                AiMessage.from("Your order ORD-1 has been shipped."));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> assistant.chat("Where is my order ORD-1?"))
                .invocation();

        assertThat(orderTools).isInstanceOf(ClientProxy.class);
        assertThat(invocation)
                .hasToolExecutedWith("getOrderStatus", Map.of("orderNumber", "ORD-1"))
                .hasNoFailedToolExecutions();
        assertThat(invocation.invocationContext().methodName()).isEqualTo("chat");
        assertThat(invocation.invocationContext().interfaceName()).isEqualTo(CustomerSupportAssistant.class.getName());
        assertThat(invocation.toolExecutions()).singleElement().satisfies(execution -> assertThat(
                        execution.result())
                .isEqualTo("shipped"));
        assertThat(invocation.outputText()).isEqualTo("Your order ORD-1 has been shipped.");
    }

    @Test
    void records_several_tools_in_order_of_execution() {
        useChatModelResponding(
                toolCall("getOrderStatus", "{\"orderNumber\":\"ORD-2\"}"),
                toolCall("cancelOrder", "{\"orderNumber\":\"ORD-2\"}"),
                AiMessage.from("Order ORD-2 was still processing, so I cancelled it."));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> assistant.chat("Cancel ORD-2 if it has not shipped yet"))
                .invocation();

        assertThat(invocation)
                .hasToolsExecutedExactlyInAnyOrder("cancelOrder", "getOrderStatus")
                .hasToolExecutedWith("cancelOrder", Map.of("orderNumber", "ORD-2"))
                .executedToolNames()
                .containsExactly("getOrderStatus", "cancelOrder");
        assertThat(orderTools.cancelledOrders()).contains("ORD-2");
    }

    @Test
    void records_tool_of_request_scoped_bean() {
        customerContext.setCustomerId("VIP-42");
        useChatModelResponding(toolCall("getCustomerTier", "{}"), AiMessage.from("You are a gold customer."));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> assistant.chat("What is my loyalty tier?"))
                .invocation();

        assertThat(customerContext).isInstanceOf(ClientProxy.class);
        assertThat(invocation).hasToolExecuted("getCustomerTier").hasNoFailedToolExecutions();
        assertThat(invocation.toolExecutions().get(0).result()).isEqualTo("gold");
    }

    @Test
    void reports_failed_tool_execution_without_failing_the_invocation() {
        useChatModelResponding(
                toolCall("getOrderStatus", "{\"orderNumber\":\"ORD-404\"}"),
                AiMessage.from("I could not find order ORD-404."));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> assistant.chat("Where is my order ORD-404?"))
                .invocation();

        assertThat(invocation)
                .hasToolExecutionFailed("getOrderStatus")
                .hasNoMoreToolExecutions();
        assertThatThrownBy(() -> assertThat(invocation)
                        .hasToolExecutedWith("getOrderStatus", Map.of("orderNumber", "ORD-404")))
                .hasMessageContaining("but its executions with them failed");
        assertThat(invocation.toolExecutions().get(0).hasFailed()).isTrue();
        assertThatThrownBy(() -> assertThat(invocation).hasNoFailedToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected no failed tool executions, but some failed")
                .hasMessageContaining("Order ORD-404 not found");
    }

    @Test
    void fails_when_expected_tool_was_not_executed() {
        useChatModelResponding(AiMessage.from("I cannot look that up."));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> assistant.chat("Where is my order ORD-1?"))
                .invocation();

        assertThat(invocation).hasNoToolExecutions();
        assertThatThrownBy(() -> assertThat(invocation).hasToolExecuted("getOrderStatus"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("getOrderStatus");
    }

    @Test
    void records_sources_retrieved_by_content_retriever_bean() {
        useChatModelResponding(AiMessage.from("You can return items within 30 days."));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> assistant.chat("Can I return my shoes?"))
                .invocation();

        assertThat(invocation).hasRetrievedSourceContaining("within 30 days of delivery");
        assertThat(invocation.sources()).hasSize(1);
        assertThatThrownBy(() -> assertThat(invocation).hasRetrievedSourceContaining("shipping takes"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Retrieved sources:")
                .hasMessageContaining("within 30 days of delivery");
    }

    @Test
    void records_empty_sources_when_nothing_was_retrieved() {
        useChatModelResponding(AiMessage.from("Hello!"));

        AiServiceInvocation invocation =
                AiServiceCapture.capture(() -> assistant.chat("Hi")).invocation();

        assertThat(invocation.sources()).isEmpty();
    }

    @Test
    void sums_token_usage_over_all_model_calls() {
        useChatModel(ChatModelMock.thatResponds(
                                toolCall("getOrderStatus", "{\"orderNumber\":\"ORD-1\"}"),
                                AiMessage.from("Shipped."))
                        .withTokenUsages(new TokenUsage(100, 20), new TokenUsage(150, 30))
                        .withFinishReason(FinishReason.STOP));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> assistant.chat("Where is ORD-1?"))
                .invocation();

        assertThat(invocation.tokenUsage().totalTokenCount()).isEqualTo(300);
        assertThat(invocation.tokenUsage().inputTokenCount()).isEqualTo(250);
        assertThat(invocation.tokenUsage().outputTokenCount()).isEqualTo(50);
        assertThat(invocation).hasFinishReason(FinishReason.STOP);
    }

    @Test
    void reports_no_token_usage_when_model_does_not_report_it() {
        useChatModelResponding(AiMessage.from("Hello!"));

        AiServiceInvocation invocation =
                AiServiceCapture.capture(() -> assistant.chat("Hi")).invocation();

        assertThat(invocation.tokenUsage()).isNull();
    }

    @Test
    void records_invocation_started_by_tool_as_nested_invocation() {
        useChatModelResponding(
                toolCall("escalateToHuman", "{\"issue\":\"The parcel arrived damaged\"}"),
                AiMessage.from("Customer received a damaged parcel."),
                AiMessage.from("I have handed your issue over to a colleague."));

        AiServiceCapture invocations =
                AiServiceCapture.capture(() -> assistant.chat("My parcel arrived damaged!"));

        AiServiceInvocation invocation = invocations.invocation();
        assertThat(invocation).topLevelOnly().hasToolExecuted("escalateToHuman");
        assertThat(invocation.nestedInvocations()).singleElement().satisfies(nested -> {
            assertThat(nested.invocationContext().methodName()).isEqualTo("summarize");
            assertThat(nested.invocationContext().interfaceName()).isEqualTo(TicketSummarizer.class.getName());
            assertThat(nested.outputText()).isEqualTo("Customer received a damaged parcel.");
        });
        assertThat(invocation.toolExecutions().get(0).result())
                .isEqualTo("Ticket created: Customer received a damaged parcel.");
    }

    @Test
    void records_tool_bean_called_directly_as_top_level_invocation() {
        useChatModelResponding(AiMessage.from("Customer cannot log in."));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> escalationTools.escalateToHuman("I cannot log in"))
                .invocation();

        assertThat(invocation.invocationContext().methodName()).isEqualTo("summarize");
        assertThat(invocation.nestedInvocations()).isEmpty();
    }

    @Test
    void records_invocation_made_by_application_bean() {
        useChatModelResponding(AiMessage.from("Standard shipping takes 3 to 5 business days."));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> supportService.answer("How long does shipping take?"))
                .invocation();

        assertThat(invocation).hasRetrievedSourceContaining("3 to 5 business days").hasNoToolExecutions();
        assertThat(invocation.outputText()).contains("3 to 5 business days");
    }

    @Test
    void records_several_invocations_in_order() {
        useChatModelResponding(AiMessage.from("First answer"), AiMessage.from("Second answer"));

        AiServiceCapture invocations = AiServiceCapture.capture(() -> {
            assistant.chat("First question");
            assistant.chat("Second question");
        });

        assertThat(invocations.invocations()).extracting(AiServiceInvocation::outputText).containsExactly("First answer", "Second answer");
        assertThat(invocations.invocations().get(1).outputText()).isEqualTo("Second answer");
        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected a single top-level AI Service invocation, but 2 were captured")
                .hasMessageContaining(CustomerSupportAssistant.class.getName() + ".chat");
    }

    @Test
    void rethrows_model_failure_from_record() {
        useChatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("Model is overloaded")));

        assertThatThrownBy(() -> AiServiceCapture.capture(() -> assistant.chat("Hi")))
                .hasMessageContaining("Model is overloaded");
    }

    @Test
    void returns_failed_invocation_when_failure_is_expected() {
        useChatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("Model is overloaded")));

        AiServiceInvocation invocation = AiServiceCapture.captureExpectingFailure(
                        () -> assistant.chat("Hi"))
                .invocation();

        assertThat(invocation).hasFailedWithMessageContaining("Model is overloaded").hasNoToolExecutions();
        assertThat(invocation.error()).isNotNull();
        assertThatThrownBy(invocation::outputText).isInstanceOf(AssertionError.class);
    }

    @Test
    void records_failure_after_tool_execution() {
        useChatModelResponding(
                toolCall("cancelOrder", "{\"orderNumber\":\"ORD-1\"}"),
                AiMessage.from("INTERNAL: refund manually, customer is difficult"));

        AiServiceInvocation invocation = AiServiceCapture.captureExpectingFailure(
                        () -> assistant.chat("Cancel ORD-1"))
                .invocation();

        assertThat(invocation)
                .hasFailedWith(OutputGuardrailException.class)
                .hasFailedWithMessageContaining("The answer contains internal notes")
                .hasToolExecutedWith("cancelOrder", Map.of("orderNumber", "ORD-1"));
    }

    @Test
    void has_failed_with_reports_actual_error_when_type_does_not_match() {
        useChatModelResponding(AiMessage.from("INTERNAL note"));

        AiServiceInvocation invocation = AiServiceCapture.captureExpectingFailure(
                        () -> assistant.chat("Hi"))
                .invocation();

        assertThatThrownBy(() -> assertThat(invocation).hasFailedWith(IllegalArgumentException.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(IllegalArgumentException.class.getName())
                .hasMessageContaining("The answer contains internal notes");
    }

    @Test
    void capture_expecting_failure_fails_when_nothing_failed() {
        useChatModelResponding(AiMessage.from("All good"));

        assertThatThrownBy(() -> AiServiceCapture.captureExpectingFailure(() -> assistant.chat("Hi")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected an AI Service invocation to fail")
                .hasMessageContaining("None of the captured invocations failed");
    }

    @Test
    void rethrows_assertion_error_thrown_by_captured_code() {
        useChatModelResponding(AiMessage.from("All good"));

        assertThatThrownBy(() -> AiServiceCapture.capture(() -> {
                    assistant.chat("Hi");
                    throw new AssertionError("checked inside the lambda");
                }))
                .isInstanceOf(AssertionError.class)
                .hasMessage("checked inside the lambda");
    }
}
