package com.example.support;

import static com.example.support.Bookings.bookings;
import static com.example.support.ModelScripts.toolCall;
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.service.AiServiceCapture.capture;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.test.service.AiServiceCapture;
import dev.langchain4j.test.service.AiServiceInvocation;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Assertions on token counts and the finish reason.
 */
@Timeout(30)
class TokenUsageAndFinishReasonTest {

    CustomerSupportAgent agent(ChatModel model) {
        return CustomerSupport.customerSupportAgent(
                model, bookings(), CustomerSupport.policyExpert(MockChatModel.thatResponds("unused")));
    }

    MockChatModel cancellingModel(TokenUsage first, TokenUsage second) {
        return MockChatModel.thatResponds(ChatResponse.builder()
                        .aiMessage(toolCall("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"))
                        .tokenUsage(first)
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build())
                .thenResponds(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Cancelled."))
                        .tokenUsage(second)
                        .finishReason(FinishReason.STOP)
                        .build());
    }

    @Test
    void should_sum_token_usage_over_all_model_calls() {
        CustomerSupportAgent agent = agent(cancellingModel(new TokenUsage(100, 20), new TokenUsage(150, 30)));

        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Cancel MS-777")).invocation();

        assertThat(invocation.tokenUsage().inputTokenCount()).isEqualTo(250);
        assertThat(invocation.tokenUsage().outputTokenCount()).isEqualTo(50);
        assertThat(invocation.tokenUsage().totalTokenCount()).isEqualTo(300);
        assertThat(invocation.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(invocation).hasFinishReason(FinishReason.STOP);
    }

    @Test
    void should_sum_only_the_reported_token_counts() {
        // the second call reports only input tokens
        CustomerSupportAgent agent =
                agent(cancellingModel(new TokenUsage(100, 20), new TokenUsage(150)));

        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Cancel MS-777")).invocation();

        assertThat(invocation.tokenUsage().inputTokenCount()).isEqualTo(250);
    }

    @Test
    void should_fail_when_the_model_did_not_report_token_usage_or_finish_reason() {
        // a response given as a whole is used as it is: this one reports neither token usage nor finish reason
        CustomerSupportAgent agent = agent(MockChatModel.thatResponds(
                ChatResponse.builder().aiMessage(AiMessage.from("Hello")).build()));

        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Hi")).invocation();

        assertThat(invocation.tokenUsage()).isNull();
        assertThat(invocation.finishReason()).isNull();
        assertThatThrownBy(() -> assertThat(invocation).hasFinishReason(FinishReason.STOP))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[CustomerSupportAgent.chat] Expected finish reason <STOP>, but the model did not report it");
    }

    @Test
    void should_detect_a_response_that_was_cut_off() {
        CustomerSupportAgent agent =
                agent(MockChatModel.thatResponds(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Your booking MS-777 starts on"))
                        .finishReason(FinishReason.LENGTH)
                        .build()));

        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "When does MS-777 start?")).invocation();

        assertThatThrownBy(() -> assertThat(invocation).hasFinishReason(FinishReason.STOP))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[CustomerSupportAgent.chat] Expected finish reason <STOP>, but was <LENGTH>");
    }

    @Test
    void should_check_token_usage_and_finish_reason_of_a_result() {
        CustomerSupportAgent agent = agent(cancellingModel(new TokenUsage(100, 20), new TokenUsage(150, 30)));

        AiServiceCapture result = capture(() -> agent.chatWithDetails("klaus", "Cancel MS-777"));

        assertThat(result).hasFinishReason(FinishReason.STOP).hasToolExecuted("cancelBooking");
        assertThat(result.invocation().tokenUsage().totalTokenCount()).isEqualTo(300);
    }

    @Test
    void should_capture_token_usage_and_finish_reason_of_a_token_stream() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                MockStreamingChatModel.thatCallsTool("getBookingDetails", Map.of("bookingNumber", "MS-777"))
                        .thenStreams("It starts on 2026-11-02.")
                        .withTokenUsage(new TokenUsage(40, 10)),
                bookings());

        AiServiceInvocation invocation = capture(() -> agent.chat("When does MS-777 start?")
                        .ignoreErrors()
                        .start())
                .invocation();

        assertThat(invocation).hasFinishReason(FinishReason.STOP);
        assertThat(invocation.tokenUsage().totalTokenCount()).isEqualTo(100);
    }

    @Test
    void should_capture_token_usage_of_a_publisher() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                MockStreamingChatModel.thatStreams("Hello").withTokenUsage(new TokenUsage(7, 3)), bookings());

        AiServiceInvocation invocation = capture(() -> agent.chatReactive("Hi")).invocation();

        assertThat(invocation.tokenUsage().totalTokenCount()).isEqualTo(10);
    }
}
