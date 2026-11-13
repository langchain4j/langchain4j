package dev.langchain4j.test.guardrail;

import static dev.langchain4j.test.guardrail.GuardrailAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.guardrail.InputGuardrailResult;
import org.junit.jupiter.api.Test;

class GuardrailAssertionsTest {

    @Test
    void should_assert_on_guardrail_results() {
        assertThat(InputGuardrailResult.success()).isSuccessful();
    }

    @Test
    void should_fail_when_guardrail_result_is_not_as_expected() {
        assertThatThrownBy(() -> assertThat(InputGuardrailResult.success()).hasFailures())
                .isInstanceOf(AssertionError.class);
    }
}
