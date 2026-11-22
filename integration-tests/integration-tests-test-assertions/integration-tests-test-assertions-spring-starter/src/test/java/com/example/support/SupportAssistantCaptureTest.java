package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static dev.langchain4j.test.service.AiServiceCapture.captureExpectingFailure;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.test.service.AiServiceCapture;
import dev.langchain4j.test.service.AiServiceInvocation;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Captures the invocations of an {@code @AiService} that the Spring Boot starter creates and injects into the test.
 */
@SpringBootTest
@Import(TestModels.class)
class SupportAssistantCaptureTest {

    @Autowired
    SupportAssistant supportAssistant;

    @Autowired
    BookingTools bookingTools;

    @Test
    void captures_the_tools_executed_by_an_injected_ai_service() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("customer-1", "Please cancel MS-777")).invocation();

        assertThat(invocation)
                .hasSucceeded()
                .hasToolsExecutedInOrder("getBookingDetails", "cancelBooking")
                .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"))
                .hasNoFailedToolExecutions()
                .hasNoMoreToolExecutions();
        assertThat(invocation.outputText()).isEqualTo("Done: Booking MS-777 cancelled");
        assertThat(invocation.invocationContext().interfaceName()).isEqualTo(SupportAssistant.class.getName());
        assertThat(invocation.invocationContext().methodName()).isEqualTo("chat");
        assertThat(invocation.invocationContext().chatMemoryId()).isEqualTo("customer-1");
        assertThat(bookingTools.isCancelled("MS-777")).isTrue();
    }

    @Test
    void reports_a_tool_that_throws_as_a_failed_tool_execution() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("customer-2", "Show the details XX-1")).invocation();

        assertThat(invocation)
                .hasSucceeded()
                .hasToolExecutionFailed("getBookingDetails")
                .hasNoMoreToolExecutions();
        assertThat(invocation.outputText()).isEqualTo("Done: Booking XX-1 not found");
        assertThatThrownBy(() -> assertThat(invocation).hasNoFailedToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("[SupportAssistant.chat]")
                .hasMessageContaining("Expected no failed tool executions, but some failed")
                .hasMessageContaining("getBookingDetails")
                .hasMessageContaining("Booking XX-1 not found");
        assertThatThrownBy(() -> assertThat(invocation).hasToolExecuted("getBookingDetails"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected tool <getBookingDetails> to be executed successfully, "
                        + "but its 1 execution(s) failed");
        assertThat(invocation)
                .toolExecutions()
                .singleElement()
                .satisfies(execution -> assertThat(execution.hasFailed()).isTrue());
    }

    @Test
    void captures_the_retrieved_sources() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("customer-3", "What is your return policy?")).invocation();

        assertThat(invocation)
                .hasNoToolExecutions()
                .hasRetrievedSourceContaining("within 14 days")
                .hasRetrievedSourceContaining("30 EUR per started day");
        assertThat(invocation.sources()).hasSize(2);
        assertThat(invocation.lastRequest()).hasUserMessageContaining("Late returns cost 30 EUR");
        assertThat(invocation.outputText()).isEqualTo(SupportScript.POLICY_ANSWER);
    }

    @Test
    void tells_that_nothing_was_retrieved() {
        AiServiceInvocation invocation = capture(() -> supportAssistant.chat("customer-4", "Hi")).invocation();

        assertThat(invocation).hasNoRetrievedSources();
        assertThatThrownBy(() -> assertThat(invocation).hasRetrievedSourceContaining("14 days"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("No sources were retrieved");
    }

    @Test
    void sums_the_tokens_of_all_model_calls_of_an_invocation() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("customer-5", "Please cancel MS-200")).invocation();

        // three model calls: two tool calls and the final answer, 20 input and 10 output tokens each
        assertThat(invocation)
                .hasOwnChatModelCallCount(3)
                .hasFinishReason(FinishReason.STOP);
        assertThat(invocation.tokenUsage()).isEqualTo(new TokenUsage(60, 30));
    }

    @Test
    void captures_the_requests_sent_to_the_model() {
        AiServiceCapture capture = capture(() -> supportAssistant.chat("customer-6", "Please cancel MS-300"));

        assertThat(capture.firstRequest())
                .hasSystemMessageContaining("Miles of Smiles")
                .hasUserMessageContaining("Please cancel MS-300")
                .hasToolSpecification("getBookingDetails")
                .hasToolSpecification("cancelBooking");
        assertThat(capture.lastRequest().messages()).hasSizeGreaterThan(capture.firstRequest().messages().size());
        assertThat(capture.lastResponse().aiMessage().text()).isEqualTo("Done: Booking MS-300 cancelled");
    }

    @Test
    void the_requests_contain_the_messages_of_the_chat_memory() {
        supportAssistant.chat("customer-7", "Hi, My name is Klaus");

        AiServiceCapture capture = capture(() -> supportAssistant.chat("customer-7", "What is my name?"));

        assertThat(capture.outputText()).isEqualTo("Your name is Klaus.");
        assertThat(capture.lastRequest())
                .hasUserMessageContaining("My name is Klaus")
                .hasUserMessageContaining("What is my name?");
        assertThat(capture.invocations()).hasSize(1);
    }

    @Test
    void captures_a_failure_of_the_model() {
        AiServiceInvocation invocation =
                captureExpectingFailure(() -> supportAssistant.chat("customer-8", "boom")).invocation();

        assertThat(invocation).hasFailedWithMessageContaining("model unavailable").hasNoToolExecutions();
    }
}
