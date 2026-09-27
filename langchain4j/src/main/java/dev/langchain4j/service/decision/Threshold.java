package dev.langchain4j.service.decision;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.Experimental;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a {@code double} parameter of a decision service method as the threshold for a {@code boolean} answer:
 * the answer is {@code true} when the probability of "yes" is greater than or equal to the threshold.
 * <p>
 * The parameter is not sent to the model. When the method returns an object with several {@code boolean} fields,
 * {@link #value()} names the field the threshold applies to.
 * <pre>{@code
 * interface SupportDesk {
 *
 *     @Decide("Is this message spam?")
 *     boolean isSpam(String message, @Threshold double threshold);
 *
 *     Triage triage(String ticket, @Threshold("urgent") double urgentThreshold);
 * }
 * }</pre>
 *
 * @see DecisionServices.Builder#thresholdProvider(java.util.function.Function)
 * @since 1.21.0
 */
@Experimental
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface Threshold {

    /**
     * The name of the {@code boolean} field the threshold applies to. Can be omitted when there is only one
     * {@code boolean} answer.
     */
    String value() default "";
}
