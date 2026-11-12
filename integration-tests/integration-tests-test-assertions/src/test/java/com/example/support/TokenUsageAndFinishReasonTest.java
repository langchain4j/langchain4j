package com.example.support;

import static com.example.support.Bookings.bookings;
import static com.example.support.ModelScripts.callToolsThenAnswer;
import static com.example.support.ModelScripts.toolCall;
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.service.AiServiceCapture.capture;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.test.service.AiServiceCapture;
import dev.langchain4j.test.service.AiServiceInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Assertions on token counts and the finish reason.
 */
@Timeout(30)
class TokenUsageAndFinishReasonTest {

    CustomerSupportAgent agent(ChatModel model) {
        return CustomerSupport.customerSupportAgent(
                model, bookings(), CustomerSupport.policyExpert(ChatModelMock.thatAlwaysResponds("unused")));
    }

    ChatModelMock cancellingModel() {
        return ChatModelMock.thatResponds(callToolsThenAnswer(
                "Cancelled.", toolCall("cancelBooking", "{\"bookingNumber\":\"MS-777\"}")));
    }

    @Test
    void should_sum_token_usage_over_all_model_calls() {
        CustomerSupportAgent agent = agent(cancellingModel()
                .withTokenUsages(new TokenUsage(100, 20), new TokenUsage(150, 30))
                .withFinishReason(FinishReason.STOP));

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
                agent(cancellingModel().withTokenUsages(new TokenUsage(100, 20), new TokenUsage(150)));

        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Cancel MS-777")).invocation();

        assertThat(invocation.tokenUsage().inputTokenCount()).isEqualTo(250);
    }

    @Test
    void should_fail_when_the_model_did_not_report_token_usage_or_finish_reason() {
        CustomerSupportAgent agent = agent(ChatModelMock.thatAlwaysResponds("Hello"));

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
                agent(ChatModelMock.thatAlwaysResponds("Your booking MS-777 starts on").withFinishReason(FinishReason.LENGTH));

        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "When does MS-777 start?")).invocation();

        assertThatThrownBy(() -> assertThat(invocation).hasFinishReason(FinishReason.STOP))
                .isInstanceOf(AssertionError.class)
                .hasMessage("[CustomerSupportAgent.chat] Expected finish reason <STOP>, but was <LENGTH>");
    }

    @Test
    void should_check_token_usage_and_finish_reason_of_a_result() {
        CustomerSupportAgent agent = agent(cancellingModel()
                .withTokenUsages(new TokenUsage(100, 20), new TokenUsage(150, 30))
                .withFinishReason(FinishReason.STOP));

        AiServiceCapture result = capture(() -> agent.chatWithDetails("klaus", "Cancel MS-777"));

        assertThat(result).hasFinishReason(FinishReason.STOP).hasToolExecuted("cancelBooking");
        assertThat(result.invocation().tokenUsage().totalTokenCount()).isEqualTo(300);
    }

    @Test
    void should_capture_token_usage_and_finish_reason_of_a_token_stream() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatAlwaysStreams(
                                toolCall("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"),
                                AiMessage.from("It starts on 2026-11-02."))
                        .withTokenUsage(new TokenUsage(40, 10))
                        .withFinishReason(FinishReason.STOP),
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
                StreamingChatModelMock.thatAlwaysStreams("Hello").withTokenUsage(new TokenUsage(7, 3)), bookings());

        AiServiceInvocation invocation = capture(() -> agent.chatReactive("Hi")).invocation();

        assertThat(invocation.tokenUsage().totalTokenCount()).isEqualTo(10);
    }
}
