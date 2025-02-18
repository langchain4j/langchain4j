package dev.langchain4j.service.guardrail;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to apply configuration to the output guardrails of the model using the declarative {@link dev.langchain4j.service.AiServices AiServices}
 * approach.
 * @see OutputGuardrails
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface OutputGuardrailsConfig {
    /**
     * The maximum number of retries to perform when an output guardrail forces a retry or reprompt.
     * <p>
     *     Set to {@code 0} to disable retries
     * </p>
     * @see dev.langchain4j.guardrail.config.OutputGuardrailsConfig#maxRetries()
     */
    int maxRetries() default dev.langchain4j.guardrail.config.OutputGuardrailsConfig.MAX_RETRIES_DEFAULT;
}
