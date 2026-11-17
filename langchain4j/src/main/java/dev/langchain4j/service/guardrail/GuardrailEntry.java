package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * A guardrail of a tool guardrail chain, together with how it was obtained, which decides how duplicates are detected
 * (see {@link ToolGuardrailChainResolver}).
 *
 * @param guardrail the guardrail
 * @param fromClass {@code true} if the framework created it from a guardrail class (an annotation or a
 *                  {@code *GuardrailClasses(..)} setter), which is done without arguments, so every instance of the
 *                  class is identical; {@code false} if the user supplied the instance
 * @param <G>       the type of guardrail
 */
record GuardrailEntry<G>(G guardrail, boolean fromClass) {

    GuardrailEntry {
        ensureNotNull(guardrail, "guardrail");
    }

    /**
     * An entry for a guardrail the framework created from a guardrail class.
     */
    static <G> GuardrailEntry<G> fromClass(G guardrail) {
        return new GuardrailEntry<>(guardrail, true);
    }

    /**
     * An entry for a guardrail instance supplied by the user.
     */
    static <G> GuardrailEntry<G> fromInstance(G guardrail) {
        return new GuardrailEntry<>(guardrail, false);
    }
}
