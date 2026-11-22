package dev.langchain4j.test.quarkus;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.quarkus.Models.useChatModelResponding;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static dev.langchain4j.test.service.AiServiceCapture.captureExpectingFailure;

import com.example.support.SupportService;
import com.example.support.TicketTriage;
import com.example.support.TicketTriage.Priority;
import dev.langchain4j.data.message.AiMessage;
import io.quarkus.test.junit.QuarkusTest;
import dev.langchain4j.test.service.AiServiceCapture;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class StructuredOutputTest {

    private static final String TRIAGE_JSON = "{\"priority\":\"HIGH\",\"category\":\"delivery\"}";

    @Inject
    SupportService supportService;

    @Test
    void output_is_the_object_returned_by_the_application_bean() {
        useChatModelResponding(AiMessage.from(TRIAGE_JSON));

        AiServiceCapture capture = capture(() -> supportService.triage("My parcel never arrived"));

        TicketTriage triage = capture.output(TicketTriage.class);
        assertThat(triage).isEqualTo(new TicketTriage(Priority.HIGH, "delivery"));
        assertThat(capture.lambdaOutput()).isEqualTo(triage);
        assertThat(capture).hasSucceeded().hasNoToolExecutions().hasOwnChatModelCallCount(1);
    }

    @Test
    void output_text_fails_for_structured_output() {
        useChatModelResponding(AiMessage.from(TRIAGE_JSON));

        AiServiceCapture capture = capture(() -> supportService.triage("My parcel never arrived"));

        assertThatThrownBy(capture::outputText)
                .isInstanceOf(AssertionError.class)
                .hasMessage("The AI Service method produced a TicketTriage, not text: use output() instead");
    }

    @Test
    void output_of_returned_uni_is_the_object_inside() {
        useChatModelResponding(AiMessage.from(TRIAGE_JSON));

        AiServiceCapture capture = capture(() -> supportService.triageLater("My parcel never arrived"));

        TicketTriage triage = capture.output(TicketTriage.class); // no need to block the Uni
        assertThat(triage).isEqualTo(new TicketTriage(Priority.HIGH, "delivery"));
        assertThat(capture.lambdaOutput()).isInstanceOf(Uni.class);
        assertThat(capture.invocation().invocationContext().methodName()).isEqualTo("triageAsync");
        assertThatThrownBy(capture::outputText)
                .isInstanceOf(AssertionError.class)
                .hasMessage("The AI Service method produced a TicketTriage, not text: use output() instead");
    }

    @Test
    void output_text_and_output_of_returned_uni_of_string() {
        useChatModelResponding(AiMessage.from("Refunds take 5 days."));

        AiServiceCapture capture = capture(() -> supportService.answerLater("How long does a refund take?"));

        assertThat(capture.output()).isEqualTo("Refunds take 5 days.");
        assertThat(capture.outputText()).isEqualTo("Refunds take 5 days.");
        assertThat(capture.lambdaOutput()).isInstanceOf(Uni.class);
    }

    @Test
    void output_text_of_blocking_string_method() {
        useChatModelResponding(AiMessage.from("Hello!"));

        AiServiceCapture capture = capture(() -> supportService.answer("Hi"));

        assertThat(capture.output()).isEqualTo("Hello!");
        assertThat(capture.outputText()).isEqualTo("Hello!");
    }

    @Test
    void output_fails_when_the_invocation_failed() {
        useChatModelResponding(AiMessage.from("not json"));

        AiServiceCapture capture = captureExpectingFailure(
                () -> supportService.triage("My parcel never arrived"));

        assertThatThrownBy(capture::output)
                .isInstanceOf(AssertionError.class)
                .hasMessage("The AI Service invocation failed, so it has no output");
        assertThatThrownBy(capture::lambdaOutput)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("so it returned no output");
        assertThat(capture).hasFailedWithMessageContaining("not json");
    }
}
