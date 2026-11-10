package com.example.support;

import static com.example.support.Bookings.bookings;
import static com.example.support.ModelScripts.callToolsThenAnswer;
import static com.example.support.ModelScripts.toolCall;
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatCode;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.LangChain4jAssertions.catchThrowableOfType;
import static dev.langchain4j.test.LangChain4jAssertions.entry;
import static dev.langchain4j.test.LangChain4jAssertions.within;
import static dev.langchain4j.test.service.AiServiceCapture.capture;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.test.service.AiServiceCapture;
import dev.langchain4j.test.service.AiServiceInvocation;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * A single static import of LangChain4jAssertions gives the LangChain4j assertions and all AssertJ assertions.
 */
@Timeout(30)
class LangChain4jAssertionsTest {

    BookingRepository bookings = bookings();

    CustomerSupportAgent agent = CustomerSupport.customerSupportAgent(
            ChatModelMock.thatResponds(callToolsThenAnswer(
                    results -> "Cancelled, you get " + results.get(1) + " EUR back.",
                    toolCall("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"),
                    toolCall("calculateRefund", "{\"bookingNumber\":\"MS-777\",\"days\":2}"))),
            bookings,
            CustomerSupport.policyExpert(ChatModelMock.thatResponds("unused")));

    @Test
    void should_mix_langchain4j_and_assertj_assertions() {
        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Cancel MS-777")).invocation();

        // LangChain4j assertions
        assertThat(invocation).hasToolExecuted("cancelBooking").hasNoFailedToolExecutions();
        // AssertJ assertions, from the same import
        assertThat(invocation.outputText()).startsWith("Cancelled").contains("99.0 EUR");
        assertThat(invocation.toolExecutions()).hasSize(2);
        assertThat(invocation.tokenUsage()).isNull();
        assertThat(Double.parseDouble(invocation.toolExecutions().get(1).result())).isCloseTo(99, within(0.01));
        assertThat(Map.of("bookingNumber", "MS-777")).contains(entry("bookingNumber", "MS-777"));
        assertThat(bookings.find("MS-777").cancelled()).isTrue();
        assertThatCode(() -> bookings.find("MS-888")).doesNotThrowAnyException();
        BookingNotFoundException notFound =
                catchThrowableOfType(BookingNotFoundException.class, () -> bookings.find("MS-999"));
        assertThat(notFound).hasMessage("Booking MS-999 not found");
    }

    @Test
    void should_assert_on_result_directly() {
        AiServiceCapture result = capture(() -> agent.chatWithDetails("klaus", "Cancel MS-777"));

        assertThat(result).hasToolsExecutedExactlyInAnyOrder("calculateRefund", "cancelBooking");
        assertThat(result.outputText()).contains("99.0");
    }

    @Test
    void should_navigate_to_tool_executions() {
        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Cancel MS-777")).invocation();

        assertThat(invocation)
                .toolExecutions()
                .filteredOn(execution -> execution.request().name().equals("calculateRefund"))
                .singleElement()
                .extracting(ToolExecution::result)
                .isEqualTo("99.0");
    }

    @Test
    void should_assert_on_guardrail_results() {
        assertThat(new NoCardNumbersGuardrail().validate(UserMessage.from("4111111111111111")))
                .hasFailures()
                .hasSingleFailureWithMessage("The message contains a payment card number");
        assertThat(new ProfessionalToneGuardrail().validate(AiMessage.from("Hello"))).isSuccessful();
    }

    @Test
    void should_support_descriptions_and_soft_assertions() {
        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Cancel MS-777")).invocation();

        assertThatThrownBy(() -> assertThat(invocation).as("cancellation of MS-777").hasToolExecuted("sendEmail"))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("[cancellation of MS-777] ");

        SoftAssertions softly = new SoftAssertions();
        softly.check(() -> assertThat(invocation).hasToolExecuted("sendEmail"));
        softly.check(() -> assertThat(invocation).hasToolExecuted("notifyManager"));
        softly.assertThat(invocation.outputText()).contains("Cancelled");
        assertThatThrownBy(softly::assertAll)
                .hasMessageContaining("Multiple Failures (2 failures)")
                .hasMessageContaining("<sendEmail>")
                .hasMessageContaining("<notifyManager>");
    }

    @Test
    void should_fail_on_null_invocation() {
        assertThatThrownBy(() -> assertThat((AiServiceInvocation) null).hasNoToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expecting actual not to be null");
    }
}
