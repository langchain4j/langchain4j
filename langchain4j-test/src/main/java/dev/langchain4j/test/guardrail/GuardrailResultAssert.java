package dev.langchain4j.test.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.GuardrailResult.Failure;
import dev.langchain4j.guardrail.GuardrailResult.Result;
import dev.langchain4j.guardrail.InputGuardrailResult;
import java.util.Objects;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ListAssert;

/**
 * Custom assertions for {@link GuardrailResult}s
 * <p>
 *     This follows the pattern described in https://assertj.github.io/doc/#assertj-core-custom-assertions-creation
 * </p>
 * @param <A> The type of {@link GuardrailResultAssert}
 * @param <R> The type of {@link GuardrailResult}
 * @param <F> The type of {@link Failure}
 */
public abstract sealed class GuardrailResultAssert<
                A extends GuardrailResultAssert<A, R, F>, R extends GuardrailResult<R>, F extends Failure>
        extends AbstractObjectAssert<A, R> permits InputGuardrailResultAssert, OutputGuardrailResultAssert {

    private final Class<F> failureClass;

    protected GuardrailResultAssert(R r, Class<A> resultType, Class<F> failureClass) {
        super(r, resultType);
        this.failureClass = failureClass;
    }

    /**
     * Asserts that the actual object's {@link Result} matches the given expected result.
     * If the result does not match, an assertion error is thrown with the actual and expected values.
     *
     * @param result the expected result to compare against the actual object's result
     * @return this assertion object for method chaining
     * @throws AssertionError if the actual result does not match the expected result
     */
    public A hasResult(Result result) {
        isNotNull();

        if (!Objects.equals(actual.result(), result)) {
            throw failureWithActualExpected(
                    actual.result(), result, "Expected result to be <%s> but was <%s>", result, actual.result());
        }

        return (A) this;
    }

    /**
     * Asserts that the actual {@link Result} contains the specified successful text.
     * This method verifies that the actual instance is in a successful state and that
     * the successful text matches the expected value. If the assertion fails, an error
     * is thrown, detailing the mismatch.
     *
     * @param successfulText the expected text for the successful state
     * @return this assertion object for method chaining
     * @throws AssertionError if the actual object is not successful or if the successful text does not match the expected text
     */
    public A hasSuccessfulText(String successfulText) {
        isSuccessful();

        if (!Objects.equals(actual.successfulText(), successfulText)) {
            throw failureWithActualExpected(
                    actual.successfulText(),
                    successfulText,
                    "Expected successful text to be <%s> but was <%s>",
                    successfulText,
                    actual.successfulText());
        }

        return (A) this;
    }

    /**
     * Asserts that the actual {@code InputGuardrailResult} represents a successful state.
     * A successful state is determined by having {@link InputGuardrailResult#isSuccess()}.
     *
     * @return this assertion object for method chaining
     * @throws AssertionError if the actual result is not successful as per the aforementioned criteria
     */
    public A isSuccessful() {
        isNotNull();

        if (!actual.isSuccess()) {
            throw failure("Expected result to be successful but was <%s>", actual.result());
        }

        return (A) this;
    }

    /**
     * Asserts that the actual {@code InputGuardrailResult} contains failures.
     * The method validates that the object being asserted is not null and
     * that there are failures present within the result.
     *
     * @return this assertion object for method chaining
     * @throws AssertionError if the actual object is null or if the failures are empty
     */
    public A hasFailures() {
        isNotNull();
        withFailures().isNotEmpty();

        return (A) this;
    }

    /**
     * Asserts that the actual {@code InputGuardrailResult} contains exactly one failure with the specified message.
     * If the assertion fails, an error is thrown detailing the problem.
     *
     * @param expectedFailureMessage the expected message of the single failure
     * @return this assertion object for method chaining
     * @throws AssertionError if the actual object is null, if there are no failures,
     *         if there is more than one failure, or if the single failure
     *         does not match the specified message
     */
    public A hasSingleFailureWithMessage(String expectedFailureMessage) {
        isNotNull();

        withFailures().singleElement().extracting(Failure::message).isEqualTo(expectedFailureMessage);

        return (A) this;
    }

    /**
     * Asserts that the {@code InputGuardrailResult} contains exactly one {@link GuardrailResult.Failure} and verifies
     * that this failure meets the specified requirements. The requirements are defined by the provided {@link Consumer}.
     *
     * @param requirements a {@link Consumer} that defines the assertions to be applied to the single failure.
     *        Must not be {@code null}.
     * @return this assertion object for method chaining.
     * @throws NullPointerException if the {@code requirements} is {@code null}.
     * @throws AssertionError if the actual object is {@code null}, if there are no failures, if there is more than
     *         one failure, or if the single failure does not satisfy the specified requirements.
     * @see #satisfies(Consumer[])
     */
    public A assertSingleFailureSatisfies(Consumer<F> requirements) {
        isNotNull();
        ensureNotNull(requirements, "requirements");

        withFailures().singleElement().satisfies(requirements);

        return (A) this;
    }

    /**
     * Returns a {@link ListAssert} for the failures of the actual {@link GuardrailResult}.
     */
    public ListAssert<F> withFailures() {
        return assertThat(actual.failures())
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.list(this.failureClass));
    }
}
