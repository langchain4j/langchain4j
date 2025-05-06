package dev.langchain4j.test.guardrail;

import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailResult.Failure;

/**
 * Custom assertions for {@link OutputGuardrailResult}s
 * <p>
 * This follows the pattern described in https://assertj.github.io/doc/#assertj-core-custom-assertions-creation
 * </p>
 */
public final class OutputGuardrailResultAssert
        extends GuardrailResultAssert<OutputGuardrailResultAssert, OutputGuardrailResult, Failure> {

    private OutputGuardrailResultAssert(OutputGuardrailResult outputGuardrailResult) {
        super(outputGuardrailResult, OutputGuardrailResultAssert.class, Failure.class);
    }

    /**
     * Creates a new {@code OutputGuardrailResultAssert} for the provided {@code OutputGuardrailResult}.
     *
     * @param actual the {@code OutputGuardrailResult} to be asserted
     * @return an {@code OutputGuardrailResultAssert} instance for chaining further assertions
     */
    public static OutputGuardrailResultAssert assertThat(OutputGuardrailResult actual) {
        return new OutputGuardrailResultAssert(actual);
    }

    /**
     * Asserts that the actual {@code OutputGuardrailResult} contains exactly one failure with the specified message and
     * reprompt.
     * If the assertion fails, an error is thrown detailing the problem.
     *
     * @param expectedFailureMessage the expected message of the single failure
     * @param expectedReprompt the expected reprompt
     * @return this assertion object for method chaining
     * @throws AssertionError if the actual object is null, if there are no failures,
     *         if there is more than one failure, or if the single failure
     *         does not match the specified message
     */
    public OutputGuardrailResultAssert hasSingleFailureWithMessageAndReprompt(
            String expectedFailureMessage, String expectedReprompt) {

        isNotNull();

        withFailures()
                .singleElement()
                .extracting(Failure::message, Failure::retry, Failure::reprompt)
                .containsExactly(expectedFailureMessage, true, expectedReprompt);

        return this;
    }
}
