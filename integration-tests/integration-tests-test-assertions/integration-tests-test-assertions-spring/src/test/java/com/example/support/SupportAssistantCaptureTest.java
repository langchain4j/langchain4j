package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static dev.langchain4j.test.service.AiServiceCapture.captureExpectingFailure;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.support.assistant.SupportAssistant;
import com.example.support.booking.BookingNotFoundException;
import com.example.support.booking.BookingTools;
import com.example.support.refund.AuditLog;
import com.example.support.refund.RefundTools;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestModels.class)
class SupportAssistantCaptureTest {

    @Autowired
    SupportAssistant supportAssistant;

    @Autowired
    BookingTools bookingTools;

    @Autowired
    RefundTools refundTools;

    @Autowired
    AuditLog auditLog;

    @Test
    void records_tools_executed_by_an_injected_ai_service() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("Please cancel MS-777")).invocation();

        assertThat(invocation)
                .hasToolsExecutedExactlyInAnyOrder("getBookingDetails", "cancelBooking")
                .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"))
                .hasNoFailedToolExecutions();
        assertThat(invocation).executedToolNames().containsExactly("getBookingDetails", "cancelBooking");
        assertThat(invocation.outputText()).isEqualTo("Done: Booking MS-777 cancelled");
        assertThat(invocation.invocationContext().interfaceName()).isEqualTo(SupportAssistant.class.getName());
        assertThat(invocation.invocationContext().methodName()).isEqualTo("chat");
        assertThat(bookingTools.isCancelled("MS-777")).isTrue();
    }

    @Test
    void records_tools_of_a_bean_behind_an_aop_proxy() {
        assertThat(AopUtils.isCglibProxy(refundTools)).isTrue();

        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("Please refund MS-100 50")).invocation();

        assertThat(invocation)
                .hasToolExecutedWith("issueRefund", Map.of("bookingNumber", "MS-100", "amountInEuros", 50))
                .hasNoFailedToolExecutions();
        assertThat(invocation.outputText()).isEqualTo("Done: Refund of 50 EUR issued for booking MS-100");
        assertThat(auditLog.entries()).contains("issueRefund[MS-100, 50]");
    }

    @Test
    void tells_which_arguments_a_tool_got_when_they_do_not_match() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("Please refund MS-101 50")).invocation();

        assertThatThrownBy(() -> assertThat(invocation)
                        .hasToolExecutedWith("issueRefund", Map.of("bookingNumber", "MS-101", "amountInEuros", 70)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("issueRefund")
                .hasMessageContaining("50");
    }

    @Test
    void reports_failed_tool_executions_without_failing_the_invocation() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("Please cancel XX-1")).invocation();

        assertThat(invocation).executedToolNames().containsExactlyInAnyOrder("getBookingDetails", "cancelBooking");
        assertThat(invocation)
                .hasToolExecutionFailed("getBookingDetails")
                .hasToolExecutionFailed("cancelBooking")
                .hasNoMoreToolExecutions();
        assertThat(invocation.error()).isNull();
        assertThatThrownBy(() -> assertThat(invocation).hasNoFailedToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("getBookingDetails")
                .hasMessageContaining("Booking XX-1 not found");
        assertThat(invocation.toolExecutions())
                .allSatisfy(execution -> assertThat(execution.hasFailed()).isTrue());
    }

    @Test
    void records_retrieved_sources() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("What is your return policy?")).invocation();

        assertThat(invocation)
                .hasNoToolExecutions()
                .hasRetrievedSourceContaining("within 14 days")
                .hasRetrievedSourceContaining("30 EUR per started day");
        assertThat(invocation.sources()).hasSize(2);
        assertThat(invocation.outputText()).isEqualTo(SupportScript.POLICY_ANSWER);
    }

    @Test
    void tells_when_nothing_was_retrieved() {
        AiServiceInvocation invocation = capture(() -> supportAssistant.chat("Hi")).invocation();

        assertThat(invocation.sources()).isEmpty();
        assertThatThrownBy(() -> assertThat(invocation).hasRetrievedSourceContaining("14 days"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("No sources were retrieved");
    }

    @Test
    void records_the_invocation_of_another_ai_service_bean_started_by_a_tool_as_nested() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("expert Can I take the car to Norway?")).invocation();

        assertThat(invocation)
                .topLevelOnly()
                .hasToolExecutedWith("askExpert", Map.of("question", "Can I take the car to Norway?"))
                .hasNoMoreToolExecutions();
        assertThat(invocation.nestedInvocations()).hasSize(1);
        AiServiceInvocation expert = invocation.nestedInvocations().get(0);
        assertThat(expert.invocationContext().methodName()).isEqualTo("answer");
        assertThat(expert.invocationContext().parentInvocationId()).isEqualTo(invocation.invocationContext().invocationId());
        assertThat(expert.outputText()).startsWith(SupportScript.EXPERT_PREFIX);
        assertThat(expert).hasNoToolExecutions();
        assertThat(expert.tokenUsage().totalTokenCount()).isLessThanOrEqualTo(30);
        assertThat(invocation.outputText()).isEqualTo("Done: " + expert.outputText());
    }

    @Test
    void sums_the_tokens_of_all_model_calls_of_an_invocation() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("Please cancel MS-200")).invocation();

        // three model calls: two tool calls and the final answer, 20 input and 10 output tokens each
        assertThat(invocation.tokenUsage().inputTokenCount()).isEqualTo(60);
        assertThat(invocation.tokenUsage().outputTokenCount()).isEqualTo(30);
        assertThat(invocation).hasFinishReason(FinishReason.STOP);
    }

    @Test
    void records_a_failure_rejected_by_an_output_guardrail() {
        AiServiceInvocation invocation =
                captureExpectingFailure(() -> supportAssistant.chat("leak something")).invocation();

        assertThat(invocation)
                .hasFailedWith(OutputGuardrailException.class)
                .hasFailedWithMessageContaining("The response leaks internal notes")
                .hasNoToolExecutions();
    }

    @Test
    void capture_rethrows_the_failure_of_an_invocation_that_is_not_expected_to_fail() {
        assertThatThrownBy(() -> capture(() -> supportAssistant.chat("leak something")))
                .isInstanceOf(OutputGuardrailException.class)
                .hasMessageContaining("The response leaks internal notes");
    }

    @Test
    void records_a_failure_of_the_model() {
        AiServiceInvocation invocation =
                captureExpectingFailure(() -> supportAssistant.chat("boom")).invocation();

        assertThat(invocation).hasFailedWithMessageContaining("model unavailable");
        assertThatThrownBy(invocation::outputText).isInstanceOf(AssertionError.class);
    }

    @Test
    void assertions_on_a_failed_invocation_fail_until_the_failure_is_checked() {
        AiServiceInvocation invocation =
                captureExpectingFailure(() -> supportAssistant.chat("boom")).invocation();

        assertThatThrownBy(() -> assertThat(invocation).hasNoToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("model unavailable");
    }

    @Test
    void capture_expecting_failure_fails_when_nothing_failed() {
        assertThatThrownBy(() -> captureExpectingFailure(() -> supportAssistant.chat("Hi")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected an AI Service invocation to fail")
                .hasMessageContaining("None of the captured invocations failed");
    }

    @Test
    void has_failed_with_names_the_actual_error_when_the_type_does_not_match() {
        AiServiceInvocation invocation =
                captureExpectingFailure(() -> supportAssistant.chat("leak something")).invocation();

        assertThatThrownBy(() -> assertThat(invocation).hasFailedWith(BookingNotFoundException.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(BookingNotFoundException.class.getName())
                .hasMessageContaining("The response leaks internal notes");
    }

    @Test
    void records_several_invocations_in_order() {
        AiServiceCapture invocations = capture(() -> {
            supportAssistant.chat("Hi");
            return supportAssistant.chat("Please cancel MS-300");
        });

        assertThat(invocations.invocations()).hasSize(2);
        assertThat(invocations.invocations().get(0)).hasNoToolExecutions();
        assertThat(invocations.invocations().get(1)).hasToolExecuted("cancelBooking");
        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected a single top-level AI Service invocation, but 2 were captured")
                .hasMessageContaining(SupportAssistant.class.getName() + ".chat");
    }

    @Test
    void checks_a_result_returned_by_the_ai_service() {
        AiServiceCapture result = capture(() -> supportAssistant.chatWithDetails("Please cancel MS-400"));

        assertThat(result)
                .hasToolsExecutedExactlyInAnyOrder("getBookingDetails", "cancelBooking")
                .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-400"));
        assertThat(result.invocation().tokenUsage().totalTokenCount()).isLessThanOrEqualTo(90);
    }

    @Test
    void records_a_method_returning_a_result() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chatWithDetails("What is your return policy?")).invocation();

        assertThat(invocation).hasRetrievedSourceContaining("14 days");
        assertThat(invocation.outputText()).isEqualTo(SupportScript.POLICY_ANSWER);
    }

    @Test
    void does_not_capture_invocations_made_outside_of_record() {
        supportAssistant.chat("Please cancel MS-500");

        AiServiceCapture invocations = capture(() -> "no AI Service called here");

        assertThat(invocations.invocations()).isEmpty();
        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("No AI Service invocation was captured.");
    }
}
