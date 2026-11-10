package com.example.support;

import static com.example.support.Bookings.bookings;
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.service.AiServiceCapture.capture;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Which invocations are captured, and single() / last() / all().
 */
@Timeout(30)
class CapturedInvocationsTest {

    ExecutorService executor = Executors.newSingleThreadExecutor();

    ChatModelMock model = ChatModelMock.thatResponds(
            request -> AiMessage.from("You said: " + ModelScripts.lastUserMessage(request)));

    CustomerSupportAgent agent = CustomerSupport.customerSupportAgent(
            model, bookings(), CustomerSupport.policyExpert(ChatModelMock.thatResponds("unused")));

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void should_capture_several_invocations_in_the_order_they_started() {
        AiServiceCapture invocations = capture(() -> {
            agent.chat("klaus", "Hello");
            agent.chat("klaus", "I want to cancel MS-777");
        });

        assertThat(invocations.invocations())
                .extracting(AiServiceInvocation::outputText)
                .containsExactly("You said: Hello", "You said: I want to cancel MS-777");
        assertThat(invocations.invocations().get(1).outputText()).isEqualTo("You said: I want to cancel MS-777");
        assertThat(invocations.invocations().get(0).invocationContext().invocationId())
                .isNotEqualTo(invocations.invocations().get(1).invocationContext().invocationId());

        // the second invocation saw the first messages in the chat memory
        List<ChatMessage> secondRequest = model.requests().get(1).messages();
        assertThat(secondRequest)
                .filteredOn(UserMessage.class::isInstance)
                .extracting(message -> ((UserMessage) message).singleText())
                .containsExactly("Hello", "I want to cancel MS-777");
    }

    @Test
    void single_should_fail_with_helpful_message_when_several_invocations_were_recorded() {
        AiServiceCapture invocations = capture(() -> {
            agent.chat("klaus", "Hello");
            agent.chat("sylvie", "Bonjour");
        });

        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessage("Expected a single top-level AI Service invocation, but 2 were captured: "
                        + "com.example.support.CustomerSupportAgent.chat, com.example.support.CustomerSupportAgent.chat. "
                        + "Use invocations() to choose one.");
    }

    @Test
    void should_capture_nothing_when_no_ai_service_is_called() {
        AiServiceCapture invocations = capture(() -> "no AI here");

        assertThat(invocations.invocations()).isEmpty();
        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("No AI Service invocation was captured. ")
                .hasMessageContaining("Invocations started on other threads are not captured");
        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("No AI Service invocation was captured. ");
    }

    @Test
    void should_explain_why_nothing_was_captured_when_the_ai_service_runs_on_another_thread() {
        AiServiceCapture invocations =
                capture(() -> CompletableFuture.supplyAsync(() -> agent.chat("klaus", "Hello"), executor)
                        .join());

        assertThat(invocations.invocations()).isEmpty();
        assertThatThrownBy(invocations::invocation)
                .hasMessageContaining("No AI Service invocation was captured")
                .hasMessageContaining("an executor");
    }

    @Test
    void should_allow_checked_exceptions_in_captured_code() throws Exception {
        // Future.get() throws checked exceptions: no need to wrap them
        AiServiceCapture invocations = capture(() -> executor.submit(() -> agent.chat("klaus", "Hello")).get());

        assertThat(invocations.invocations()).isEmpty();
    }

    @Test
    void should_not_capture_invocations_made_before_or_after_the_captured_code() {
        agent.chat("klaus", "Before");

        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "During")).invocation();

        agent.chat("klaus", "After");
        assertThat(invocation.outputText()).isEqualTo("You said: During");
    }

    @Test
    void should_not_capture_invocations_of_a_previous_capture() {
        capture(() -> agent.chat("klaus", "First"));

        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Second")).invocation();

        assertThat(invocation.outputText()).isEqualTo("You said: Second");
    }

    @Test
    void should_capture_nested_recordings_separately() {
        AiServiceCapture outer = capture(() -> {
            agent.chat("klaus", "Outer");
            AiServiceInvocation inner = capture(() -> agent.chat("klaus", "Inner")).invocation();
            assertThat(inner.outputText()).isEqualTo("You said: Inner");
        });

        // the invocation captured by the inner capture is not seen by the outer one
        assertThat(outer.outputText()).isEqualTo("You said: Outer");
    }

    @Test
    void should_describe_invocations() {
        AiServiceCapture invocations = capture(() -> agent.chat("klaus", "Hello"));

        assertThat(invocations.toString())
                .contains("method=com.example.support.CustomerSupportAgent.chat")
                .contains("toolExecutions=0")
                .contains("error=null")
                .contains("cancelled=false");
    }
}
