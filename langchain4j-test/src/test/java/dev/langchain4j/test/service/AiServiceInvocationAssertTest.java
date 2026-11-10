package dev.langchain4j.test.service;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.test.LangChain4jAssertions;
import dev.langchain4j.test.judge.Judge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AiServiceInvocationAssertTest {

    @Test
    void should_pass_when_no_tools_were_executed() {
        assertThatCode(() -> LangChain4jAssertions.assertThat(invocation()).hasNoToolExecutions())
                .doesNotThrowAnyException();
    }

    @Test
    void should_fail_when_tools_were_executed_but_none_expected() {
        AiServiceInvocation invocation = invocation(execution("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasNoToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected no tools to be executed")
                .hasMessageContaining("1. getBookingDetails({\"bookingNumber\":\"MS-777\"}) -> ok");
    }

    @Test
    void should_check_that_tool_was_executed() {
        AiServiceInvocation invocation = invocation(execution("getBookingDetails", "{}"));

        LangChain4jAssertions.assertThat(invocation).hasToolExecuted("getBookingDetails");

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecuted("cancelBooking"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected tool <cancelBooking> to be executed successfully, but it was not executed")
                .hasMessageContaining("getBookingDetails");
    }

    @Test
    void should_report_that_no_tools_were_executed() {
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation()).hasToolExecuted("getBookingDetails"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected tool <getBookingDetails> to be executed")
                .hasMessageContaining("No tools were executed.");
    }

    @Test
    void should_check_only_tools_executed_in_any_order() {
        AiServiceInvocation invocation = invocation(execution("cancelBooking", "{}"), execution("getBookingDetails", "{}"));

        LangChain4jAssertions.assertThat(invocation).hasToolsExecutedExactlyInAnyOrder("getBookingDetails", "cancelBooking");
    }

    @Test
    void should_count_executions_of_the_same_tool() {
        AiServiceInvocation invocation = invocation(execution("getBookingDetails", "{}"), execution("getBookingDetails", "{}"));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolsExecutedExactlyInAnyOrder("getBookingDetails"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("these were missing: []")
                .hasMessageContaining("these were not expected: [getBookingDetails]");

        LangChain4jAssertions.assertThat(invocation).hasToolsExecutedExactlyInAnyOrder("getBookingDetails", "getBookingDetails");
    }

    @Test
    void should_fail_when_expected_tool_is_missing() {
        AiServiceInvocation invocation = invocation(execution("getBookingDetails", "{}"));

        assertThatThrownBy(() ->
                        LangChain4jAssertions.assertThat(invocation).hasToolsExecutedExactlyInAnyOrder("getBookingDetails", "cancelBooking"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("these were missing: [cancelBooking]")
                .hasMessageContaining("these were not expected: []");
    }

    @Test
    void should_match_a_subset_of_arguments() {
        AiServiceInvocation invocation = invocation(
                execution("getBookingDetails", "{\"bookingNumber\":\"MS-777\",\"customerName\":\"Klaus\"}"),
                execution("getWeather", "{\"city\":\"Berlin\",\"days\":3,\"units\":{\"temperature\":\"C\"}}"));

        LangChain4jAssertions.assertThat(invocation)
                .hasToolExecutedWith("getBookingDetails", Map.of("bookingNumber", "MS-777"))
                .hasToolExecutedWith("getWeather", Map.of("days", 3, "units", Map.of("temperature", "C")))
                .hasToolExecutedWith("getWeather", Map.of());
    }

    @Test
    void should_check_tool_arguments_with_requirements() {
        AiServiceInvocation invocation = invocation(execution("getWeather", "{\"city\":\"München\",\"days\":3}"));

        LangChain4jAssertions.assertThat(invocation)
                .hasToolExecutedWith("getWeather", arguments -> assertThat(arguments.get("city"))
                        .isIn("Munich", "München"))
                .hasToolExecutedWith("getWeather", arguments -> assertThat(arguments.get("days"))
                        .isEqualTo(3));
    }

    @Test
    void should_fail_with_what_the_requirements_rejected() {
        AiServiceInvocation invocation = invocation(execution("getWeather", "{\"city\":\"Berlin\"}"));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation)
                        .hasToolExecutedWith("getWeather", arguments -> assertThat(arguments.get("city"))
                                .isIn("Munich", "München")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "Expected tool <getWeather> to be executed successfully with arguments that pass the requirements")
                .hasMessageContaining("its executions did not pass them")
                .hasMessageContaining("[\"Munich\", \"München\"]")
                .hasMessageContaining("1. getWeather({\"city\":\"Berlin\"}) -> ok");
    }

    @Test
    void should_not_run_the_requirements_when_the_tool_was_not_executed() {
        List<Map<String, Object>> checked = new ArrayList<>();
        AiServiceInvocation invocation = invocation(execution("getBookingDetails", "{}"));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecutedWith("getWeather", checked::add))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("but it was not.")
                .hasMessageContaining("getBookingDetails");
        assertThat(checked).isEmpty();
    }

    @Test
    void should_not_run_the_requirements_for_failed_tool_executions() {
        List<Map<String, Object>> checked = new ArrayList<>();
        AiServiceInvocation invocation = invocation(failedExecution("getWeather", "{\"city\":\"Munich\"}"));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecutedWith("getWeather", checked::add))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("but it was not.")
                .hasMessageContaining("(FAILED)");
        assertThat(checked).isEmpty();
    }

    @Test
    void should_mark_only_the_executions_that_pass_the_requirements_as_checked() {
        AiServiceInvocation invocation = invocation(
                execution("getWeather", "{\"city\":\"Munich\"}"), execution("getWeather", "{\"city\":\"Berlin\"}"));

        LangChain4jAssertions.assertThat(invocation).hasToolExecutedWith("getWeather", arguments -> assertThat(
                        arguments.get("city"))
                .isEqualTo("Munich"));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasNoMoreToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("not checked by a tool assertion")
                .hasMessageContaining("1. getWeather({\"city\":\"Berlin\"})");

        LangChain4jAssertions.assertThat(invocation)
                .hasToolExecutedWith("getWeather", arguments -> assertThat(arguments.get("city"))
                        .isEqualTo("Berlin"))
                .hasNoMoreToolExecutions();
    }

    @Test
    void should_rethrow_exceptions_of_the_requirements_that_are_not_assertion_failures() {
        IllegalStateException problem = new IllegalStateException("the judge could not give a verdict");
        AiServiceInvocation invocation = invocation(execution("getWeather", "{}"));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecutedWith("getWeather", arguments -> {
                    throw problem;
                }))
                .isSameAs(problem);
    }

    @Test
    void should_check_tool_arguments_with_a_judge() {
        ChatModelMock satisfied = ChatModelMock.thatAlwaysResponds(
                """
                {"assessments": [{"conditionNumber": 1, "reasoning": "DE is Germany.", "result": "SATISFIED"}]}""");
        ChatModelMock notSatisfied = ChatModelMock.thatAlwaysResponds(
                """
                {"assessments": [{"conditionNumber": 1, "reasoning": "DE is not France.", "result": "NOT_SATISFIED"}]}""");
        AiServiceInvocation invocation = invocation(execution("getWeather", "{\"country\":\"DE\"}"));

        LangChain4jAssertions.assertThat(invocation).hasToolExecutedWith("getWeather", arguments -> Judge.from(satisfied)
                .assertThat(arguments.get("country").toString())
                .satisfies("refers to Germany"));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation)
                        .hasToolExecutedWith("getWeather", arguments -> Judge.from(notSatisfied)
                                .assertThat(arguments.get("country").toString())
                                .satisfies("refers to France")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("its executions did not pass them")
                .hasMessageContaining("DE is not France.");
    }

    @Test
    void should_parse_arguments_like_the_tool_executor() {
        // some models produce a trailing comma, or arguments encoded as a JSON string, which tools accept
        AiServiceInvocation invocation = invocation(
                execution("getBookingDetails", "{\"bookingNumber\":\"MS-777\",}"),
                execution("cancelBooking", "\"{\\\"bookingNumber\\\":\\\"MS-888\\\"}\""));

        LangChain4jAssertions.assertThat(invocation)
                .hasToolExecutedWith("getBookingDetails", Map.of("bookingNumber", "MS-777"))
                .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-888"));
    }

    @Test
    void should_compare_numbers_by_value() {
        AiServiceInvocation invocation = invocation(execution("getWeather", "{\"days\":3.0,\"coordinates\":[52.5,13.4]}"));

        LangChain4jAssertions.assertThat(invocation)
                .hasToolExecutedWith("getWeather", Map.of("days", 3))
                .hasToolExecutedWith("getWeather", Map.of("coordinates", List.of(52.50, 13.4)));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecutedWith("getWeather", Map.of("days", 4)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected tool <getWeather> to be executed successfully with arguments containing {\"days\":4}");
    }

    @Test
    void should_match_numbers_and_booleans_sent_as_strings_like_the_tool_receives_them() {
        // weaker models often send numbers and booleans as strings, which the tool receives converted
        AiServiceInvocation invocation =
                invocation(execution("getWeather", "{\"days\":\"3\",\"hourly\":\"true\",\"limit\":5}"));

        LangChain4jAssertions.assertThat(invocation)
                .hasToolExecutedWith("getWeather", Map.of("days", 3, "hourly", true))
                .hasToolExecutedWith("getWeather", Map.of("limit", "5"));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecutedWith("getWeather", Map.of("days", 4)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected tool <getWeather> to be executed successfully with arguments containing {\"days\":4}");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecutedWith("getWeather", Map.of("hourly", false)))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecutedWith("getWeather", Map.of("days", "three")))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_match_nested_values_exactly() {
        AiServiceInvocation invocation =
                invocation(execution("getWeather", "{\"units\":{\"temperature\":\"C\",\"speed\":\"kmh\"}}"));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation)
                        .hasToolExecutedWith("getWeather", Map.of("units", Map.of("temperature", "C"))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("but it was not");
    }

    @Test
    void should_truncate_long_arguments_and_results_in_failure_message() {
        String longArgument = "{\"text\":\"" + "a".repeat(1000) + "\"}";
        AiServiceInvocation invocation = invocation(execution("summarize", longArgument));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasNoToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("...")
                .message()
                .doesNotContain("a".repeat(400));
    }

    @Test
    void should_fail_when_arguments_do_not_match() {
        AiServiceInvocation invocation = invocation(execution("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation)
                        .hasToolExecutedWith("getBookingDetails", Map.of("bookingNumber", "MS-778")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "Expected tool <getBookingDetails> to be executed successfully with arguments containing {\"bookingNumber\":\"MS-778\"}")
                .hasMessageContaining("getBookingDetails({\"bookingNumber\":\"MS-777\"})");
    }

    @Test
    void should_match_arguments_of_any_execution_of_the_tool() {
        AiServiceInvocation invocation = invocation(
                execution("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"),
                execution("getBookingDetails", "{\"bookingNumber\":\"MS-778\"}"));

        LangChain4jAssertions.assertThat(invocation).hasToolExecutedWith("getBookingDetails", Map.of("bookingNumber", "MS-778"));
    }

    @Test
    void should_not_match_arguments_that_are_not_valid_json() {
        AiServiceInvocation invocation = invocation(execution("getBookingDetails", "not json"));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation)
                        .hasToolExecutedWith("getBookingDetails", Map.of("bookingNumber", "MS-777")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("getBookingDetails(not json) -> ok (arguments could not be parsed as JSON)");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecutedWith("getBookingDetails", Map.of()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("but it was not");
    }

    @Test
    void should_check_for_failed_tool_executions() {
        ToolExecution failed = ToolExecution.builder()
                .request(ToolExecutionRequest.builder()
                        .name("cancelBooking")
                        .arguments("{}")
                        .build())
                .result(ToolExecutionResult.builder()
                        .resultText("Booking not found")
                        .isError(true)
                        .build())
                .invocationContext(InvocationContext.builder().build())
                .build();

        LangChain4jAssertions.assertThat(invocation(execution("getBookingDetails", "{}"))).hasNoFailedToolExecutions();

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation(failed)).hasNoFailedToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("cancelBooking({}) -> Booking not found (FAILED)");
    }

    @Test
    void should_check_retrieved_sources() {
        AiServiceInvocation invocation = TestInvocation.builder()
                .content("answer")
                .sources(List.of(Content.from("Reservations can be cancelled up to 7 days before the start date.")))
                .build();

        LangChain4jAssertions.assertThat(invocation).hasRetrievedSourceContaining("cancelled up to 7 days");

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasRetrievedSourceContaining("refund"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected a retrieved source containing <refund>")
                .hasMessageContaining("Reservations can be cancelled up to 7 days");

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation()).hasRetrievedSourceContaining("refund"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("no sources were captured: the AI Service has no retrieval augmentor");
    }

    @Test
    void should_check_finish_reason() {
        AiServiceInvocation invocation = TestInvocation.builder()
                .content("answer")
                .finishReason(FinishReason.LENGTH)
                .build();

        LangChain4jAssertions.assertThat(invocation).hasFinishReason(FinishReason.LENGTH);

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasFinishReason(FinishReason.STOP))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected finish reason <STOP>, but was <LENGTH>");
    }

    @Test
    void should_navigate_to_tool_names_and_executions() {
        AiServiceInvocation invocation = invocation(execution("getBookingDetails", "{}"), execution("cancelBooking", "{}"));

        LangChain4jAssertions.assertThat(invocation).executedToolNames().containsExactly("getBookingDetails", "cancelBooking");
        LangChain4jAssertions.assertThat(invocation).toolExecutions().hasSize(2);

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation)
                        .executedToolNames()
                        .containsExactly("cancelBooking", "getBookingDetails"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("cancelBooking");
    }

    @Test
    void should_reject_blank_arguments() {
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation()).hasToolExecuted(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolName");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation()).hasRetrievedSourceContaining(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation()).hasToolExecutionFailed(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolName");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation()).hasToolExecutedTimes("x", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("times must not be negative");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation()).hasOwnChatModelCallCount(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedCount must not be negative");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation()).hasOwnChatModelCallCountAtMost(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxCount must not be negative");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation())
                        .hasToolExecutedWith(" ", arguments -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolName");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation())
                        .hasToolExecutedWith("x", (Consumer<Map<String, Object>>) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("argumentsRequirements");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation()).hasToolsExecutedInOrder((String[]) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolNames");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation()).hasFailedWithMessageContaining(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    @Test
    void should_fail_on_null() {
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat((AiServiceInvocation) null).hasNoToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expecting actual not to be null");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat((AiServiceInvocation) null).hasNoToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expecting actual not to be null");
    }

    @Test
    void should_count_only_successful_executions() {
        AiServiceInvocation invocation = invocation(
                execution("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"),
                failedExecution("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecuted("cancelBooking"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "Expected tool <cancelBooking> to be executed successfully, but its 1 execution(s) failed");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation)
                        .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("but its executions with them failed");
        LangChain4jAssertions.assertThat(invocation)
                .executedToolNames()
                .containsExactly("getBookingDetails", "cancelBooking");
    }

    @Test
    void exactly_should_fail_when_a_tool_that_is_not_expected_was_executed_and_failed() {
        AiServiceInvocation invocation = invocation(
                execution("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"),
                failedExecution("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"));

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation)
                        .hasToolsExecutedExactlyInAnyOrder("getBookingDetails"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("and these tools, not expected, were executed and failed: [cancelBooking]");
    }

    @Test
    void exactly_should_ignore_failed_executions_of_expected_tools() {
        AiServiceInvocation invocation = invocation(
                failedExecution("getBookingDetails", "{\"bookingNumber\":\"MS-7\"}"),
                execution("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"));

        LangChain4jAssertions.assertThat(invocation).hasToolsExecutedExactlyInAnyOrder("getBookingDetails");
    }

    @Test
    void should_check_failed_tool_execution() {
        AiServiceInvocation invocation = invocation(execution("getBookingDetails", "{}"), failedExecution("cancelBooking", "{}"));

        LangChain4jAssertions.assertThat(invocation).hasToolExecutionFailed("cancelBooking");

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecutionFailed("getBookingDetails"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected an execution of tool <getBookingDetails> to fail, but none failed");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecutionFailed("refund"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected an execution of tool <refund> to fail, but it was not executed");
    }

    @Test
    void should_check_how_many_times_a_tool_was_executed() {
        AiServiceInvocation invocation = invocation(
                execution("getWeather", "{}"), execution("getWeather", "{}"), failedExecution("getWeather", "{}"));

        LangChain4jAssertions.assertThat(invocation)
                .hasToolExecutedTimes("getWeather", 2)
                .hasToolExecutedTimes("cancelBooking", 0);

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecutedTimes("getWeather", 3))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "Expected tool <getWeather> to be executed successfully 3 time(s), but it was 2 time(s) and failed 1 time(s)");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecutedTimes("getWeather", 0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("but it was 2 time(s)");
    }

    @Test
    void should_check_the_order_of_tool_executions() {
        AiServiceInvocation invocation = invocation(
                execution("getBookingDetails", "{}"), execution("getWeather", "{}"), execution("cancelBooking", "{}"));

        LangChain4jAssertions.assertThat(invocation)
                .hasToolsExecutedInOrder("getBookingDetails", "cancelBooking")
                .hasToolsExecutedInOrder();

        assertThatThrownBy(() ->
                        LangChain4jAssertions.assertThat(invocation).hasToolsExecutedInOrder("cancelBooking", "getBookingDetails"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "Expected these tools to be executed successfully in this order: [cancelBooking, getBookingDetails], but they were not");
    }

    @Test
    void should_check_that_no_more_tools_were_executed() {
        AiServiceInvocation invocation = invocation(
                execution("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"),
                execution("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"),
                failedExecution("refund", "{}"));

        LangChain4jAssertions.assertThat(invocation)
                .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"))
                .hasToolExecuted("getBookingDetails")
                .hasToolExecutionFailed("refund")
                .hasNoMoreToolExecutions();

        AiServiceInvocation another = invocation(
                execution("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"),
                execution("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"),
                failedExecution("refund", "{}"));
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(another)
                        .hasToolExecuted("getBookingDetails")
                        .hasNoMoreToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected no more tool executions, but these were not checked by a tool assertion:")
                .hasMessageContaining("1. cancelBooking({\"bookingNumber\":\"MS-777\"}) -> ok")
                .hasMessageContaining("2. refund({}) -> failed (FAILED)");
    }

    @Test
    void should_check_that_no_sources_were_retrieved() {
        LangChain4jAssertions.assertThat(TestInvocation.builder().sources(List.of()).build())
                .hasNoRetrievedSources();

        AiServiceInvocation invocation = TestInvocation.builder()
                .content("answer")
                .sources(List.of(Content.from("Reservations can be cancelled up to 7 days before the start date.")))
                .build();
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasNoRetrievedSources())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected no retrieved sources, but these were retrieved:")
                .hasMessageContaining("Reservations can be cancelled");
    }

    @Test
    void should_check_the_number_of_own_chat_model_calls() {
        AiServiceInvocation invocation = TestInvocation.builder()
                .content("answer")
                .intermediateResponses(List.of(response("calling a tool", null)))
                .finalResponse(response("answer", null))
                .build();

        LangChain4jAssertions.assertThat(invocation).hasOwnChatModelCallCount(2).hasOwnChatModelCallCountAtMost(2);

        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasOwnChatModelCallCount(1))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected 1 call(s) of the AI Service to its chat model, but there were 2");
        assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasOwnChatModelCallCountAtMost(1))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected at most 1 call(s) of the AI Service to its chat model, but there were 2");
    }

    private static ToolExecution failedExecution(String name, String arguments) {
        return ToolExecution.builder()
                .request(ToolExecutionRequest.builder()
                        .name(name)
                        .arguments(arguments)
                        .build())
                .result(ToolExecutionResult.builder()
                        .resultText("failed")
                        .isError(true)
                        .build())
                .invocationContext(InvocationContext.builder().build())
                .build();
    }

    private static ChatResponse response(String text, TokenUsage tokenUsage) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .tokenUsage(tokenUsage)
                .build();
    }

    private static ToolExecution execution(String name, String arguments) {
        return ToolExecution.builder()
                .request(ToolExecutionRequest.builder()
                        .name(name)
                        .arguments(arguments)
                        .build())
                .result(ToolExecutionResult.builder().resultText("ok").build())
                .invocationContext(InvocationContext.builder().build())
                .build();
    }

    private static AiServiceInvocation invocation(ToolExecution... toolExecutions) {
        return TestInvocation.builder()
                .content("answer")
                .toolExecutions(List.of(toolExecutions))
                .build();
    }

    @Nested
    class ScopeAndVerification {
        interface Assistant {

            String chat(String message);
        }

        static class BookingTools {

            @Tool
            String getBookingDetails(@P(name = "bookingNumber") String bookingNumber) {
                return "Booking " + bookingNumber + " exists";
            }

            @Tool
            String cancelBooking(@P(name = "bookingNumber") String bookingNumber) {
                if (bookingNumber.equals("MS-000")) {
                    throw new IllegalStateException("Booking not found");
                }
                return "Booking " + bookingNumber + " cancelled";
            }
        }

        static class ExpertTool {

            private final Assistant expert;

            ExpertTool(Assistant expert) {
                this.expert = expert;
            }

            @Tool
            String askExpert(@P(name = "question") String question) {
                return expert.chat(question);
            }
        }

        // the outer assistant calls the expert through a tool, and the expert cancels the booking
        private static AiServiceCapture captureWithNestedInvocation() {
            Assistant expert = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer(toolCall("cancelBooking", "MS-777")))
                            .withTokenUsage(new TokenUsage(10, 5, 15)))
                    .tools(new BookingTools())
                    .build();
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(
                                    callToolsThenAnswer(ToolExecutionRequest.builder()
                                            .id("1")
                                            .name("askExpert")
                                            .arguments("{\"question\":\"cancel MS-777\"}")
                                            .build()))
                            .withTokenUsage(new TokenUsage(100, 50, 150))
                            .withFinishReason(FinishReason.STOP))
                    .tools(new ExpertTool(expert))
                    .build();
            return capture(() -> assistant.chat("Cancel MS-777"));
        }

        @Test
        void should_ask_which_invocations_to_check_when_there_are_nested_invocations() {
            AiServiceCapture capture = captureWithNestedInvocation();

            String expectedMessage = "[Assistant.chat] This invocation has 1 nested invocations. "
                    + "Use includingNested() or topLevelOnly() to say which ones to check.";
            assertThatThrownBy(() -> assertThat(capture).hasToolExecuted("askExpert")).hasMessage(expectedMessage);
            assertThatThrownBy(() -> assertThat(capture).hasNoToolExecutions()).hasMessage(expectedMessage);
            assertThatThrownBy(() -> assertThat(capture).hasNoFailedToolExecutions()).hasMessage(expectedMessage);
            assertThatThrownBy(() -> assertThat(capture).hasOwnChatModelCallCountAtMost(10)).hasMessage(expectedMessage);
            assertThatThrownBy(() -> assertThat(capture).hasModelCallCount(2)).hasMessage(expectedMessage);
            assertThatThrownBy(() -> assertThat(capture).hasNoRetrievedSources()).hasMessage(expectedMessage);
            assertThatThrownBy(() -> assertThat(capture).hasNoMoreToolExecutions()).hasMessage(expectedMessage);
            assertThatThrownBy(() -> assertThat(capture).executedToolNames()).hasMessage(expectedMessage);
        }

        @Test
        void should_not_ask_which_invocations_to_check_for_assertions_about_the_invocation_itself() {
            AiServiceCapture capture = captureWithNestedInvocation();

            assertThat(capture).hasSucceeded().hasFinishReason(FinishReason.STOP);
        }

        @Test
        void should_check_only_the_invocation_itself_with_top_level_only() {
            AiServiceCapture capture = captureWithNestedInvocation();

            assertThat(capture)
                    .topLevelOnly()
                    .hasToolExecuted("askExpert")
                    .hasToolExecutedTimes("cancelBooking", 0)
                    .hasOwnChatModelCallCount(2)
                    .hasModelCallCount(2)
                    .hasNoMoreToolExecutions();
            assertThatThrownBy(() -> assertThat(capture).topLevelOnly().hasModelCallCount(3))
                    .hasMessageContaining("Expected 3 model call(s), but there were 2");
        }

        @Test
        void should_check_the_whole_tree_with_including_nested() {
            AiServiceCapture capture = captureWithNestedInvocation();

            assertThat(capture)
                    .includingNested()
                    .hasToolsExecutedExactlyInAnyOrder("askExpert", "cancelBooking")
                    .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"))
                    .hasOwnChatModelCallCount(4)
                    .hasModelCallCount(4)
                    .hasNoMoreToolExecutions();
            assertThat(capture).includingNested().executedToolNames().containsExactly("askExpert", "cancelBooking");
            assertThatThrownBy(() -> assertThat(capture).includingNested().hasModelCallCount(ChatModelCall.class, 3))
                    .hasMessageContaining("Expected 3 chat model call(s), but there were 4");
            assertThatThrownBy(() -> assertThat(capture).includingNested().hasNoToolExecutions())
                    .hasMessageContaining("2. cancelBooking({\"bookingNumber\":\"MS-777\"}) -> Booking MS-777 cancelled");
        }

        @Test
        void should_refuse_to_check_the_order_of_tools_across_nested_invocations() {
            AiServiceCapture capture = captureWithNestedInvocation();

            assertThatThrownBy(() -> assertThat(capture).includingNested().hasToolsExecutedInOrder("askExpert"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("The order of the tool executions of different invocations is not known");
        }

        @Test
        void should_fail_when_a_tool_execution_was_not_checked() {
            AiServiceCapture capture = captureCalling(toolCall("getBookingDetails", "MS-777"), toolCall("cancelBooking", "MS-777"));

            assertThatThrownBy(() -> assertThat(capture).hasToolExecuted("getBookingDetails").hasNoMoreToolExecutions())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected no more tool executions, but these were not checked by a tool assertion:%n"
                            .formatted()
                            + "  1. cancelBooking({\"bookingNumber\":\"MS-777\"}) -> Booking MS-777 cancelled");
        }

        @Test
        void should_add_up_the_checks_of_separate_assertions_on_the_same_capture() {
            AiServiceCapture capture = captureCalling(toolCall("getBookingDetails", "MS-777"), toolCall("cancelBooking", "MS-777"));

            assertThat(capture).hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"));
            assertThat(capture).hasToolExecuted("getBookingDetails").hasNoMoreToolExecutions();
        }

        @Test
        void should_mark_only_the_executions_with_the_given_arguments_as_checked() {
            AiServiceCapture capture = captureCalling(toolCall("cancelBooking", "MS-777"), toolCall("cancelBooking", "MS-778"));

            assertThatThrownBy(() -> assertThat(capture)
                            .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"))
                            .hasNoMoreToolExecutions())
                    .hasMessageContaining("  1. cancelBooking({\"bookingNumber\":\"MS-778\"}) -> Booking MS-778 cancelled")
                    .message()
                    .doesNotContain("not checked by a tool assertion:%n  1. cancelBooking({\"bookingNumber\":\"MS-777\"})"
                            .formatted());
        }

        @Test
        void should_mark_failed_executions_as_checked_when_their_failure_is_checked() {
            AiServiceCapture capture = captureCalling(toolCall("cancelBooking", "MS-000"), toolCall("cancelBooking", "MS-777"));

            assertThat(capture)
                    .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"))
                    .hasToolExecutionFailed("cancelBooking")
                    .hasNoMoreToolExecutions();
        }

        @Test
        void should_refuse_tool_executions_whose_failure_was_not_reported() {
            // simulates a framework that does not report whether a tool execution failed
            AiServiceInvocation invocation = capture(() -> {
                        AiServiceListenerRegistrar registrar = AiServiceListenerRegistrar.newInstance();
                        InvocationContext context = InvocationContext.builder()
                                .invocationId(UUID.randomUUID())
                                .interfaceName("com.example.Assistant")
                                .methodName("chat")
                                .timestampNow()
                                .build();
                        registrar.fireEvent(AiServiceStartedEvent.builder()
                                .invocationContext(context)
                                .userMessage(UserMessage.from("Hello"))
                                .build());
                        registrar.fireEvent(ToolExecutedEvent.builder()
                                .invocationContext(context)
                                .request(ToolExecutionRequest.builder()
                                        .id("1")
                                        .name("cancelBooking")
                                        .arguments("{}")
                                        .build())
                                .resultText("cancelled")
                                .build());
                        registrar.fireEvent(AiServiceCompletedEvent.builder()
                                .invocationContext(context)
                                .result("answer")
                                .build());
                    })
                    .invocation();

            assertThatThrownBy(invocation::toolExecutions)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("did not report whether its tool executions failed");
            assertThatThrownBy(() -> assertThat(invocation).toolExecutions())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("did not report whether its tool executions failed");
            assertThat(invocation).executedToolNames().containsExactly("cancelBooking");
        }

        private static AiServiceCapture captureCalling(ToolExecutionRequest... toolCalls) {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer(toolCalls)))
                    .tools(new BookingTools())
                    .build();
            return capture(() -> assistant.chat("Cancel"));
        }

        private static ToolExecutionRequest toolCall(String toolName, String bookingNumber) {
            return ToolExecutionRequest.builder()
                    .id(toolName + bookingNumber)
                    .name(toolName)
                    .arguments("{\"bookingNumber\":\"" + bookingNumber + "\"}")
                    .build();
        }

        private static Function<ChatRequest, AiMessage> callToolsThenAnswer(ToolExecutionRequest... toolCalls) {
            return request -> {
                List<ChatMessage> messages = request.messages();
                if (messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage) {
                    return AiMessage.from("answer");
                }
                return AiMessage.from(List.of(toolCalls));
            };
        }
    }
}
