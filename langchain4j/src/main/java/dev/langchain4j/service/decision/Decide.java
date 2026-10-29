package dev.langchain4j.service.decision;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.Experimental;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * The question a decision model answers for a method of a decision service, or for a field of the object a method
 * returns.
 * <pre>{@code
 * interface SupportDesk {
 *
 *     @Decide("Is this message spam?")
 *     boolean isSpam(String message);
 * }
 * }</pre>
 *
 * @see DecisionServices
 * @since 1.21.0
 */
@Experimental
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface Decide {

    /**
     * The question to answer, for example "Is this message spam?".
     */
    String value();
}
