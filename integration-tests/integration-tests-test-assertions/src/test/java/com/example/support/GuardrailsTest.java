package com.example.support;

import static com.example.support.Bookings.bookings;
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.test.service.AiServiceInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The guardrails of the agent, tested on their own and through the AI Service.
 */
@Timeout(30)
class GuardrailsTest {

    @Test
    void input_guardrail_should_stop_card_numbers() {
        NoCardNumbersGuardrail guardrail = new NoCardNumbersGuardrail();

        assertThat(guardrail.validate(UserMessage.from("Charge 4111 1111 1111 1111 please")))
                .hasResult(GuardrailResult.Result.FATAL)
                .hasSingleFailureWithMessage("The message contains a payment card number");
        assertThat(guardrail.validate(UserMessage.from("Cancel MS-777"))).isSuccessful();
    }

    @Test
    void output_guardrail_should_ask_for_a_professional_tone() {
        ProfessionalToneGuardrail guardrail = new ProfessionalToneGuardrail();

        assertThat(guardrail.validate(AiMessage.from("Sure dude, done")))
                .hasSingleFailureWithMessageAndReprompt(
                        "The response uses slang", "Rewrite your response in a professional tone, without slang.");
        assertThat(guardrail.validate(AiMessage.from("Your booking is cancelled."))).isSuccessful();
    }

    @Test
    void should_count_the_model_call_repeated_by_the_reprompting_output_guardrail() {
        MockChatModel model = MockChatModel.thatResponds(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Sure dude, it's gonna be cancelled"))
                        .tokenUsage(new TokenUsage(50, 10))
                        .finishReason(FinishReason.STOP)
                        .build())
                .thenResponds(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Your booking is cancelled."))
                        .tokenUsage(new TokenUsage(80, 8))
                        .finishReason(FinishReason.STOP)
                        .build());
        CustomerSupportAgent agent = CustomerSupport.customerSupportAgent(
                model, bookings(), CustomerSupport.policyExpert(MockChatModel.thatResponds("unused")));

        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Cancel MS-777")).invocation();

        assertThat(invocation.outputText()).isEqualTo("Your booking is cancelled.");
        assertThat(model.requests()).hasSize(2);
        assertThat(ModelScripts.lastUserMessage(model.requests().get(1)))
                .isEqualTo("Rewrite your response in a professional tone, without slang.");
        // both model calls are counted
        assertThat(invocation.tokenUsage().inputTokenCount()).isEqualTo(130);
        assertThat(invocation.tokenUsage().outputTokenCount()).isEqualTo(18);
        assertThat(invocation)
                .hasFinishReason(FinishReason.STOP)
                .hasNoToolExecutions();
    }
}
