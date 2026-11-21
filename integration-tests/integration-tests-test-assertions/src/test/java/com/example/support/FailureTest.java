package com.example.support;

import static com.example.support.Bookings.bookings;
import static com.example.support.ModelScripts.toolCall;
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.LangChain4jAssertions.catchThrowable;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static dev.langchain4j.test.service.AiServiceCapture.captureExpectingFailure;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.service.AiServiceInvocation;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Failed invocations: which failures capture(...) rethrows, and how to check an expected failure.
 */
@Timeout(30)
class FailureTest {

    MockChatModel overloadedModel = MockChatModel.thatFails(new RuntimeException("Model overloaded")).thenRepeats();

    CustomerSupportAgent agent(ChatModel model) {
        return CustomerSupport.customerSupportAgent(
                model, bookings(), CustomerSupport.policyExpert(MockChatModel.thatResponds("unused")));
    }

    @Test
    void should_rethrow_failure_of_top_level_invocation() {
        CustomerSupportAgent agent = agent(overloadedModel);

        assertThatThrownBy(() -> capture(() -> agent.chat("klaus", "Hi")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Model overloaded");
    }

    @Test
    void should_rethrow_failure_even_when_the_captured_code_recovered_from_it() {
        CustomerSupportAgent agent = agent(overloadedModel);

        Throwable thrown = catchThrowable(() -> capture(() -> {
            try {
                return agent.chat("klaus", "Hi");
            } catch (RuntimeException e) {
                return "Sorry, please try again later.";
            }
        }));

        assertThat(thrown).isInstanceOf(RuntimeException.class).hasMessageContaining("Model overloaded");
    }

    @Test
    void should_return_failed_invocation_when_failure_is_expected() {
        CustomerSupportAgent agent = agent(overloadedModel);

        AiServiceInvocation invocation = captureExpectingFailure(() -> agent.chat("klaus", "Hi")).invocation();

        assertThat(invocation.error()).hasMessageContaining("Model overloaded");
        assertThat(invocation)
                .hasFailedWithMessageContaining("Model overloaded")
                .hasFailedWith(RuntimeException.class)
                // after the failure was checked, the other assertions check what happened before it
                .hasNoToolExecutions();
        assertThatThrownBy(invocation::outputText)
                .isInstanceOf(AssertionError.class)
                .hasMessage("The AI Service invocation failed, so it has no output")
                .hasRootCauseMessage("Model overloaded");
    }

    @Test
    void should_check_what_happened_before_the_failure() {
        // the model asks for a tool, then fails
        CustomerSupportAgent agent = agent(MockChatModel.thatResponds(request -> {
            if (request.messages().get(request.messages().size() - 1) instanceof ToolExecutionResultMessage) {
                throw new IllegalStateException("Connection reset");
            }
            return toolCall("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}");
        }).thenRepeats());

        AiServiceInvocation invocation =
                captureExpectingFailure(() -> agent.chat("klaus", "Show MS-777")).invocation();

        assertThat(invocation)
                .hasFailedWithMessageContaining("Connection reset")
                .hasToolExecutedWith("getBookingDetails", java.util.Map.of("bookingNumber", "MS-777"))
                .hasNoFailedToolExecutions();
    }

    @Test
    void should_fail_assertions_on_failed_invocation_until_the_failure_is_checked() {
        CustomerSupportAgent agent = agent(overloadedModel);
        AiServiceInvocation invocation = captureExpectingFailure(() -> agent.chat("klaus", "Hi")).invocation();

        assertThatThrownBy(() -> assertThat(invocation).hasNoToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected the invocation to succeed, but it failed with:")
                .hasMessageContaining("Model overloaded")
                .hasMessageContaining(
                        "To check an expected failure, call hasFailedWithMessageContaining(...) or hasFailedWith(...) first");
    }

    @Test
    void should_tell_how_the_invocation_failed_when_the_failure_does_not_match() {
        CustomerSupportAgent agent = agent(overloadedModel);
        AiServiceInvocation invocation = captureExpectingFailure(() -> agent.chat("klaus", "Hi")).invocation();

        assertThatThrownBy(() -> assertThat(invocation).hasFailedWith(IllegalArgumentException.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "Expected the invocation to fail with <java.lang.IllegalArgumentException>, but it failed with:")
                .hasMessageContaining("java.lang.RuntimeException: Model overloaded");
        assertThatThrownBy(() -> assertThat(invocation).hasFailedWithMessageContaining("timeout"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected the invocation to fail with a message containing <timeout>, but it failed with:")
                .hasMessageContaining("Model overloaded");
    }

    @Test
    void should_match_failure_by_cause() {
        CustomerSupportAgent agent = agent(MockChatModel.thatFails(new RuntimeException("Request failed", new SocketTimeoutException("Read timed out"))));

        AiServiceInvocation invocation = captureExpectingFailure(() -> agent.chat("klaus", "Hi")).invocation();

        assertThat(invocation)
                .hasFailedWith(SocketTimeoutException.class)
                .hasFailedWithMessageContaining("Read timed out");
    }

    @Test
    void should_fail_failure_assertions_on_a_successful_invocation() {
        CustomerSupportAgent agent = agent(MockChatModel.thatResponds("Hello"));
        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Hi")).invocation();

        assertThatThrownBy(() -> assertThat(invocation).hasFailedWith(RuntimeException.class))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[CustomerSupportAgent.chat] Expected the invocation to fail with <java.lang.RuntimeException>, but it did not fail");
        assertThatThrownBy(() -> assertThat(invocation).hasFailedWithMessageContaining("overloaded"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[CustomerSupportAgent.chat] Expected the invocation to fail with a message containing <overloaded>, but it did not fail");
    }

    @Test
    void capture_expecting_failure_should_fail_when_nothing_failed() {
        CustomerSupportAgent agent = agent(MockChatModel.thatResponds("Hello"));

        assertThatThrownBy(() -> captureExpectingFailure(() -> agent.chat("klaus", "Hi")))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("Expected an AI Service invocation to fail. None of the captured invocations failed: ")
                .hasMessageContaining("CustomerSupportAgent.chat");
    }

    @Test
    void capture_expecting_failure_should_fail_when_nothing_was_recorded() {
        assertThatThrownBy(() -> captureExpectingFailure(() -> "nothing"))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("Expected an AI Service invocation to fail. No AI Service invocation was captured.");
    }

    @Test
    void should_capture_invocation_stopped_by_input_guardrail() {
        CustomerSupportAgent agent = agent(overloadedModel);

        AiServiceInvocation invocation = captureExpectingFailure(
                        () -> agent.chat("klaus", "Please charge my card 4111 1111 1111 1111"))
                .invocation();

        assertThat(invocation)
                .hasFailedWith(InputGuardrailException.class)
                .hasFailedWithMessageContaining("The message contains a payment card number")
                .hasNoToolExecutions();
        // the message did not reach the model
        assertThat(overloadedModel.requests()).isEmpty();
    }

    @Test
    void should_rethrow_exception_thrown_by_the_captured_code_itself() {
        CustomerSupportAgent agent = agent(MockChatModel.thatResponds("Hello").thenRepeats());

        assertThatThrownBy(() -> capture(() -> {
                    String answer = agent.chat("klaus", "Hi");
                    throw new IllegalStateException("Unexpected answer: " + answer);
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unexpected answer: Hello");

        // also when a failure is expected: it is not the failure of an AI Service invocation
        assertThatThrownBy(() -> captureExpectingFailure(() -> {
                    agent.chat("klaus", "Hi");
                    throw new IllegalStateException("bug in the test");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bug in the test");
    }

    @Test
    void should_rethrow_assertion_error_thrown_in_a_mock_model_even_when_failure_is_expected() {
        CustomerSupportAgent agent = agent(MockChatModel.thatResponds(request -> {
            throw new AssertionError("The prompt should mention the booking number");
        }).thenRepeats());

        assertThatThrownBy(() -> capture(() -> agent.chat("klaus", "Hi")))
                .isInstanceOf(AssertionError.class)
                .hasMessage("The prompt should mention the booking number");
        assertThatThrownBy(() -> captureExpectingFailure(() -> agent.chat("klaus", "Hi")))
                .isInstanceOf(AssertionError.class)
                .hasMessage("The prompt should mention the booking number");
    }

    @Test
    void should_rethrow_failure_of_async_invocation() {
        AsyncCustomerSupportAgent agent = CustomerSupport.asyncCustomerSupportAgent(overloadedModel, bookings());

        assertThatThrownBy(() -> capture(() -> agent.chat("Hi")))
                .hasMessageContaining("Model overloaded");
    }

    @Test
    void should_rethrow_failure_of_async_invocation_handled_by_the_captured_code() {
        AsyncCustomerSupportAgent agent = CustomerSupport.asyncCustomerSupportAgent(overloadedModel, bookings());

        assertThatThrownBy(() -> capture(() -> agent.chat("Hi").exceptionally(error -> "fallback")))
                .hasMessageContaining("Model overloaded");

        AiServiceInvocation invocation = captureExpectingFailure(
                        () -> agent.chat("Hi").exceptionally(error -> "fallback"))
                .invocation();
        assertThat(invocation).hasFailedWithMessageContaining("Model overloaded");
    }

    @Test
    void should_rethrow_failure_of_returned_future_that_is_not_an_ai_service_failure() {
        AsyncCustomerSupportAgent agent =
                CustomerSupport.asyncCustomerSupportAgent(MockChatModel.thatResponds("Hello"), bookings());

        assertThatThrownBy(() -> capture(() -> agent.chat("Hi").thenCompose(answer ->
                        CompletableFuture.<String>failedFuture(new IllegalStateException("post-processing failed")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("post-processing failed");
    }

    @Test
    void should_capture_failed_structured_output() {
        BookingRequestExtractor extractor =
                CustomerSupport.bookingRequestExtractor(MockChatModel.thatResponds(AiMessage.from("I am not JSON")));

        AiServiceInvocation invocation =
                captureExpectingFailure(() -> extractor.extract("Cancel MS-777")).invocation();

        assertThat(invocation).hasFailedWithMessageContaining("I am not JSON");
    }
}
