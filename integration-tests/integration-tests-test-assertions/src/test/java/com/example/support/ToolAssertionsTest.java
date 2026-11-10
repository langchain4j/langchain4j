package com.example.support;

import static com.example.support.Bookings.bookings;
import static com.example.support.ModelScripts.callToolsThenAnswer;
import static com.example.support.ModelScripts.toolCall;
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.service.AiServiceCapture.capture;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.test.service.AiServiceInvocation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Assertions on the tools executed by an AI Service.
 */
@Timeout(30)
class ToolAssertionsTest {

    BookingRepository bookings = bookings();

    CustomerSupportAgent agent(ChatModel model) {
        return CustomerSupport.customerSupportAgent(
                model, bookings, CustomerSupport.policyExpert(ChatModelMock.thatAlwaysResponds("unused")));
    }

    AiServiceInvocation cancelMs777() {
        CustomerSupportAgent agent = agent(ChatModelMock.thatResponds(callToolsThenAnswer(
                "Your booking MS-777 is cancelled, you get 148.5 EUR back.",
                toolCall("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"),
                toolCall("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"),
                toolCall("calculateRefund", "{\"bookingNumber\":\"MS-777\",\"days\":3}"))));
        return capture(() -> agent.chat("klaus", "Please cancel MS-777")).invocation();
    }

    @Test
    void should_check_executed_tools_and_their_arguments() {
        AiServiceInvocation invocation = cancelMs777();

        assertThat(invocation)
                .hasToolExecuted("cancelBooking")
                .hasToolsExecutedExactlyInAnyOrder("cancelBooking", "calculateRefund", "getBookingDetails")
                .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"))
                .hasNoFailedToolExecutions();
        assertThat(invocation)
                .executedToolNames()
                .containsExactly("getBookingDetails", "cancelBooking", "calculateRefund");
        assertThat(invocation)
                .toolExecutions()
                .extracting(ToolExecution::result)
                .contains("Booking MS-777 cancelled", "148.5");
        assertThat(bookings.find("MS-777").cancelled()).isTrue();
    }

    @Test
    void should_compare_tool_arguments_as_json() {
        AiServiceInvocation invocation = cancelMs777();

        // 3 matches "days": 3 as well as 3.0, and other arguments are ignored
        assertThat(invocation)
                .hasToolExecutedWith("calculateRefund", Map.of("days", 3))
                .hasToolExecutedWith("calculateRefund", Map.of("days", 3.0))
                .hasToolExecutedWith("calculateRefund", Map.of("bookingNumber", "MS-777", "days", 3L));
    }

    @Test
    void should_list_executed_tools_when_arguments_do_not_match() {
        AiServiceInvocation invocation = cancelMs777();

        assertThatThrownBy(() -> assertThat(invocation).hasToolExecutedWith("calculateRefund", Map.of("days", 4)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "Expected tool <calculateRefund> to be executed successfully with arguments containing {\"days\":4}, but it was not.")
                .hasMessageContaining("Executed tools:")
                .hasMessageContaining("1. getBookingDetails({\"bookingNumber\":\"MS-777\"}) -> ")
                .hasMessageContaining("2. cancelBooking({\"bookingNumber\":\"MS-777\"}) -> Booking MS-777 cancelled")
                .hasMessageContaining("3. calculateRefund({\"bookingNumber\":\"MS-777\",\"days\":3}) -> 148.5");
    }

    @Test
    void should_tell_which_tools_are_missing_and_which_were_not_expected() {
        AiServiceInvocation invocation = cancelMs777();

        assertThatThrownBy(() -> assertThat(invocation)
                        .hasToolsExecutedExactlyInAnyOrder("getBookingDetails", "cancelBooking", "sendEmail"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "Expected exactly these successful tool executions (in any order): [getBookingDetails, cancelBooking, sendEmail]")
                .hasMessageContaining("but these were missing: [sendEmail]")
                .hasMessageContaining("and these were not expected: [calculateRefund]");
    }

    @Test
    void should_count_repeated_tool_executions() {
        CustomerSupportAgent agent = agent(ChatModelMock.thatResponds(callToolsThenAnswer(
                "Both bookings are confirmed.",
                toolCall("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"),
                toolCall("getBookingDetails", "{\"bookingNumber\":\"MS-888\"}"))));

        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Check MS-777 and MS-888")).invocation();

        assertThat(invocation)
                .hasToolsExecutedExactlyInAnyOrder("getBookingDetails", "getBookingDetails")
                .hasToolExecutedWith("getBookingDetails", Map.of("bookingNumber", "MS-888"));
        assertThatThrownBy(() -> assertThat(invocation).hasToolsExecutedExactlyInAnyOrder("getBookingDetails"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("and these were not expected: [getBookingDetails]");
    }

    @Test
    void should_fail_when_a_tool_was_not_executed() {
        AiServiceInvocation invocation = cancelMs777();

        assertThatThrownBy(() -> assertThat(invocation).hasToolExecuted("sendEmail"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected tool <sendEmail> to be executed successfully, but it was not executed.")
                .hasMessageContaining("Executed tools:");
    }

    @Test
    void should_check_that_no_tool_was_executed() {
        CustomerSupportAgent agent = agent(ChatModelMock.thatAlwaysResponds("Hello, how can I help?"));

        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Hi")).invocation();

        assertThat(invocation).hasNoToolExecutions().hasNoFailedToolExecutions();
        assertThat(invocation.toolExecutions()).isEmpty();
        assertThatThrownBy(() -> assertThat(invocation).hasToolExecuted("cancelBooking"))
                .hasMessageContaining("No tools were executed.");

        AiServiceInvocation cancellation = cancelMs777();
        assertThatThrownBy(() -> assertThat(cancellation).hasNoToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected no tools to be executed, but these were executed:")
                .hasMessageContaining("cancelBooking");
    }

    @Test
    void should_report_failed_tool_execution_without_failing_the_invocation() {
        CustomerSupportAgent agent = agent(ChatModelMock.thatResponds(callToolsThenAnswer(
                "I could not find booking MS-999.", toolCall("getBookingDetails", "{\"bookingNumber\":\"MS-999\"}"))));

        // the tool failure is reported to the model, so capture(...) does not throw
        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Show MS-999")).invocation();

        assertThat(invocation.outputText()).isEqualTo("I could not find booking MS-999.");
        assertThat(invocation).hasToolExecutionFailed("getBookingDetails");
        assertThatThrownBy(() -> assertThat(invocation).hasToolExecuted("getBookingDetails"))
                .hasMessageContaining("Expected tool <getBookingDetails> to be executed successfully, but its 1 execution(s) failed.");
        assertThat(invocation.toolExecutions()).singleElement().satisfies(execution -> {
            assertThat(execution.hasFailed()).isTrue();
            assertThat(execution.result()).isEqualTo("Booking MS-999 not found");
        });
        assertThatThrownBy(() -> assertThat(invocation).hasNoFailedToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected no failed tool executions, but some failed.")
                .hasMessageContaining("1. getBookingDetails({\"bookingNumber\":\"MS-999\"}) -> Booking MS-999 not found (FAILED)");
    }

    interface Concierge {

        String chat(String message);
    }

    static class StrictTools {

        @Tool("Books a table")
        String bookTable(@P(name = "guests", description = "The number of guests") int guests) {
            // an assertion inside a tool, as a test might do
            assertThat(guests).as("guests").isLessThanOrEqualTo(8);
            return "Table booked for " + guests;
        }
    }

    @Test
    void should_report_assertion_error_thrown_by_tool_as_failed_tool_execution() {
        Concierge concierge = AiServices.builder(Concierge.class)
                .chatModel(ChatModelMock.thatResponds(
                        callToolsThenAnswer("Sorry, that is too many guests.", toolCall("bookTable", "{\"guests\":12}"))))
                .tools(new StrictTools())
                .build();

        AiServiceInvocation invocation = capture(() -> concierge.chat("A table for 12 please")).invocation();

        assertThatThrownBy(() -> assertThat(invocation).hasNoFailedToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("bookTable({\"guests\":12}) -> ")
                .hasMessageContaining("[guests]")
                .hasMessageContaining("(FAILED)");
    }

    static class EchoTools {

        @Tool("Echoes a text")
        String echo(@P(name = "text", description = "The text") String text) {
            return text;
        }
    }

    @Test
    void should_truncate_long_tool_results_in_failure_messages() {
        String longAnswer = "x".repeat(1000);
        Concierge concierge = AiServices.builder(Concierge.class)
                .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer(
                        "ok", toolCall("echo", "{\"text\":\"" + longAnswer + "\"}"))))
                .tools(new EchoTools())
                .build();

        AiServiceInvocation invocation = capture(() -> concierge.chat("Echo")).invocation();

        assertThatThrownBy(() -> assertThat(invocation).hasToolExecuted("other"))
                .hasMessageContaining("x".repeat(300) + "...")
                .message()
                .doesNotContain("x".repeat(301));
    }

    @Test
    void should_check_a_tool_of_another_ai_service() {
        Concierge concierge = AiServices.builder(Concierge.class)
                .chatModel(ChatModelMock.thatResponds(
                        toolCall("bookTable", "{\"guests\":2}"), AiMessage.from("Booked.")))
                .tools(new StrictTools())
                .build();

        AiServiceInvocation invocation = capture(() -> concierge.chat("A table for 2")).invocation();

        assertThat(invocation).hasToolExecutedWith("bookTable", Map.of("guests", 2));
        assertThat(invocation.toolExecutions()).extracting(ToolExecution::result).isEqualTo(List.of("Table booked for 2"));
    }
}
