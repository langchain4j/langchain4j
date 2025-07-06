package dev.langchain4j.test.guardrail;

import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import org.assertj.core.api.Assertions;

/**
 * Custom assertions for working with Guardrails
 * <p>
 * This follows the pattern described in https://assertj.github.io/doc/#assertj-core-custom-assertions-entry-point
 * </p>
 */
public class GuardrailAssertions extends Assertions {
    /**
     * Returns an {@link OutputGuardrailResultAssert} for assertions on an {@link OutputGuardrailResult}
     * @param actual The actual {@link OutputGuardrailResult}
     * @return The {@link OutputGuardrailResultAssert}
     */
    public static OutputGuardrailResultAssert assertThat(OutputGuardrailResult actual) {
        return OutputGuardrailResultAssert.assertThat(actual);
    }

    /**
     * Returns an {@link InputGuardrailResultAssert} for assertions on an {@link InputGuardrailResult}
     * @param actual The actual {@link InputGuardrailResult}
     * @return The {@link InputGuardrailResultAssert}
     */
    public static InputGuardrailResultAssert assertThat(InputGuardrailResult actual) {
        return InputGuardrailResultAssert.assertThat(actual);
    }
}
