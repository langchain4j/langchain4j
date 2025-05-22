package dev.langchain4j.service.guardrail;

import dev.langchain4j.guardrail.InputGuardrail;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to apply input guardrails to the input of the model using the declarative {@link dev.langchain4j.service.AiServices AiServices} approach.
 * <p>
 *     An input guardrail is a rule that is applied to the input of the model (essentially the user message) to ensure
 *     that the input is safe and meets the expectations of the model. It does not replace a moderation model, but it can
 *     be used to add additional checks (i.e. prompt injection, etc).
 * </p>
 * <p>
 *     Unlike for output guardrails, the input guardrails do not support retry or reprompt. The failure is passed directly
 *     to the caller, wrapped into a {@link dev.langchain4j.guardrail.GuardrailException GuardrailException}.
 * </p>
 * <p>
 *     If the annotation is present on a class, the guardrails will be applied to all the methods of the class.
 * </p>
 * <p>
 *     When several guardrails are applied, the order of the guardrails is important, as the guardrails are applied in the order
 *     they are listed.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface InputGuardrails {
    /**
     * The ordered list of {@link InputGuardrail}s to apply to the input of the model.
     * <p>
     *     The order of the classes is important as the guardrails are applied in the order they are listed.
     *     Guardrails can not be present twice in the list.
     * </p>
     */
    Class<? extends InputGuardrail>[] value();
}
