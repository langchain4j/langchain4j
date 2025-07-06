package dev.langchain4j.service.guardrail;

import dev.langchain4j.guardrail.OutputGuardrail;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to apply guardrails to the output of the model using the declarative {@link dev.langchain4j.service.AiServices AiServices}
 * approach.
 * <p>
 *     Am output guardrail is a rule that is applied to the output of the model to ensure that the output is safe and meets
 *     certain expectations.
 * </p>
 * <p>
 *     When a validation fails, the result can indicate whether the request should be retried as-is, or to provide a
 *     {@code reprompt} message to append to the prompt.
 * </p>
 * <p>
 *     In the case of re-prompting, the reprompt message is added to the LLM context and the request is then retried.
 * </p>
 * <p>
 *     If the annotation is present on a class, the guardrails will be applied to all the methods of the class.
 * </p>
 * <p>
 *     When several guardrails are applied, the order of the guardrails is important, as the guardrails are applied in
 *     the order they are listed.
 * </p>
 * <p>
 *     When several {@link OutputGuardrail}s are applied, if any guardrail forces a retry or reprompt, then all of the
 *     guardrails will be re-applied to the new response.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface OutputGuardrails {
    /**
     * The ordered list of guardrails to apply to the output of the model.
     * <p>
     *     The order of the classes is important as the guardrails are applied in the order they are listed.
     *     Guardrails can not be present twice in the list.
     * </p>
     */
    Class<? extends OutputGuardrail>[] value();

    /**
     * The maximum number of retries to perform when an output guardrail forces a retry or reprompt.
     * <p>
     *     Set to {@code 0} to disable retries
     * </p>
     * @see dev.langchain4j.guardrail.config.OutputGuardrailsConfig#maxRetries()
     */
    int maxRetries() default dev.langchain4j.guardrail.config.OutputGuardrailsConfig.MAX_RETRIES_DEFAULT;
}
