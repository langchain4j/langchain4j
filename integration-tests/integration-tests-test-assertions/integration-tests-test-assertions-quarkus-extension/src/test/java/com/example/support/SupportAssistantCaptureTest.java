package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static dev.langchain4j.test.service.AiServiceCapture.captureExpectingFailure;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.test.service.AiServiceCapture;
import dev.langchain4j.test.service.AiServiceInvocation;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Captures the invocations of a {@code @RegisterAiService} that the Quarkus extension creates and injects into the
 * test.
 */
@QuarkusTest
class SupportAssistantCaptureTest {

    @Inject
    SupportAssistant supportAssistant;

    @Inject
    BookingTools bookingTools;

    @Test
    void captures_the_tools_executed_by_an_injected_ai_service() {
        // Quarkus extension does not pass the providers of the application to LangChain4j yet (ISSUE-PROVIDER-LOOKUP, to be opened); flip this assert when it does, by calling captures_the_tools_executed_by_an_injected_ai_serviceOnceCaptured() directly
        ExtensionCapture.assertNotCaptured(this::captures_the_tools_executed_by_an_injected_ai_serviceOnceCaptured);
    }

    private void captures_the_tools_executed_by_an_injected_ai_serviceOnceCaptured() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("customer-1", "Please cancel MS-777")).invocation();

        assertThat(invocation).hasSucceeded().executedToolNames().containsExactly("getBookingDetails", "cancelBooking");
        assertThat(invocation.outputText()).isEqualTo("Done: Booking MS-777 cancelled");
        assertThat(invocation.invocationContext().interfaceName()).isEqualTo(SupportAssistant.class.getName());
        assertThat(invocation.invocationContext().methodName()).isEqualTo("chat");
        assertThat(invocation.invocationContext().chatMemoryId()).isEqualTo("customer-1");
        assertThat(bookingTools.isCancelled("MS-777")).isTrue();
    }

    @Test
    void tells_that_the_ai_service_does_not_report_whether_its_tools_failed() {
        // Quarkus extension does not pass the providers of the application to LangChain4j yet (ISSUE-PROVIDER-LOOKUP, to be opened); flip this assert when it does, by calling tells_that_the_ai_service_does_not_report_whether_its_tools_failedOnceCaptured() directly
        ExtensionCapture.assertNotCaptured(this::tells_that_the_ai_service_does_not_report_whether_its_tools_failedOnceCaptured);
    }

    private void tells_that_the_ai_service_does_not_report_whether_its_tools_failedOnceCaptured() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("customer-2", "Please cancel MS-778")).invocation();

        // Quarkus extension does not report ToolExecutedEvent.isError() in the pinned version (fixed on quarkus-langchain4j main: https://github.com/quarkiverse/quarkus-langchain4j/pull/3102); flip this assert when the pin moves to a release that contains it
        assertThatThrownBy(() -> assertThat(invocation).hasToolsExecutedInOrder("getBookingDetails", "cancelBooking"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("[SupportAssistant.chat]")
                .hasMessageContaining("Expected these tools to be executed successfully in this order: "
                        + "[getBookingDetails, cancelBooking], but the AI Service did not report whether its tool "
                        + "executions failed (see ToolExecutedEvent.isError()), so it is not known which ones succeeded")
                .hasMessageContaining("cancelBooking({\"bookingNumber\":\"MS-778\"}) -> Booking MS-778 cancelled "
                        + "(failure not reported)");
        // Quarkus extension does not report ToolExecutedEvent.isError() in the pinned version (fixed on quarkus-langchain4j main: https://github.com/quarkiverse/quarkus-langchain4j/pull/3102); flip this assert when the pin moves to a release that contains it
        assertThatThrownBy(() -> assertThat(invocation)
                        .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-778")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("the AI Service did not report whether its tool executions failed");
        // Quarkus extension does not report ToolExecutedEvent.isError() in the pinned version (fixed on quarkus-langchain4j main: https://github.com/quarkiverse/quarkus-langchain4j/pull/3102); flip this assert when the pin moves to a release that contains it
        assertThatThrownBy(() -> assertThat(invocation).hasNoFailedToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected no failed tool executions, but the AI Service did not report whether "
                        + "its tool executions failed");
    }

    @Test
    void captures_a_tool_that_throws_but_not_that_it_failed() {
        // Quarkus extension does not pass the providers of the application to LangChain4j yet (ISSUE-PROVIDER-LOOKUP, to be opened); flip this assert when it does, by calling captures_a_tool_that_throws_but_not_that_it_failedOnceCaptured() directly
        ExtensionCapture.assertNotCaptured(this::captures_a_tool_that_throws_but_not_that_it_failedOnceCaptured);
    }

    private void captures_a_tool_that_throws_but_not_that_it_failedOnceCaptured() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("customer-3", "Show the details XX-1")).invocation();

        assertThat(invocation).hasSucceeded().executedToolNames().containsExactly("getBookingDetails");
        assertThat(invocation.outputText()).isEqualTo("Done: Booking XX-1 not found");
        // Quarkus extension does not report ToolExecutedEvent.isError() in the pinned version (fixed on quarkus-langchain4j main: https://github.com/quarkiverse/quarkus-langchain4j/pull/3102); flip this assert when the pin moves to a release that contains it
        assertThatThrownBy(() -> assertThat(invocation).hasToolExecutionFailed("getBookingDetails"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected an execution of tool <getBookingDetails> to fail, but the AI Service "
                        + "did not report whether its tool executions failed")
                .hasMessageContaining("getBookingDetails({\"bookingNumber\":\"XX-1\"}) -> Booking XX-1 not found "
                        + "(failure not reported)");
        // Quarkus extension does not report ToolExecutedEvent.isError() in the pinned version (fixed on quarkus-langchain4j main: https://github.com/quarkiverse/quarkus-langchain4j/pull/3102); flip this assert when the pin moves to a release that contains it
        assertThatThrownBy(() -> assertThat(invocation).toolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected the tool executions, but the AI Service did not report whether its "
                        + "tool executions failed");
        // Quarkus extension does not report ToolExecutedEvent.isError() in the pinned version (fixed on quarkus-langchain4j main: https://github.com/quarkiverse/quarkus-langchain4j/pull/3102); flip this assert when the pin moves to a release that contains it
        assertThatThrownBy(invocation::toolExecutions)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("The AI Service did not report whether its tool executions failed");
    }

    @Test
    void tells_that_the_ai_service_does_not_report_the_retrieved_sources() {
        // Quarkus extension does not pass the providers of the application to LangChain4j yet (ISSUE-PROVIDER-LOOKUP, to be opened); flip this assert when it does, by calling tells_that_the_ai_service_does_not_report_the_retrieved_sourcesOnceCaptured() directly
        ExtensionCapture.assertNotCaptured(this::tells_that_the_ai_service_does_not_report_the_retrieved_sourcesOnceCaptured);
    }

    private void tells_that_the_ai_service_does_not_report_the_retrieved_sourcesOnceCaptured() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("customer-4", "What is your return policy?")).invocation();

        assertThat(invocation).hasNoToolExecutions();
        assertThat(invocation.outputText()).isEqualTo(SupportScript.POLICY_ANSWER);
        // Quarkus extension does not fire RetrievalAugmentedEvent in the pinned version (fixed on quarkus-langchain4j main: https://github.com/quarkiverse/quarkus-langchain4j/pull/3103); flip this assert when the pin moves to a release that contains it
        assertThatThrownBy(invocation::sources)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("reported no retrieved contents");
        // Quarkus extension does not fire RetrievalAugmentedEvent in the pinned version (fixed on quarkus-langchain4j main: https://github.com/quarkiverse/quarkus-langchain4j/pull/3103); flip this assert when the pin moves to a release that contains it
        assertThatThrownBy(() -> assertThat(invocation).hasRetrievedSourceContaining("within 14 days"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("[SupportAssistant.chat]")
                .hasMessageContaining("no sources were captured");
        // the retrieved contents are still visible in the request sent to the model
        assertThat(invocation.lastRequest())
                .hasUserMessageContaining("within 14 days")
                .hasUserMessageContaining("Late returns cost 30 EUR");
    }

    @Test
    void sums_the_tokens_of_all_model_calls_of_an_invocation() {
        // Quarkus extension does not pass the providers of the application to LangChain4j yet (ISSUE-PROVIDER-LOOKUP, to be opened); flip this assert when it does, by calling sums_the_tokens_of_all_model_calls_of_an_invocationOnceCaptured() directly
        ExtensionCapture.assertNotCaptured(this::sums_the_tokens_of_all_model_calls_of_an_invocationOnceCaptured);
    }

    private void sums_the_tokens_of_all_model_calls_of_an_invocationOnceCaptured() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("customer-5", "Please cancel MS-200")).invocation();

        // three model calls: two tool calls and the final answer, 20 input and 10 output tokens each
        // Quarkus extension notifies the chat model listeners only for the first call of a tool loop (ISSUE-LINK-MODEL-LISTENERS, to be opened); flip this assert when it does
        assertThat(invocation).hasOwnChatModelCallCount(1);
        // Quarkus extension notifies the chat model listeners only for the first call of a tool loop (ISSUE-LINK-MODEL-LISTENERS, to be opened); flip this assert when it does
        assertThat(invocation.tokenUsage()).isEqualTo(new TokenUsage(20, 10));
    }

    @Test
    void captures_the_requests_sent_to_the_model() {
        // Quarkus extension does not pass the providers of the application to LangChain4j yet (ISSUE-PROVIDER-LOOKUP, to be opened); flip this assert when it does, by calling captures_the_requests_sent_to_the_modelOnceCaptured() directly
        ExtensionCapture.assertNotCaptured(this::captures_the_requests_sent_to_the_modelOnceCaptured);
    }

    private void captures_the_requests_sent_to_the_modelOnceCaptured() {
        AiServiceCapture capture = capture(() -> supportAssistant.chat("customer-6", "Please cancel MS-300"));

        assertThat(capture.firstRequest())
                .hasSystemMessageContaining("Miles of Smiles")
                .hasUserMessageContaining("Please cancel MS-300")
                .hasToolSpecification("getBookingDetails")
                .hasToolSpecification("cancelBooking");
        // Quarkus extension notifies the chat model listeners only for the first call of a tool loop (ISSUE-LINK-MODEL-LISTENERS, to be opened); flip this assert when it does
        assertThat(capture.lastRequest()).isSameAs(capture.firstRequest());
        // Quarkus extension notifies the chat model listeners only for the first call of a tool loop (ISSUE-LINK-MODEL-LISTENERS, to be opened); flip this assert when it does
        assertThat(capture.lastResponse().aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(capture.outputText()).isEqualTo("Done: Booking MS-300 cancelled");
    }

    @Test
    void the_requests_contain_the_messages_of_the_chat_memory() {
        // Quarkus extension does not pass the providers of the application to LangChain4j yet (ISSUE-PROVIDER-LOOKUP, to be opened); flip this assert when it does, by calling the_requests_contain_the_messages_of_the_chat_memoryOnceCaptured() directly
        ExtensionCapture.assertNotCaptured(this::the_requests_contain_the_messages_of_the_chat_memoryOnceCaptured);
    }

    private void the_requests_contain_the_messages_of_the_chat_memoryOnceCaptured() {
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
        // Quarkus extension does not pass the providers of the application to LangChain4j yet (ISSUE-PROVIDER-LOOKUP, to be opened); flip this assert when it does, by calling captures_a_failure_of_the_modelOnceCaptured() directly
        ExtensionCapture.assertNotCaptured(() -> captureExpectingFailure(() -> supportAssistant.chat("customer-8", "boom")));
        assertThatThrownBy(() -> captureExpectingFailure(() -> supportAssistant.chat("customer-8", "boom")))
                .hasRootCauseMessage("model unavailable");
    }

    private void captures_a_failure_of_the_modelOnceCaptured() {
        AiServiceInvocation invocation =
                captureExpectingFailure(() -> supportAssistant.chat("customer-8", "boom")).invocation();

        assertThat(invocation).hasFailedWithMessageContaining("model unavailable").hasNoToolExecutions();
    }
}
