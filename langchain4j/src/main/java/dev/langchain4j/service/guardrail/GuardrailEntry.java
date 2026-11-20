package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import org.jspecify.annotations.Nullable;

/**
 * A guardrail of a tool guardrail chain, together with how it was obtained, which decides how duplicates are detected
 * (see {@link ToolGuardrailChainResolver}).
 *
 * @param guardrail      the guardrail
 * @param guardrailClass the class the framework created it from (an annotation or a {@code *GuardrailClasses(..)}
 *                       setter), which identifies it; {@code null} if the user supplied the instance
 * @param <G>            the type of guardrail
 */
record GuardrailEntry<G>(G guardrail, @Nullable Class<?> guardrailClass) {

    GuardrailEntry {
        ensureNotNull(guardrail, "guardrail");
    }

    /**
     * An entry for a guardrail the framework created from a guardrail class.
     */
    static <G> GuardrailEntry<G> fromClass(Class<? extends G> guardrailClass, G guardrail) {
        return new GuardrailEntry<>(guardrail, ensureNotNull(guardrailClass, "guardrailClass"));
    }

    /**
     * An entry for a guardrail instance supplied by the user.
     */
    static <G> GuardrailEntry<G> fromInstance(G guardrail) {
        return new GuardrailEntry<>(guardrail, null);
    }

    /**
     * Whether the framework created the guardrail from a guardrail class.
     */
    boolean fromClass() {
        return guardrailClass != null;
    }
}
