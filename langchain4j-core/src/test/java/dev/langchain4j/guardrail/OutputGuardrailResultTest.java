package dev.langchain4j.guardrail;

import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutputGuardrailResultTest {

    @Test
    void success_shouldReturnSingletonSuccess() {
        OutputGuardrailResult result1 = OutputGuardrailResult.success();
        OutputGuardrailResult result2 = OutputGuardrailResult.success();

        assertThat(result1).isSameAs(result2);
        assertThat(result1.result()).isEqualTo(OutputGuardrailResult.Result.SUCCESS);
        assertThat(result1.failures()).isEmpty();
    }

    @Test
    void successWithText_shouldReturnResultWithText() {
        String text = "Hello, AI!";
        OutputGuardrailResult result = OutputGuardrailResult.successWith(text);

        assertThat(result.result()).isEqualTo(OutputGuardrailResult.Result.SUCCESS_WITH_RESULT);
        assertThat(result.successfulText()).isEqualTo(text);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void successWithAiMessage_shouldReturnResultWithAiMessage() {
        AiMessage aiMessage = AiMessage.from("AI Message");
        OutputGuardrailResult result = OutputGuardrailResult.successWith(aiMessage);

        assertThat(result.result()).isEqualTo(OutputGuardrailResult.Result.SUCCESS_WITH_RESULT);
        assertThat(result.successfulText()).isEqualTo("AI Message");
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void failure_shouldReturnResultWithFailures() {
        OutputGuardrailResult.Failure failure = new OutputGuardrailResult.Failure("Oops", null, true, "Try again");
        OutputGuardrailResult result = OutputGuardrailResult.failure(List.of(failure));

        assertThat(result.result()).isEqualTo(OutputGuardrailResult.Result.FAILURE);
        assertThat(result.failures()).containsExactly(failure);
        assertThat(result.isRetry()).isTrue();
        assertThat(result.isReprompt()).isTrue();
        assertThat(result.getReprompt()).contains("Try again");
    }

    @Test
    void successWithResult_shouldReturnObject() {
        String text = "Success Text";
        Object value = 42;
        OutputGuardrailResult result = OutputGuardrailResult.successWith(text, value);

        assertThat(result.result()).isEqualTo(OutputGuardrailResult.Result.SUCCESS_WITH_RESULT);
        assertThat(result.successfulText()).isEqualTo(text);
        assertThat(result.successfulResult()).isEqualTo(value);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void equalsAndHashCode_shouldBeConsistent() {
        OutputGuardrailResult r1 = OutputGuardrailResult.successWith("Hi");
        OutputGuardrailResult r2 = OutputGuardrailResult.successWith("Hi");
        OutputGuardrailResult r3 = OutputGuardrailResult.successWith("Hello");

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        assertThat(r1).isNotEqualTo(r3);
    }
}
