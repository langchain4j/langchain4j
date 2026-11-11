package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static dev.langchain4j.test.service.AiServiceCapture.captureExpectingFailure;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.support.assistant.SupportAssistant;
import com.example.support.booking.BookingSummary;
import com.example.support.refund.AuditLog;
import com.example.support.refund.RefundTools;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.test.service.ChatModelCall;
import dev.langchain4j.test.service.AiServiceCapture;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Same configuration as {@link SupportAssistantCaptureTest}, so the Spring context is reused from the cache.
 */
@SpringBootTest
@Import(TestModels.class)
class ToolLoopAndStructuredOutputTest {

    @Autowired
    SupportAssistant supportAssistant;

    @Autowired
    RefundTools refundTools;

    @Autowired
    AuditLog auditLog;

    @Test
    void output_is_the_typed_object_of_a_bean_method_with_structured_output() {
        AiServiceCapture capture = capture(() -> supportAssistant.summarizeBooking("Please summarize MS-310"));

        BookingSummary summary = capture.output(BookingSummary.class);
        assertThat(summary).isEqualTo(new BookingSummary("MS-310", "compact car"));
        assertThat(capture.output()).isEqualTo(summary);
        assertThat(capture)
                .hasSucceeded()
                .hasToolExecutedTimes("getBookingDetails", 1)
                .hasOwnChatModelCallCount(2)
                .hasNoRetrievedSources();
    }

    @Test
    void output_text_fails_for_a_method_with_structured_output() {
        AiServiceCapture capture = capture(() -> supportAssistant.summarizeBooking("Please summarize MS-311"));

        assertThatThrownBy(capture::outputText)
                .isInstanceOf(AssertionError.class)
                .hasMessage("The AI Service method produced a BookingSummary, not text: use output() instead");
    }

    @Test
    void output_text_is_the_answer_of_a_string_method() {
        AiServiceCapture capture = capture(() -> supportAssistant.chat("Hi"));

        assertThat(capture.output()).isEqualTo(SupportScript.GREETING);
        assertThat(capture.outputText()).isEqualTo(SupportScript.GREETING);
    }

    @Test
    void counts_executions_of_a_tool_bean_behind_an_aop_proxy() {
        assertThat(AopUtils.isAopProxy(refundTools)).isTrue();

        AiServiceCapture capture = capture(() -> supportAssistant.chat("Please split refund MS-320 30 20"));

        assertThat(capture)
                .hasToolExecutedTimes("issueRefund", 2)
                .hasToolExecutedTimes("getBookingDetails", 1)
                .hasToolExecutedTimes("cancelBooking", 0)
                .hasToolsExecutedInOrder("getBookingDetails", "issueRefund")
                .hasToolsExecutedInOrder("getBookingDetails", "issueRefund", "issueRefund")
                .hasToolsExecutedInOrder("issueRefund", "issueRefund");
        assertThat(auditLog.entries()).contains("issueRefund[MS-320, 30]", "issueRefund[MS-320, 20]");
        assertThat(capture.outputText()).isEqualTo("Done: Refund of 20 EUR issued for booking MS-320");
    }

    @Test
    void tells_how_often_a_tool_was_executed_when_the_count_does_not_match() {
        AiServiceCapture capture = capture(() -> supportAssistant.chat("Please split refund MS-321 30 20"));

        assertThatThrownBy(() -> assertThat(capture).hasToolExecutedTimes("issueRefund", 1))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected tool <issueRefund> to be executed successfully 1 time(s), but it was 2 time(s)");
    }

    @Test
    void fails_when_the_tools_were_executed_in_another_order() {
        AiServiceCapture capture = capture(() -> supportAssistant.chat("Please split refund MS-322 30 20"));

        assertThatThrownBy(() -> assertThat(capture).hasToolsExecutedInOrder("issueRefund", "getBookingDetails"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "Expected these tools to be executed successfully in this order: [issueRefund, getBookingDetails], but they were not");
    }

    @Test
    void records_every_own_chat_model_call_of_the_tool_loop_and_each_request_offers_the_tools() {
        AiServiceCapture capture = capture(() -> supportAssistant.chat("Please split refund MS-323 30 20"));

        List<ChatModelCall> calls = capture.invocation().ownChatModelCalls();
        assertThat(calls).hasSize(4);
        assertThat(capture).hasOwnChatModelCallCount(4).hasOwnChatModelCallCountAtMost(4);
        for (ChatModelCall call : calls) {
            assertThat(call.request().toolSpecifications())
                    .extracting(ToolSpecification::name)
                    .contains("getBookingDetails", "cancelBooking", "issueRefund", "askExpert");
        }
        assertThat(calls)
                .extracting(call -> call.response().aiMessage().hasToolExecutionRequests())
                .containsExactly(true, true, true, false);
        assertThat(calls.get(3).response()).isEqualTo(capture.invocation().lastResponse());
    }

    @Test
    void fails_when_the_tool_loop_called_the_model_more_often() {
        AiServiceCapture capture = capture(() -> supportAssistant.chat("Please split refund MS-324 30 20"));

        assertThatThrownBy(() -> assertThat(capture).hasOwnChatModelCallCount(3))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected 3 call(s) of the AI Service to its chat model, but there were 4");
        assertThatThrownBy(() -> assertThat(capture).hasOwnChatModelCallCountAtMost(3))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected at most 3 call(s) of the AI Service to its chat model, but there were 4");
    }

    @Test
    void has_no_retrieved_sources_fails_when_the_knowledge_base_answered() {
        AiServiceCapture capture = capture(() -> supportAssistant.chat("What is your return policy?"));

        assertThatThrownBy(() -> assertThat(capture).hasNoRetrievedSources())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected no retrieved sources, but these were retrieved")
                .hasMessageContaining("Cars can be returned free of charge within 14 days");
    }

    @Test
    void has_succeeded_fails_for_a_failed_invocation() {
        AiServiceCapture capture = captureExpectingFailure(() -> supportAssistant.chat("boom"));

        assertThatThrownBy(() -> assertThat(capture).hasSucceeded())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected the invocation to succeed, but it failed with")
                .hasMessageContaining("model unavailable");
        assertThatThrownBy(capture::output)
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void invocation_fails_when_the_captured_code_called_the_bean_twice() {
        AiServiceCapture capture = capture(() -> supportAssistant.chat("Hi") + supportAssistant.chat("Hi again"));

        assertThat(capture.invocations()).hasSize(2);
        assertThatThrownBy(capture::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Use invocations() to choose one.");
    }
}
