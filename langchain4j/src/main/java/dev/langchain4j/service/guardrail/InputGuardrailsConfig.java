package dev.langchain4j.service.guardrail;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to apply configuration to the input guardrails of the model using the declarative {@link dev.langchain4j.service.AiServices AiServices}
 * approach.
 * @see InputGuardrails
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface InputGuardrailsConfig {}
